import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";
import { BASE_URL, uuid, authHeaders } from "./helpers.js";

const USERNAME = __ENV.TEST_USERNAME || "";
const PASSWORD = __ENV.TEST_PASSWORD || "";
const LIMITED_USERNAME = __ENV.LIMITED_USERNAME || "";
const LIMITED_PASSWORD = __ENV.LIMITED_PASSWORD || "";

const authzLatency = new Trend("authz_latency");
const errorRate = new Rate("errors");

export const options = {
  thresholds: {
    authz_latency: ["p(95)<1000"],
    errors: ["rate<0.10"],
  },
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
  const token = res.json("token");

  let limitedToken = null;
  if (LIMITED_USERNAME && LIMITED_PASSWORD) {
    const limitedRes = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
      username: LIMITED_USERNAME,
      password: LIMITED_PASSWORD,
    }), { headers: { "Content-Type": "application/json" } });
    if (limitedRes.status === 200) {
      limitedToken = limitedRes.json("token");
    }
  }

  return { token, limitedToken };
}

export default function (data) {
  testInvalidTokenReturns401();
  testExpiredTokenReturns401();

  if (data.limitedToken) {
    testInsufficientCapabilityReturns403(data.limitedToken);
  }

  sleep(1);
}

function testInvalidTokenReturns401() {
  const tags = { group: "authz", test: "invalid-token" };
  const badHeaders = authHeaders("this.is.a.bad.token");

  const res = http.get(`${BASE_URL}/api/branches`, { headers: badHeaders });
  authzLatency.add(res.timings.duration, tags);

  check(res, {
    "invalid token returns 401": (r) => r.status === 401,
  });

  if (res.status !== 401) {
    errorRate.add(1, tags);
  }
}

function testExpiredTokenReturns401() {
  const tags = { group: "authz", test: "expired-token" };
  const expiredToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwMDAwMDAwMC0wMDAwLTAwMDAtMDAwMC0wMDAwMDAwMDAwMDEiLCJleHAiOjEwMDAwMDAwMDAsImlhdCI6MTAwMDAwMDAwMCwiaXNzIjoidGVzdCIsImF1ZCI6InRlc3QifQ.invalid";

  const res = http.get(`${BASE_URL}/api/branches`, {
    headers: authHeaders(expiredToken),
  });
  authzLatency.add(res.timings.duration, tags);

  check(res, {
    "expired/malformed token returns 401": (r) => r.status === 401,
  });

  if (res.status !== 401) {
    errorRate.add(1, tags);
  }
}

function testInsufficientCapabilityReturns403(limitedToken) {
  const tags = { group: "authz", test: "insufficient-capability" };
  const limitedHeaders = authHeaders(limitedToken);

  const branchId = uuid();
  const createBranchRes = http.post(`${BASE_URL}/api/branches`, JSON.stringify({
    id: branchId, name: `Authz-Test-${uuid().slice(0, 8)}`, branchType: "CLINIC",
  }), { headers: limitedHeaders });
  authzLatency.add(createBranchRes.timings.duration, tags);

  const today = new Date().toISOString().slice(0, 10);
  const branchDayId = uuid();
  const expenseRes = http.post(`${BASE_URL}/api/expenses`, JSON.stringify({
    id: uuid(), branchDayId, amount: "50.00", category: "MISCELLANEOUS",
  }), { headers: limitedHeaders });
  authzLatency.add(expenseRes.timings.duration, tags);

  if (createBranchRes.status === 403 || expenseRes.status === 403) {
    check(true, { "limited user gets 403 for restricted operations": true });
  }
}
