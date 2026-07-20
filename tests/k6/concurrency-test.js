import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";
import { BASE_URL, uuid, authHeaders, metrics, thresholds } from "./helpers.js";

const USERNAME = __ENV.TEST_USERNAME || "";
const PASSWORD = __ENV.TEST_PASSWORD || "";

export const options = {
  thresholds: {
    concurrency_latency: ["p(95)<1000"],
    errors: ["rate<0.10"],
  },
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
  const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
    username: USERNAME,
    password: PASSWORD,
  }), { headers: { "Content-Type": "application/json" } });
  check(res, { "setup login": (r) => r.status === 200 });
  return { token: res.json("token") };
}

export default function (data) {
  const headers = authHeaders(data.token);
  const testId = uuid();

  testIdempotentCreate(headers, testId);
  testSessionPendingGuard(headers, testId);
  testVersionMismatch(headers, testId);
  testDuplicateIdempotency(headers, testId);

  sleep(1);
}

function testIdempotentCreate(headers, testId) {
  const tags = { group: "concurrency", test: "idempotent" };
  const branchId = uuid();
  const clientId = uuid();

  http.post(`${BASE_URL}/api/branches`, JSON.stringify({
    id: branchId, name: `K6-Concurrent-Branch-${testId}`, branchType: "CLINIC",
  }), { headers });

  http.post(`${BASE_URL}/api/clients`, JSON.stringify({
    id: clientId, firstName: `Concurrent-${testId}`,
    lastName: `Test-${testId}`, gender: "F", age: 25,
  }), { headers });

  const sessionId = uuid();
  const create1 = http.post(`${BASE_URL}/api/sessions`, JSON.stringify({
    id: sessionId, clientId, branchId,
    isWalkIn: true, finalPrice: "2500.00",
  }), { headers });

  metrics.concurrencyLatency.add(create1.timings.duration, tags);
  const isFirstCreated = create1.status === 201 || create1.status === 200;
  metrics.errorRate.add(!isFirstCreated, tags);

  const create2 = http.post(`${BASE_URL}/api/sessions`, JSON.stringify({
    id: sessionId, clientId, branchId,
    isWalkIn: true, finalPrice: "2500.00",
  }), { headers });

  metrics.concurrencyLatency.add(create2.timings.duration, tags);
  metrics.errorRate.add(create2.status >= 400, tags);

  check(create2, {
    "idempotent create returns 200 on duplicate UUID": (r) => r.status === 200,
  });
}

function testSessionPendingGuard(headers, testId) {
  const tags = { group: "concurrency", test: "pending-guard" };
  const branchId = uuid();
  const clientId = uuid();

  http.post(`${BASE_URL}/api/branches`, JSON.stringify({
    id: branchId, name: `K6-Pending-Branch-${testId}`, branchType: "CLINIC",
  }), { headers });

  http.post(`${BASE_URL}/api/clients`, JSON.stringify({
    id: clientId, firstName: `Pending-${testId}`,
    lastName: `Test-${testId}`, gender: "M", age: 30,
  }), { headers });

  const session1Id = uuid();
  const s1 = http.post(`${BASE_URL}/api/sessions`, JSON.stringify({
    id: session1Id, clientId, branchId,
    isWalkIn: false, finalPrice: "2500.00",
    bookedAt: new Date().toISOString(),
  }), { headers });

  if (s1.status === 201 || s1.status === 200) {
    const session2Id = uuid();
    const s2 = http.post(`${BASE_URL}/api/sessions`, JSON.stringify({
      id: session2Id, clientId, branchId,
      isWalkIn: false, finalPrice: "2500.00",
      bookedAt: new Date().toISOString(),
    }), { headers });

    metrics.concurrencyLatency.add(s2.timings.duration, tags);

    check(s2, {
      "duplicate PENDING session returns 409": (r) => r.status === 409,
    });

    if (s2.status !== 409) {
      metrics.errorRate.add(1, tags);
    }
  }
}

function testVersionMismatch(headers, testId) {
  const tags = { group: "concurrency", test: "version-mismatch" };
  const branchId = uuid();
  const clientId = uuid();

  http.post(`${BASE_URL}/api/branches`, JSON.stringify({
    id: branchId, name: `K6-Version-Branch-${testId}`, branchType: "CLINIC",
  }), { headers });

  http.post(`${BASE_URL}/api/clients`, JSON.stringify({
    id: clientId, firstName: `Version-${testId}`,
    lastName: `Test-${testId}`, gender: "F", age: 28,
  }), { headers });

  const sessionId = uuid();
  const s = http.post(`${BASE_URL}/api/sessions`, JSON.stringify({
    id: sessionId, clientId, branchId,
    isWalkIn: true, finalPrice: "2000.00",
  }), { headers });

  if (s.status === 201 || s.status === 200) {
    const wrongVersion = 999;
    const updateRes = http.patch(`${BASE_URL}/api/sessions/${sessionId}/status`, JSON.stringify({
      status: "COMPLETED",
      version: wrongVersion,
    }), { headers });

    metrics.concurrencyLatency.add(updateRes.timings.duration, tags);

    check(updateRes, {
      "version mismatch returns 409": (r) => r.status === 409,
    });

    if (updateRes.status !== 409) {
      metrics.errorRate.add(1, tags);
    }
  }
}

function testDuplicateIdempotency(headers, testId) {
  const tags = { group: "concurrency", test: "duplicate-uuid" };
  const branchId = uuid();
  const clientId = uuid();
  const expenseId = uuid();

  http.post(`${BASE_URL}/api/branches`, JSON.stringify({
    id: branchId, name: `K6-Dup-Branch-${testId}`, branchType: "CLINIC",
  }), { headers });

  const today = new Date().toISOString().slice(0, 10);
  const branchDayRes = http.get(`${BASE_URL}/api/branches/${branchId}/daily-summary?date=${today}`, { headers });
  const branchDayId = branchDayRes.json("branchDayId") || uuid();

  http.post(`${BASE_URL}/api/clients`, JSON.stringify({
    id: clientId, firstName: `Dup-${testId}`,
    lastName: `Test-${testId}`, gender: "M", age: 35,
  }), { headers });

  const e1 = http.post(`${BASE_URL}/api/expenses`, JSON.stringify({
    id: expenseId, branchDayId, amount: "100.00",
    category: "MISCELLANEOUS",
  }), { headers });

  const e2 = http.post(`${BASE_URL}/api/expenses`, JSON.stringify({
    id: expenseId, branchDayId, amount: "100.00",
    category: "MISCELLANEOUS",
  }), { headers });

  metrics.concurrencyLatency.add(e2.timings.duration, tags);

  check(e2, {
    "duplicate UUID expense returns 200": (r) => r.status === 200,
  });
}
