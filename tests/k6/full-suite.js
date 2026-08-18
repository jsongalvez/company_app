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
  check(res, { "setup login": (r) => r.status === 200 });
  return { token: res.json("token") };
}

export default function (data) {
  const headers = authHeaders(data.token);
  const tid = uuid();

  const branchId = createBranch(headers, tid);
  const clientId = createClient(headers, tid);
  const sessionId = createSession(headers, tid, branchId, clientId);
  updateSessionStatus(headers, tid, sessionId);
  voidAndUnvoidSession(headers, tid, sessionId);
  managePractitioners(headers, tid, sessionId);
  const catId = createProductCategory(headers, tid);
  const prodId = createProduct(headers, tid, catId);
  createInventoryCard(headers, tid, branchId, prodId);
  restockInventory(headers, tid, branchId, prodId);
  createProductSale(headers, tid, branchId, prodId, sessionId);
  createCompensation(headers, tid);
  createExpense(headers, tid);
  createAllowance(headers, tid);
  createAndSubmitRemittance(headers, tid, branchId);
  fetchNotifications(headers, tid);
  fetchReports(headers, tid, branchId);

  sleep(0.5);
}

function createBranch(headers, tid) {
  const tags = { group: "branch" };
  const branchId = uuid();
  const res = http.post(`${BASE_URL}/api/branches`, JSON.stringify({
    id: branchId, name: `K6 Branch ${tid}`, branchType: "CLINIC",
  }), { headers });
  metrics.branchLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);
  check(res, { "create branch": (r) => r.status === 201 || r.status === 200 });

  const listRes = http.get(`${BASE_URL}/api/branches`, { headers });
  metrics.branchLatency.add(listRes.timings.duration, tags);

  const getRes = http.get(`${BASE_URL}/api/branches/${branchId}`, { headers });
  metrics.branchLatency.add(getRes.timings.duration, tags);

  return branchId;
}

function createClient(headers, tid) {
  const tags = { group: "client" };
  const clientId = uuid();
  const res = http.post(`${BASE_URL}/api/clients`, JSON.stringify({
    id: clientId, firstName: `K6First-${tid}`, lastName: `K6Last-${tid}`,
    gender: "F", age: 30, phoneNumber: "09170000000",
  }), { headers });
  metrics.clientLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);

  const searchRes = http.get(`${BASE_URL}/api/clients?q=K6First`, { headers });
  metrics.clientLatency.add(searchRes.timings.duration, tags);

  const getRes = http.get(`${BASE_URL}/api/clients/${clientId}`, { headers });
  metrics.clientLatency.add(getRes.timings.duration, tags);

  const updRes = http.patch(`${BASE_URL}/api/clients/${clientId}`, JSON.stringify({
    phoneNumber: "09171111111",
  }), { headers });
  metrics.clientLatency.add(updRes.timings.duration, tags);

  return clientId;
}

function createSession(headers, tid, branchId, clientId) {
  const tags = { group: "session" };
  const sessionId = uuid();
  const res = http.post(`${BASE_URL}/api/sessions`, JSON.stringify({
    id: sessionId, clientId, branchId, isWalkIn: true, finalPrice: "2500.00",
  }), { headers });
  metrics.sessionLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);
  check(res, { "create session": (r) => r.status === 201 || r.status === 200 });
  return sessionId;
}

function updateSessionStatus(headers, tid, sessionId) {
  const tags = { group: "session" };
  const res = http.patch(`${BASE_URL}/api/sessions/${sessionId}/status`, JSON.stringify({
    status: "COMPLETED", version: 1,
  }), { headers });
  metrics.sessionLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);
}

function voidAndUnvoidSession(headers, tid, sessionId) {
  const tags = { group: "session" };

  const voidId = uuid();
  const voidRes = http.post(`${BASE_URL}/api/sessions/${sessionId}/void`, JSON.stringify({
    id: voidId, voidReason: "K6 load test void",
  }), { headers });
  metrics.sessionLatency.add(voidRes.timings.duration, tags);
  metrics.errorRate.add(voidRes.status >= 400, tags);

  const unvoidRes = http.post(`${BASE_URL}/api/sessions/${sessionId}/unvoid`, JSON.stringify({
    unvoidedReason: "K6 load test unvoid",
  }), { headers });
  metrics.sessionLatency.add(unvoidRes.timings.duration, tags);
  metrics.errorRate.add(unvoidRes.status >= 400, tags);
}

function managePractitioners(headers, tid, sessionId) {
  const tags = { group: "session" };

  const pId = uuid();
  const addRes = http.post(`${BASE_URL}/api/sessions/${sessionId}/practitioners`, JSON.stringify({
    id: pId, practitionerId: tid,
  }), { headers });
  metrics.sessionLatency.add(addRes.timings.duration, tags);

  if (addRes.status === 201 || addRes.status === 200) {
    const updRes = http.patch(`${BASE_URL}/api/sessions/${sessionId}/practitioners/${tid}`,
      JSON.stringify({ remarks: "K6 test remarks" }), { headers });
    metrics.sessionLatency.add(updRes.timings.duration, tags);

    const delRes = http.del(`${BASE_URL}/api/sessions/${sessionId}/practitioners/${tid}`, null, { headers });
    metrics.sessionLatency.add(delRes.timings.duration, tags);
  }

  const concernsRes = http.get(`${BASE_URL}/api/concerns`, { headers });
  metrics.sessionLatency.add(concernsRes.timings.duration, tags);
}

function createProductCategory(headers, tid) {
  const tags = { group: "product" };
  const catId = uuid();
  const res = http.post(`${BASE_URL}/api/product-categories`, JSON.stringify({
    id: catId, name: `K6 Category ${tid}`,
  }), { headers });
  metrics.productLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);

  http.get(`${BASE_URL}/api/product-categories`, { headers });
  http.get(`${BASE_URL}/api/product-categories/${catId}`, { headers });

  return catId;
}

function createProduct(headers, tid, catId) {
  const tags = { group: "product" };
  const prodId = uuid();
  const res = http.post(`${BASE_URL}/api/products`, JSON.stringify({
    id: prodId, name: `K6 Product ${tid}`, productCategoryId: catId,
    unitPrice: "500.00", commissionAmount: "50.00",
  }), { headers });
  metrics.productLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);

  http.get(`${BASE_URL}/api/products`, { headers });
  http.get(`${BASE_URL}/api/products/${prodId}`, { headers });
  return prodId;
}

function createInventoryCard(headers, tid, branchId, prodId) {
  const tags = { group: "inventory" };
  const res = http.post(`${BASE_URL}/api/branches/${branchId}/inventory`, JSON.stringify({
    productId: prodId,
  }), { headers });
  metrics.inventoryLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);
  check(res, { "create inventory card": (r) => r.status === 201 || r.status === 200 });
}

function restockInventory(headers, tid, branchId, prodId) {
  const tags = { group: "inventory" };
  const today = new Date().toISOString().slice(0, 10);
  const branchDayId = uuid();

  const res = http.post(`${BASE_URL}/api/branches/${branchId}/inventory/${prodId}/restock`, JSON.stringify({
    id: uuid(), quantity: 50, branchDayId,
  }), { headers });
  metrics.inventoryLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);

  const listRes = http.get(`${BASE_URL}/api/branches/${branchId}/inventory`, { headers });
  metrics.inventoryLatency.add(listRes.timings.duration, tags);

  const movementRes = http.post(`${BASE_URL}/api/branches/${branchId}/inventory/${prodId}/movement`, JSON.stringify({
    movementId: uuid(), reason: "ADJUSTMENT", quantityChange: 5, branchDayId: uuid(), expectedVersion: 1,
  }), { headers });
  metrics.inventoryLatency.add(movementRes.timings.duration, tags);
}

function createProductSale(headers, tid, branchId, prodId, sessionId) {
  const tags = { group: "sale" };
  const branchDayId = uuid();

  const res = http.post(`${BASE_URL}/api/product-sales`, JSON.stringify({
    id: uuid(), branchDayId, sessionId, clientId: uuid(),
    isWalkIn: false, productId: prodId, quantity: 1, expectedVersion: 1,
  }), { headers });
  metrics.saleLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);
}

function createCompensation(headers, tid) {
  const tags = { group: "compensation" };
  const compId = uuid();
  const workBranchDayId = uuid();
  const payingBranchDayId = uuid();

  const res = http.post(`${BASE_URL}/api/compensation`, JSON.stringify({
    id: compId, workBranchDayId, payingBranchDayId,
    userId: tid, amount: "500.00", note: "K6 test compensation",
  }), { headers });
  metrics.compensationLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);
}

function createExpense(headers, tid) {
  const tags = { group: "expense" };
  const branchDayId = uuid();
  const expenseId = uuid();

  const res = http.post(`${BASE_URL}/api/expenses`, JSON.stringify({
    id: expenseId, branchDayId, amount: "100.00", category: "MISCELLANEOUS",
  }), { headers });
  metrics.expenseLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);

  const listRes = http.get(`${BASE_URL}/api/expenses?branchDayId=${branchDayId}`, { headers });
  metrics.expenseLatency.add(listRes.timings.duration, tags);
}

function createAllowance(headers, tid) {
  const tags = { group: "allowance" };
  const branchDayId = uuid();

  const res = http.post(`${BASE_URL}/api/allowances`, JSON.stringify({
    id: uuid(), branchDayId, userId: tid, amount: "200.00",
  }), { headers });
  metrics.allowanceLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);

  const listRes = http.get(`${BASE_URL}/api/allowances?branchDayId=${branchDayId}`, { headers });
  metrics.allowanceLatency.add(listRes.timings.duration, tags);
}

function createAndSubmitRemittance(headers, tid, branchId) {
  const tags = { group: "remittance" };
  const today = new Date().toISOString().slice(0, 10);

  const remitRes = http.post(`${BASE_URL}/api/remittances`, JSON.stringify({
    id: uuid(), type: "SESSION", branchId,
    method: "BANK_TRANSFER", dateRangeStart: today, dateRangeEnd: today,
  }), { headers });
  metrics.remittanceLatency.add(remitRes.timings.duration, tags);
  metrics.errorRate.add(remitRes.status >= 400, tags);

  const remittanceId = remitRes.json("id");
  if (remittanceId) {
    const submitRes = http.post(`${BASE_URL}/api/remittances/${remittanceId}/submit`, JSON.stringify({
      expectedVersion: 1,
    }), { headers });
    metrics.remittanceLatency.add(submitRes.timings.duration, tags);
    metrics.errorRate.add(submitRes.status >= 400 && submitRes.status !== 409, tags);
  }
}

function fetchNotifications(headers, tid) {
  const tags = { group: "notification" };
  const res = http.get(`${BASE_URL}/api/notifications`, { headers });
  metrics.notificationLatency.add(res.timings.duration, tags);
  metrics.errorRate.add(res.status >= 400, tags);
}

function fetchReports(headers, tid, branchId) {
  const tags = { group: "report" };
  const today = new Date().toISOString().slice(0, 10);
  const year = today.slice(0, 4);
  const month = today.slice(5, 7);

  const dailyRes = http.get(`${BASE_URL}/api/branches/${branchId}/daily-summary?date=${today}`, { headers });
  metrics.reportLatency.add(dailyRes.timings.duration, tags);

  const monthlyRes = http.get(`${BASE_URL}/api/branches/${branchId}/monthly-summary?year=${year}&month=${month}`, { headers });
  metrics.reportLatency.add(monthlyRes.timings.duration, tags);
}
