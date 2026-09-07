import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, uuid, authHeaders, metrics, thresholdProfiles } from "./helpers.js";

const USERNAME = __ENV.TEST_USERNAME || "";
const PASSWORD = __ENV.TEST_PASSWORD || "";
// Branch-scoped principal seeded by DevSeeder (#411). Optional: when unset the
// suite runs exactly as before with the GLOBAL owner principal only.
const SCOPED_USERNAME = __ENV.SCOPED_USERNAME || "";
const SCOPED_PASSWORD = __ENV.SCOPED_PASSWORD || "";
// Relief requester principal seeded by DevSeeder (#413) — capability without an
// assignment, the only seat that can request relief duty. Optional like the scoped one.
const RELIEF_USERNAME = __ENV.RELIEF_USERNAME || "";
const RELIEF_PASSWORD = __ENV.RELIEF_PASSWORD || "";
const MANILA_OFFSET_MS = 8 * 60 * 60 * 1000;

export const options = { thresholds: thresholdProfiles.full, stages: [
  { duration: "15s", target: 5 },
  { duration: "30s", target: 5 },
  { duration: "15s", target: 0 },
] };

export function setup() {
  if (!USERNAME || !PASSWORD) {
    throw new Error("Set TEST_USERNAME and TEST_PASSWORD environment variables");
  }
  const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: USERNAME, password: PASSWORD,
  }), { headers: { "Content-Type": "application/json" } });
  if (!observe(res, null, {}, "setup login", (r) => r.status === 200)) throw new Error("Load-test login failed");
  const token = res.json("token");
  const me = http.get(`${BASE_URL}/api/me`, { headers: authHeaders(token) });
  if (!observe(me, null, {}, "setup current user", (r) => r.status === 200)) throw new Error("Load-test user lookup failed");
  const userId = me.json("id");
  const branches = http.get(`${BASE_URL}/api/branches`, { headers: authHeaders(token) });
  if (!observe(branches, null, {}, "setup fixture branches", (r) => r.status === 200)) throw new Error("Load-test branch lookup failed");
  const branch = branches.json().find((candidate) => candidate.name === "K6 Fixture Branch");
  if (!branch) throw new Error("Dev seeder did not create K6 Fixture Branch");
  const branchDetail = http.get(`${BASE_URL}/api/branches/${branch.id}`, { headers: authHeaders(token) });
  if (!observe(branchDetail, null, {}, "setup fixture branch detail", (r) => r.status === 200)) throw new Error("Load-test fixture lookup failed");
  const clockIn = http.post(`${BASE_URL}/api/attendance/clock-in`, JSON.stringify({
    attendanceId: uuid(), branchId: branch.id,
  }), { headers: authHeaders(token) });
  const clockedIn = observe(clockIn, null, {}, "setup fixture clock-in", (r) => r.status === 200 || r.status === 201 || r.status === 409);
  let branchDayId = clockedIn ? clockIn.json("branchDayId") : null;
  if (!branchDayId) {
    const today = http.get(`${BASE_URL}/api/branches/${branch.id}/today`, { headers: authHeaders(token) });
    if (!observe(today, null, {}, "setup fixture day", (r) => r.status === 200)) throw new Error("Load-test day lookup failed");
    branchDayId = today.json("branchDayId");
  }
  return { token, userId, branchId: branch.id, branchDayId, scoped: setupScopedPrincipal(), relief: setupReliefPrincipal(branch.id) };
}

function setupScopedPrincipal() {
  if (!SCOPED_USERNAME || !SCOPED_PASSWORD) return null;
  const loginRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: SCOPED_USERNAME, password: SCOPED_PASSWORD,
  }), { headers: { "Content-Type": "application/json" } });
  if (!observe(loginRes, null, {}, "setup scoped login", (r) => r.status === 200)) throw new Error("Scoped-principal login failed");
  const scopedToken = loginRes.json("token");
  const meRes = http.get(`${BASE_URL}/api/me`, { headers: authHeaders(scopedToken) });
  if (!observe(meRes, null, {}, "setup scoped current user", (r) => r.status === 200)) throw new Error("Scoped-principal lookup failed");
  // Negative spot-check: a BRANCH-scoped principal must fail the GLOBAL
  // MANAGE_USERS gate. Fail the suite loudly if seeding ever over-scopes.
  const deniedRes = http.get(`${BASE_URL}/api/users`, { headers: authHeaders(scopedToken) });
  if (!observe(deniedRes, null, {}, "setup scoped MANAGE_USERS denial", (r) => r.status === 403)) {
    throw new Error("Scoped principal passed a MANAGE_USERS gate — seeding is over-scoped");
  }
  return { token: scopedToken, userId: meRes.json("id") };
}

// Relief requester principal (#413): login + identity, then a fail-closed negative
// spot-check — this seat holds NO assignment, so the assignment-gated invite list
// (createInvite authority surface) must deny it. Fails the suite loudly if seeding
// ever hands the relief user an assignment.
function setupReliefPrincipal(branchId) {
  if (!RELIEF_USERNAME || !RELIEF_PASSWORD) return null;
  const loginRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: RELIEF_USERNAME, password: RELIEF_PASSWORD,
  }), { headers: { "Content-Type": "application/json" } });
  if (!observe(loginRes, null, {}, "setup relief login", (r) => r.status === 200)) throw new Error("Relief-principal login failed");
  const reliefToken = loginRes.json("token");
  const meRes = http.get(`${BASE_URL}/api/me`, { headers: authHeaders(reliefToken) });
  if (!observe(meRes, null, {}, "setup relief current user", (r) => r.status === 200)) throw new Error("Relief-principal lookup failed");
  // Negative spot-check: this seat holds NO home assignment, so the
  // assignment-gated invite-authority read must deny it.
  const deniedRes = http.get(`${BASE_URL}/api/branches/${branchId}/relief-invites`, { headers: authHeaders(reliefToken) });
  if (!observe(deniedRes, null, {}, "setup relief assignment denial", (r) => r.status === 403)) {
    throw new Error("Relief principal passed an assignment-gated gate — seeding is over-scoped");
  }
  return { token: reliefToken, userId: meRes.json("id") };
}

export default function (data) {
  const headers = authHeaders(data.token);
  const tid = uuid();

  const branch = { id: data.branchId, dayId: data.branchDayId };
  const clientId = createClient(headers, tid);
  if (!clientId) return;
  const session = createSession(headers, tid, branch.id, clientId);
  if (!session) return;
  updateSessionStatus(headers, tid, session.id);
  voidAndUnvoidSession(headers, tid, session.id);
  managePractitioners(headers, tid, session.id, data.userId);
  const catId = createProductCategory(headers, tid);
  if (!catId) return;
  const prodId = createProduct(headers, tid, catId);
  if (!prodId || !createInventoryCard(headers, tid, branch.id, prodId)) return;
  const inventoryVersion = restockInventory(headers, tid, branch.id, prodId, branch.dayId);
  if (inventoryVersion === null) return;
  const uniqueWrites = __VU === 1 && __ITER === 0;
  if (uniqueWrites) {
    const saleVersion = recordInventoryMovement(headers, tid, branch.id, prodId, branch.dayId, inventoryVersion);
    if (saleVersion === null) return;
    createProductSale(headers, tid, branch.dayId, prodId, session.id, saleVersion);
    createCompensation(headers, tid, branch.dayId, data.userId);
  }
  createExpense(headers, tid, branch.dayId);
  createAllowance(headers, tid, branch.dayId, data.userId);
  // Relief flow legs (#413) run BEFORE the remittance-submit cluster below: grant,
  // invite-accept, and deny all require an OPEN day, and the owner's remittance
  // submission is the one leg that can close it mid-run. Single execution — the
  // flood rule (#357) allows only one live request per requester+branch-day.
  if (data.relief && data.scoped && uniqueWrites) {
    runReliefLegs(data.scoped, data.relief, branch);
  }
  if (uniqueWrites) {
    createAndSubmitRemittance(headers, tid, branch.id, branch.dayId, session.id);
  }
  fetchNotifications(headers, tid);
  fetchReports(headers, tid, branch.id);
  // Clients are GLOBAL-gated (requireGlobalCapability), so the scoped
  // principal cannot create its own — it reuses the one this iteration's
  // GLOBAL leg created, exactly like branch-scoped staff work off the
  // shared directory.
  if (data.scoped) {
    runScopedLegs(data.scoped, branch, prodId, clientId);
  }

  sleep(0.5);
}

// Branch-scoped principal (#411): exercises the exact-scope gate paths real
// staff hit (plain-BRANCH grants) that the GLOBAL owner principal can never
// reach. Failures count toward the shared error budget — fail-closed.
function runScopedLegs(scoped, branch, prodId, clientId) {
  const headers = authHeaders(scoped.token);
  const tags = { group: "scoped" };

  const sessionId = uuid();
  const sessionRes = http.post(`${BASE_URL}/api/sessions`, JSON.stringify({
    id: sessionId, clientId, branchId: branch.id, isWalkIn: true, finalPrice: "2500.00",
  }), { headers });
  const sessionCreated = observe(sessionRes, metrics.scopedLatency, tags, "scoped create session", (r) => r.status === 201 || r.status === 200);

  if (sessionCreated) {
    const statusRes = http.patch(`${BASE_URL}/api/sessions/${sessionId}/status`, JSON.stringify({
      status: "COMPLETED", version: 1, reason: "K6 scoped-principal fixture",
    }), { headers });
    observe(statusRes, metrics.scopedLatency, tags, "scoped complete session", (r) => r.status === 200);

    const voidRes = http.post(`${BASE_URL}/api/sessions/${sessionId}/void`, JSON.stringify({
      id: uuid(), voidReason: "K6 scoped-principal void",
    }), { headers });
    observe(voidRes, metrics.scopedLatency, tags, "scoped void session", (r) => r.status === 200 || r.status === 201);

    const unvoidRes = http.post(`${BASE_URL}/api/sessions/${sessionId}/unvoid`, JSON.stringify({
      unvoidedReason: "K6 scoped-principal unvoid",
    }), { headers });
    observe(unvoidRes, metrics.scopedLatency, tags, "scoped unvoid session", (r) => r.status === 200);

    const addPractitionerRes = http.post(`${BASE_URL}/api/sessions/${sessionId}/practitioners`, JSON.stringify({
      id: uuid(), practitionerId: scoped.userId, reason: "K6 scoped-principal fixture",
    }), { headers });
    const added = observe(addPractitionerRes, metrics.scopedLatency, tags, "scoped add practitioner", (r) => r.status === 201 || r.status === 200);
    if (added) {
      const delRes = http.del(`${BASE_URL}/api/sessions/${sessionId}/practitioners/${scoped.userId}`, JSON.stringify({
        reason: "K6 scoped-principal fixture",
      }), { headers });
      observe(delRes, metrics.scopedLatency, tags, "scoped remove practitioner", (r) => r.status === 200 || r.status === 204);
    }
  }

  const expenseId = uuid();
  const expenseRes = http.post(`${BASE_URL}/api/expenses`, JSON.stringify({
    id: expenseId, branchDayId: branch.dayId, amount: "100.00", category: "MISCELLANEOUS",
    reason: "K6 scoped-principal fixture",
  }), { headers });
  observe(expenseRes, metrics.scopedLatency, tags, "scoped create expense", (r) => r.status === 201 || r.status === 200);

  const expenseListRes = http.get(`${BASE_URL}/api/expenses?branchDayId=${branch.dayId}`, { headers });
  observe(expenseListRes, metrics.scopedLatency, tags, "scoped list expenses", (r) => r.status === 200);

  if (prodId) {
    const restockRes = http.post(`${BASE_URL}/api/branches/${branch.id}/inventory/${prodId}/restock`, JSON.stringify({
      id: uuid(), quantity: 5, branchDayId: branch.dayId,
      editReason: "K6 scoped-principal fixture",
    }), { headers });
    observe(restockRes, metrics.scopedLatency, tags, "scoped restock inventory", (r) => r.status === 201 || r.status === 200);
  }

  const notificationsRes = http.get(`${BASE_URL}/api/notifications`, { headers });
  observe(notificationsRes, metrics.scopedLatency, tags, "scoped list notifications", (r) => r.status === 200);

  const dailyRes = http.get(`${BASE_URL}/api/branches/${branch.id}/daily-summary?date=${manilaDate()}`, { headers });
  observe(dailyRes, metrics.scopedLatency, tags, "scoped daily report", (r) => r.status === 200);
}

// Relief flow legs (#413): the staffing-critical state machines under load — invite
// mint→accept→revoke and request→grant plus request→deny — with both principals'
// notification reads pinning the transactional broadcast fan-out. Scoped principal
// holds grant/revoke authority (assignment); relief principal requests and accepts
// (capability without assignment). Runs before the remittance-submit cluster so the
// day is still OPEN for every gate.
function runReliefLegs(scoped, relief, branch) {
  const scopedHeaders = authHeaders(scoped.token);
  const reliefHeaders = authHeaders(relief.token);
  const tags = { group: "relief" };
  const today = manilaDate();

  // Invite cycle first: accept writes an active day grant for the invitee, and
  // createInvite rejects an invitee already holding one (#357 per-person guard).
  const mintRes = http.post(`${BASE_URL}/api/branches/${branch.id}/relief-invites`, JSON.stringify({
    inviteeUserId: relief.userId, date: today,
  }), { headers: scopedHeaders });
  const invited = observe(mintRes, metrics.reliefLatency, tags, "relief mint invite", (r) => r.status === 201 || r.status === 200);
  if (invited) {
    const inviteId = mintRes.json("id");
    if (inviteId) {
      const acceptRes = http.post(`${BASE_URL}/api/relief-invites/${inviteId}/accept`, null, { headers: reliefHeaders });
      const accepted = observe(acceptRes, metrics.reliefLatency, tags, "relief accept invite", (r) => r.status === 200);
      if (accepted) {
        const revokeRes = http.post(`${BASE_URL}/api/relief-invites/${inviteId}/revoke`, null, { headers: scopedHeaders });
        observe(revokeRes, metrics.reliefLatency, tags, "relief revoke invite", (r) => r.status === 200);
      }
    }
  }

  // Request → grant, then a second request → deny. The #357 flood rule allows one
  // live request at a time, so each must resolve before the next is created.
  const grantedRequest = createReliefRequest(reliefHeaders, branch.id, tags, "grant");
  if (grantedRequest) {
    const grantRes = http.patch(`${BASE_URL}/api/relief-access/${grantedRequest}/grant`, JSON.stringify({
      reason: "K6 relief fixture",
    }), { headers: scopedHeaders });
    observe(grantRes, metrics.reliefLatency, tags, "relief grant request", (r) => r.status === 200);
  }

  const deniedRequest = createReliefRequest(reliefHeaders, branch.id, tags, "deny");
  if (deniedRequest) {
    const denyRes = http.patch(`${BASE_URL}/api/relief-access/${deniedRequest}/deny`, JSON.stringify({
      reason: "K6 relief fixture",
    }), { headers: scopedHeaders });
    observe(denyRes, metrics.reliefLatency, tags, "relief deny request", (r) => r.status === 200);
  }

  // Caller-relative discovery reads: the mine list and the deep-link day read.
  const mineRes = http.get(`${BASE_URL}/api/relief-access/mine`, { headers: reliefHeaders });
  observe(mineRes, metrics.reliefLatency, tags, "relief mine list", (r) => r.status === 200);
  const dayReadRes = http.get(`${BASE_URL}/api/relief-access?branchId=${branch.id}&date=${today}`, { headers: reliefHeaders });
  observe(dayReadRes, metrics.reliefLatency, tags, "relief deep-link read", (r) => r.status === 200);

  // Broadcast fan-out observation: every event above committed notification rows
  // inside its command transaction; both principals now read their mailboxes.
  const reliefNotificationsRes = http.get(`${BASE_URL}/api/notifications`, { headers: reliefHeaders });
  observe(reliefNotificationsRes, metrics.reliefLatency, tags, "relief list notifications", (r) => r.status === 200);
  const scopedNotificationsRes = http.get(`${BASE_URL}/api/notifications`, { headers: scopedHeaders });
  observe(scopedNotificationsRes, metrics.reliefLatency, tags, "scoped relief notifications", (r) => r.status === 200);
}

function createReliefRequest(reliefHeaders, branchId, tags, label) {
  const res = http.post(`${BASE_URL}/api/relief-access/request`, JSON.stringify({
    requestId: uuid(), branchId, reason: `K6 relief fixture (${label})`,
  }), { headers: reliefHeaders });
  const created = observe(res, metrics.reliefLatency, tags, `relief create ${label} request`, (r) => r.status === 201 || r.status === 200);
  return created ? res.json("id") : null;
}

function createClient(headers, tid) {
  const tags = { group: "client" };
  const clientId = uuid();
  const res = http.post(`${BASE_URL}/api/clients`, JSON.stringify({
    id: clientId, firstName: `K6First-${tid}`, lastName: `K6Last-${tid}`,
    gender: "F", age: 30, phoneNumber: "09170000000",
  }), { headers });
  if (!observe(res, metrics.clientLatency, tags, "create client", (r) => r.status === 201 || r.status === 200)) return null;

  const searchRes = http.get(`${BASE_URL}/api/clients?q=K6First`, { headers });
  observe(searchRes, metrics.clientLatency, tags, "search clients", (r) => r.status === 200);

  const getRes = http.get(`${BASE_URL}/api/clients/${clientId}`, { headers });
  observe(getRes, metrics.clientLatency, tags, "get client", (r) => r.status === 200);

  const updRes = http.patch(`${BASE_URL}/api/clients/${clientId}`, JSON.stringify({
    phoneNumber: "09171111111",
  }), { headers });
  observe(updRes, metrics.clientLatency, tags, "update client", (r) => r.status === 200);

  return clientId;
}

function createSession(headers, tid, branchId, clientId) {
  const tags = { group: "session" };
  const sessionId = uuid();
  const res = http.post(`${BASE_URL}/api/sessions`, JSON.stringify({
    id: sessionId, clientId, branchId, isWalkIn: true, finalPrice: "2500.00",
  }), { headers });
  if (!observe(res, metrics.sessionLatency, tags, "create session", (r) => r.status === 201 || r.status === 200)) return null;
  return { id: sessionId, branchDayId: res.json("branchDayId") };
}

function updateSessionStatus(headers, tid, sessionId) {
  const tags = { group: "session" };
  const res = http.patch(`${BASE_URL}/api/sessions/${sessionId}/status`, JSON.stringify({
    status: "COMPLETED", version: 1, reason: "K6 repeated-workflow fixture",
  }), { headers });
  observe(res, metrics.sessionLatency, tags, "complete session", (r) => r.status === 200);
}

function voidAndUnvoidSession(headers, tid, sessionId) {
  const tags = { group: "session" };

  const voidId = uuid();
  const voidRes = http.post(`${BASE_URL}/api/sessions/${sessionId}/void`, JSON.stringify({
    id: voidId, voidReason: "K6 load test void",
  }), { headers });
  observe(voidRes, metrics.sessionLatency, tags, "void session", (r) => r.status === 200 || r.status === 201);

  const unvoidRes = http.post(`${BASE_URL}/api/sessions/${sessionId}/unvoid`, JSON.stringify({
    unvoidedReason: "K6 load test unvoid",
  }), { headers });
  observe(unvoidRes, metrics.sessionLatency, tags, "unvoid session", (r) => r.status === 200);
}

function managePractitioners(headers, tid, sessionId, practitionerId) {
  const tags = { group: "session" };

  const addRes = http.post(`${BASE_URL}/api/sessions/${sessionId}/practitioners`, JSON.stringify({
    id: uuid(), practitionerId, reason: "K6 repeated-workflow fixture",
  }), { headers });
  const added = observe(addRes, metrics.sessionLatency, tags, "add practitioner", (r) => r.status === 201 || r.status === 200);

  if (added) {
    const updRes = http.patch(`${BASE_URL}/api/sessions/${sessionId}/practitioners/${practitionerId}`,
      JSON.stringify({ remarks: "K6 test remarks", reason: "K6 repeated-workflow fixture" }), { headers });
    observe(updRes, metrics.sessionLatency, tags, "update practitioner", (r) => r.status === 200);

    const delRes = http.del(`${BASE_URL}/api/sessions/${sessionId}/practitioners/${practitionerId}`, JSON.stringify({
      reason: "K6 repeated-workflow fixture",
    }), { headers });
    observe(delRes, metrics.sessionLatency, tags, "remove practitioner", (r) => r.status === 200 || r.status === 204);
  }

  const concernsRes = http.get(`${BASE_URL}/api/concerns`, { headers });
  observe(concernsRes, metrics.sessionLatency, tags, "list concerns", (r) => r.status === 200);
}

function createProductCategory(headers, tid) {
  const tags = { group: "product" };
  const catId = uuid();
  const res = http.post(`${BASE_URL}/api/product-categories`, JSON.stringify({
    id: catId, name: `K6 Category ${tid}`,
  }), { headers });
  if (!observe(res, metrics.productLatency, tags, "create category", (r) => r.status === 201 || r.status === 200)) return null;

  observe(http.get(`${BASE_URL}/api/product-categories`, { headers }), metrics.productLatency, tags, "list categories", (r) => r.status === 200);
  observe(http.get(`${BASE_URL}/api/product-categories/${catId}`, { headers }), metrics.productLatency, tags, "get category", (r) => r.status === 200);

  return catId;
}

function createProduct(headers, tid, catId) {
  const tags = { group: "product" };
  const prodId = uuid();
  const res = http.post(`${BASE_URL}/api/products`, JSON.stringify({
    id: prodId, name: `K6 Product ${tid}`, productCategoryId: catId,
    unitPrice: "500.00", commissionAmount: "50.00",
  }), { headers });
  if (!observe(res, metrics.productLatency, tags, "create product", (r) => r.status === 201 || r.status === 200)) return null;

  observe(http.get(`${BASE_URL}/api/products`, { headers }), metrics.productLatency, tags, "list products", (r) => r.status === 200);
  observe(http.get(`${BASE_URL}/api/products/${prodId}`, { headers }), metrics.productLatency, tags, "get product", (r) => r.status === 200);
  return prodId;
}

function createInventoryCard(headers, tid, branchId, prodId) {
  const tags = { group: "inventory" };
  const res = http.post(`${BASE_URL}/api/branches/${branchId}/inventory`, JSON.stringify({
    productId: prodId,
  }), { headers });
  return observe(res, metrics.inventoryLatency, tags, "create inventory card", (r) => r.status === 201 || r.status === 200);
}

function restockInventory(headers, tid, branchId, prodId, branchDayId) {
  const tags = { group: "inventory" };
  const res = http.post(`${BASE_URL}/api/branches/${branchId}/inventory/${prodId}/restock`, JSON.stringify({
    id: uuid(), quantity: 50, branchDayId,
    editReason: "K6 repeated-workflow fixture",
  }), { headers });
  if (!observe(res, metrics.inventoryLatency, tags, "restock inventory", (r) => r.status === 201 || r.status === 200)) return null;

  const listRes = http.get(`${BASE_URL}/api/branches/${branchId}/inventory`, { headers });
  observe(listRes, metrics.inventoryLatency, tags, "list inventory", (r) => r.status === 200);
  if (listRes.status !== 200) return null;
  const inventory = listRes.json();
  const card = inventory.find((item) => item.productId === prodId);
  return card ? card.version : null;
}

function recordInventoryMovement(headers, tid, branchId, prodId, branchDayId, version) {
  const tags = { group: "inventory" };
  const movementRes = http.post(`${BASE_URL}/api/branches/${branchId}/inventory/${prodId}/movement`, JSON.stringify({
    movementId: uuid(), reason: "ADJUSTMENT", quantityChange: 5, branchDayId, expectedVersion: version,
    editReason: "K6 repeated-workflow fixture",
  }), { headers });
  const moved = observe(movementRes, metrics.inventoryLatency, tags, "record inventory movement", (r) => r.status === 201 || r.status === 200);
  return moved ? version + 1 : null;
}

function createProductSale(headers, tid, branchDayId, prodId, sessionId, expectedVersion) {
  const tags = { group: "sale" };

  const res = http.post(`${BASE_URL}/api/product-sales`, JSON.stringify({
    id: uuid(), branchDayId, sessionId, clientId: null,
    isWalkIn: false, productId: prodId, quantity: 1, expectedVersion,
    reason: "K6 repeated-workflow fixture",
  }), { headers });
  observe(res, metrics.saleLatency, tags, "create product sale", (r) => r.status === 201 || r.status === 200);
}

function createCompensation(headers, tid, branchDayId, userId) {
  const tags = { group: "compensation" };
  const compId = uuid();
  const res = http.post(`${BASE_URL}/api/compensation`, JSON.stringify({
    id: compId, workBranchDayId: branchDayId, payingBranchDayId: branchDayId,
    userId, amount: "500.00", note: "K6 test compensation", reason: "K6 repeated-workflow fixture",
  }), { headers });
  observe(res, metrics.compensationLatency, tags, "create compensation", (r) => r.status === 201 || r.status === 200);
}

function createExpense(headers, tid, branchDayId) {
  const tags = { group: "expense" };
  const expenseId = uuid();

  const res = http.post(`${BASE_URL}/api/expenses`, JSON.stringify({
    id: expenseId, branchDayId, amount: "100.00", category: "MISCELLANEOUS",
    reason: "K6 repeated-workflow fixture",
  }), { headers });
  observe(res, metrics.expenseLatency, tags, "create expense", (r) => r.status === 201 || r.status === 200);

  const listRes = http.get(`${BASE_URL}/api/expenses?branchDayId=${branchDayId}`, { headers });
  observe(listRes, metrics.expenseLatency, tags, "list expenses", (r) => r.status === 200);
}

function createAllowance(headers, tid, branchDayId, userId) {
  const tags = { group: "allowance" };

  const res = http.post(`${BASE_URL}/api/allowances`, JSON.stringify({
    id: uuid(), branchDayId, userId, amount: "200.00", reason: "K6 repeated-workflow fixture",
  }), { headers });
  observe(res, metrics.allowanceLatency, tags, "create allowance", (r) => r.status === 201 || r.status === 200);

  const listRes = http.get(`${BASE_URL}/api/allowances?branchDayId=${branchDayId}`, { headers });
  observe(listRes, metrics.allowanceLatency, tags, "list allowances", (r) => r.status === 200);
}

function createAndSubmitRemittance(headers, tid, branchId, branchDayId, sessionId) {
  const tags = { group: "remittance" };
  const today = manilaDate();

  const remitRes = http.post(`${BASE_URL}/api/remittances`, JSON.stringify({
    id: uuid(), type: "SESSION", branchId,
    method: "BANK_TRANSFER", dateRangeStart: today, dateRangeEnd: today,
  }), { headers });
  const created = observe(remitRes, metrics.remittanceLatency, tags, "create remittance", (r) => r.status === 201 || r.status === 200);

  const remittanceId = remitRes.json("id");
  if (created && remittanceId) {
    const dayRes = http.post(`${BASE_URL}/api/remittances/${remittanceId}/day-breakdowns`, JSON.stringify({
      id: uuid(), branchDayId,
    }), { headers });
    const dayAdded = observe(dayRes, metrics.remittanceLatency, tags, "add remittance day", (r) => r.status === 201 || r.status === 200);
    const lineRes = http.post(`${BASE_URL}/api/remittances/${remittanceId}/lines`, JSON.stringify({
      id: uuid(), type: "SESSION", sessionId, amount: "2500.00",
    }), { headers });
    const lineAdded = observe(lineRes, metrics.remittanceLatency, tags, "add remittance line", (r) => r.status === 201 || r.status === 200);
    if (dayAdded && lineAdded) {
      const currentRes = http.get(`${BASE_URL}/api/remittances/${remittanceId}`, { headers });
      const current = observe(currentRes, metrics.remittanceLatency, tags, "get remittance before submit", (r) => r.status === 200);
      if (current) {
        const submitRes = http.post(`${BASE_URL}/api/remittances/${remittanceId}/submit`, JSON.stringify({
          expectedVersion: currentRes.json("version"),
        }), { headers });
        observe(submitRes, metrics.remittanceLatency, tags, "submit remittance", (r) => r.status === 200);
      }
    }
  }
}

function fetchNotifications(headers, tid) {
  const tags = { group: "notification" };
  const res = http.get(`${BASE_URL}/api/notifications`, { headers });
  observe(res, metrics.notificationLatency, tags, "list notifications", (r) => r.status === 200);
}

function fetchReports(headers, tid, branchId) {
  const tags = { group: "report" };
  const today = manilaDate();
  const year = today.slice(0, 4);
  const month = today.slice(5, 7);

  const dailyRes = http.get(`${BASE_URL}/api/branches/${branchId}/daily-summary?date=${today}`, { headers });
  observe(dailyRes, metrics.reportLatency, tags, "daily report", (r) => r.status === 200);

  const monthlyRes = http.get(`${BASE_URL}/api/branches/${branchId}/monthly-summary?year=${year}&month=${month}`, { headers });
  observe(monthlyRes, metrics.reportLatency, tags, "monthly report", (r) => r.status === 200);
}

function manilaDate() {
  return new Date(Date.now() + MANILA_OFFSET_MS).toISOString().slice(0, 10);
}

function observe(res, metric, tags, name, expected) {
  if (metric) metric.add(res.timings.duration, tags);
  const ok = expected(res);
  metrics.errorRate.add(!ok, tags);
  if (!ok) console.log(`${name}: ${res.status} ${res.body}`);
  check(res, { [name]: expected });
  return ok;
}
