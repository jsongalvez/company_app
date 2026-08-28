import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, uuid, authHeaders, metrics, thresholdProfiles } from "./helpers.js";

const USERNAME = __ENV.TEST_USERNAME || "";
const PASSWORD = __ENV.TEST_PASSWORD || "";
const FIXTURE_BRANCH_NAME = "K6 Fixture Branch";

export const options = {
  thresholds: thresholdProfiles.concurrency,
  stages: [
    { duration: "5s", target: 3 },
    { duration: "10s", target: 3 },
    { duration: "5s", target: 0 },
  ],
};

export function setup() {
  if (!USERNAME || !PASSWORD) {
    throw new Error("Set TEST_USERNAME and TEST_PASSWORD environment variables");
  }

  const login = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: USERNAME,
    password: PASSWORD,
  }), { headers: { "Content-Type": "application/json" } });
  requireResponse(login, "setup login", (r) => r.status === 200);

  const token = login.json("token");
  if (!token) throw new Error("Setup login did not return token");
  const headers = authHeaders(token);
  const me = http.get(`${BASE_URL}/api/me`, { headers });
  requireResponse(me, "setup current user", (r) => r.status === 200);
  const userId = me.json("id");
  if (!userId) throw new Error("Setup current user did not return id");

  const branches = http.get(`${BASE_URL}/api/branches`, { headers });
  requireResponse(branches, "setup branches", (r) => r.status === 200);
  const branch = branches.json().find((candidate) => candidate.name === FIXTURE_BRANCH_NAME);
  if (!branch) throw new Error(`Dev seeder did not create ${FIXTURE_BRANCH_NAME}`);
  if (!branch.id) throw new Error("Setup fixture branch did not return id");

  const clockIn = http.post(`${BASE_URL}/api/attendance/clock-in`, JSON.stringify({
    attendanceId: uuid(),
    branchId: branch.id,
  }), { headers });
  requireResponse(clockIn, "setup clock-in", (r) => r.status === 200 || r.status === 201 || r.status === 409);

  let branchDayId = clockIn.json("branchDayId");
  if (!branchDayId) {
    const today = http.get(`${BASE_URL}/api/branches/${branch.id}/today`, { headers });
    requireResponse(today, "setup branch day", (r) => r.status === 200);
    branchDayId = today.json("branchDayId");
  }
  if (!branchDayId) throw new Error("Setup did not return branchDayId");

  return { token, userId, branchId: branch.id, branchDayId };
}

export default function (data) {
  const headers = authHeaders(data.token);

  testIdempotentCreate(headers, data.branchId);
  testSessionPendingGuard(headers, data.branchId);
  testVersionMismatch(headers, data.branchId);
  testDuplicateIdempotency(headers, data.branchDayId);

  sleep(1);
}

function testIdempotentCreate(headers, branchId) {
  const tags = { group: "concurrency", test: "idempotent" };
  const clientId = uuid();
  const client = http.post(`${BASE_URL}/api/clients`, JSON.stringify({
    id: clientId, firstName: `Concurrent-${clientId}`, lastName: "Test", gender: "F", age: 25,
  }), { headers });
  observe(client, tags, "idempotent client", (r) => r.status === 201 || r.status === 200);
  if (client.status >= 400) return;

  const sessionId = uuid();
  const body = JSON.stringify({ id: sessionId, clientId, branchId, isWalkIn: true, finalPrice: "2500.00" });
  const first = http.post(`${BASE_URL}/api/sessions`, body, { headers });
  observe(first, tags, "idempotent create", (r) => r.status === 200 || r.status === 201);
  if (first.status >= 400) return;

  const retry = http.post(`${BASE_URL}/api/sessions`, body, { headers });
  observe(retry, tags, "idempotent retry", (r) => r.status === 200);
  check(retry, {
    "sequential duplicate session UUID returns 200": (r) => r.status === 200,
  });
}

function testSessionPendingGuard(headers, branchId) {
  const tags = { group: "concurrency", test: "pending-guard" };
  const clientId = uuid();
  const client = http.post(`${BASE_URL}/api/clients`, JSON.stringify({
    id: clientId, firstName: `Pending-${clientId}`, lastName: "Test", gender: "M", age: 30,
  }), { headers });
  observe(client, tags, "pending client", (r) => r.status === 201 || r.status === 200);
  if (client.status >= 400) return;

  const requests = [1, 2].map(() => ["POST", `${BASE_URL}/api/sessions`, JSON.stringify({
    id: uuid(), clientId, branchId, isWalkIn: false, finalPrice: "2500.00",
  }), { headers }]);
  const sessions = http.batch(requests);
  sessions.forEach((res) => observe(res, tags, "pending guard", (r) => r.status === 201 || r.status === 200 || r.status === 409));

  check(sessions, {
    "concurrent PENDING sessions allow one and reject one": (responses) =>
      responses.filter((r) => r.status === 201 || r.status === 200).length === 1 &&
      responses.filter((r) => r.status === 409).length === 1,
  });
}

function testVersionMismatch(headers, branchId) {
  const tags = { group: "concurrency", test: "version-mismatch" };
  const clientId = uuid();
  const client = http.post(`${BASE_URL}/api/clients`, JSON.stringify({
    id: clientId, firstName: `Version-${clientId}`, lastName: "Test", gender: "F", age: 28,
  }), { headers });
  observe(client, tags, "version client", (r) => r.status === 201 || r.status === 200);
  if (client.status >= 400) return;

  const sessionId = uuid();
  const session = http.post(`${BASE_URL}/api/sessions`, JSON.stringify({
    id: sessionId, clientId, branchId, isWalkIn: true, finalPrice: "2000.00",
  }), { headers });
  observe(session, tags, "version session", (r) => r.status === 201 || r.status === 200);
  if (session.status >= 400) return;

  const update = http.patch(`${BASE_URL}/api/sessions/${sessionId}/status`, JSON.stringify({
    status: "COMPLETED", version: 999,
  }), { headers });
  observe(update, tags, "version mismatch", (r) => r.status === 409);
}

function testDuplicateIdempotency(headers, branchDayId) {
  const tags = { group: "concurrency", test: "duplicate-uuid" };
  const expenseId = uuid();
  const body = JSON.stringify({ id: expenseId, branchDayId, amount: "100.00", category: "MISCELLANEOUS" });
  const expenses = http.batch([
    ["POST", `${BASE_URL}/api/expenses`, body, { headers }],
    ["POST", `${BASE_URL}/api/expenses`, body, { headers }],
  ]);
  expenses.forEach((res) => observe(res, tags, "duplicate expense", (r) => r.status === 200 || r.status === 201));

  check(expenses, {
    "duplicate expense UUID has one create and one idempotent response": (responses) =>
      responses.filter((r) => r.status === 201).length === 1 &&
      responses.filter((r) => r.status === 200).length === 1,
  });
}

function observe(res, tags, name, expected) {
  metrics.concurrencyLatency.add(res.timings.duration, tags);
  const ok = expected(res);
  metrics.errorRate.add(!ok, tags);
  check(res, { [name]: expected });
  if (!ok) console.log(`${name}: ${res.status} ${res.body}`);
  return ok;
}

function requireResponse(res, name, expected) {
  if (!expected(res)) throw new Error(`${name} failed: ${res.status} ${res.body}`);
}
