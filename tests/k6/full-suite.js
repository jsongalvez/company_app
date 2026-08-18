import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, uuid, authHeaders, metrics, thresholdProfiles } from "./helpers.js";

const USERNAME = __ENV.TEST_USERNAME || "";
const PASSWORD = __ENV.TEST_PASSWORD || "";

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
  observe(res, null, {}, "setup login", (r) => r.status === 200);
  const token = res.json("token");
  const me = http.get(`${BASE_URL}/api/me`, { headers: authHeaders(token) });
  observe(me, null, {}, "setup current user", (r) => r.status === 200);
  const userId = me.json("id");
  const branches = http.get(`${BASE_URL}/api/branches`, { headers: authHeaders(token) });
  observe(branches, null, {}, "setup fixture branches", (r) => r.status === 200);
  const branch = branches.json().find((candidate) => candidate.name === "K6 Fixture Branch");
  if (!branch) throw new Error("Dev seeder did not create K6 Fixture Branch");
  const clockIn = http.post(`${BASE_URL}/api/attendance/clock-in`, JSON.stringify({
    attendanceId: uuid(), branchId: branch.id,
  }), { headers: authHeaders(token) });
  observe(clockIn, null, {}, "setup fixture clock-in", (r) => r.status === 200 || r.status === 201 || r.status === 409);
  let branchDayId = clockIn.json("branchDayId");
  if (!branchDayId) {
    const today = http.get(`${BASE_URL}/api/branches/${branch.id}/today`, { headers: authHeaders(token) });
    observe(today, null, {}, "setup fixture day", (r) => r.status === 200);
    branchDayId = today.json("branchDayId");
  }
  return { token, userId, branchId: branch.id, branchDayId };
}

export default function (data) {
  const headers = authHeaders(data.token);
  const tid = uuid();

  const branch = { id: data.branchId, dayId: data.branchDayId };
  const clientId = createClient(headers, tid);
  const session = createSession(headers, tid, branch.id, clientId);
  updateSessionStatus(headers, tid, session.id);
  voidAndUnvoidSession(headers, tid, session.id);
  managePractitioners(headers, tid, session.id, data.userId);
  const catId = createProductCategory(headers, tid);
  const prodId = createProduct(headers, tid, catId);
  createInventoryCard(headers, tid, branch.id, prodId);
  const inventoryVersion = restockInventory(headers, tid, branch.id, prodId, branch.dayId);
  if (__VU === 1 && __ITER === 0) {
    createProductSale(headers, tid, branch.dayId, prodId, session.id, inventoryVersion);
    createCompensation(headers, tid, branch.dayId, data.userId);
  }
  createExpense(headers, tid, branch.dayId);
  createAllowance(headers, tid, branch.dayId, data.userId);
  if (__VU === 1 && __ITER === 0) {
    createAndSubmitRemittance(headers, tid, branch.id, branch.dayId, session.id);
  }
  fetchNotifications(headers, tid);
  fetchReports(headers, tid, branch.id);

  sleep(0.5);
}

function createClient(headers, tid) {
  const tags = { group: "client" };
  const clientId = uuid();
  const res = http.post(`${BASE_URL}/api/clients`, JSON.stringify({
    id: clientId, firstName: `K6First-${tid}`, lastName: `K6Last-${tid}`,
    gender: "F", age: 30, phoneNumber: "09170000000",
  }), { headers });
  observe(res, metrics.clientLatency, tags, "create client", (r) => r.status === 201 || r.status === 200);

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
  observe(res, metrics.sessionLatency, tags, "create session", (r) => r.status === 201 || r.status === 200);
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
  observe(res, metrics.productLatency, tags, "create category", (r) => r.status === 201 || r.status === 200);

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
  observe(res, metrics.productLatency, tags, "create product", (r) => r.status === 201 || r.status === 200);

  observe(http.get(`${BASE_URL}/api/products`, { headers }), metrics.productLatency, tags, "list products", (r) => r.status === 200);
  observe(http.get(`${BASE_URL}/api/products/${prodId}`, { headers }), metrics.productLatency, tags, "get product", (r) => r.status === 200);
  return prodId;
}

function createInventoryCard(headers, tid, branchId, prodId) {
  const tags = { group: "inventory" };
  const res = http.post(`${BASE_URL}/api/branches/${branchId}/inventory`, JSON.stringify({
    productId: prodId,
  }), { headers });
  observe(res, metrics.inventoryLatency, tags, "create inventory card", (r) => r.status === 201 || r.status === 200);
}

function restockInventory(headers, tid, branchId, prodId, branchDayId) {
  const tags = { group: "inventory" };
  const res = http.post(`${BASE_URL}/api/branches/${branchId}/inventory/${prodId}/restock`, JSON.stringify({
    id: uuid(), quantity: 50, branchDayId,
    editReason: "K6 repeated-workflow fixture",
  }), { headers });
  observe(res, metrics.inventoryLatency, tags, "restock inventory", (r) => r.status === 201 || r.status === 200);

  const listRes = http.get(`${BASE_URL}/api/branches/${branchId}/inventory`, { headers });
  observe(listRes, metrics.inventoryLatency, tags, "list inventory", (r) => r.status === 200);
  const inventory = listRes.json();
  return inventory.length ? inventory[0].version : 1;
}

function recordInventoryMovement(headers, tid, branchId, prodId, branchDayId, version) {
  const tags = { group: "inventory" };
  const movementRes = http.post(`${BASE_URL}/api/branches/${branchId}/inventory/${prodId}/movement`, JSON.stringify({
    movementId: uuid(), reason: "ADJUSTMENT", quantityChange: 5, branchDayId, expectedVersion: version,
  }), { headers });
  observe(movementRes, metrics.inventoryLatency, tags, "record inventory movement", (r) => r.status === 201 || r.status === 200);
  return version + 1;
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
  const today = new Date().toISOString().slice(0, 10);

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
  const today = new Date().toISOString().slice(0, 10);
  const year = today.slice(0, 4);
  const month = today.slice(5, 7);

  const dailyRes = http.get(`${BASE_URL}/api/branches/${branchId}/daily-summary?date=${today}`, { headers });
  observe(dailyRes, metrics.reportLatency, tags, "daily report", (r) => r.status === 200);

  const monthlyRes = http.get(`${BASE_URL}/api/branches/${branchId}/monthly-summary?year=${year}&month=${month}`, { headers });
  observe(monthlyRes, metrics.reportLatency, tags, "monthly report", (r) => r.status === 200);
}

function observe(res, metric, tags, name, expected) {
  if (metric) metric.add(res.timings.duration, tags);
  const ok = expected(res);
  metrics.errorRate.add(!ok, tags);
  if (!ok) console.log(`${name}: ${res.status} ${res.body}`);
  check(res, { [name]: expected });
  return ok;
}
