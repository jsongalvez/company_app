import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, uuid, authHeaders, metrics } from "./helpers.js";

const USERNAME = __ENV.TEST_USERNAME || "";
const PASSWORD = __ENV.TEST_PASSWORD || "";

export const options = {
  thresholds: {
    remittance_race_latency: ["p(95)<3000"],
    errors: ["rate<0.20"],
  },
  stages: [
    { duration: "5s", target: 5 },
    { duration: "10s", target: 5 },
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
  const token = res.json("token");
  const headers = authHeaders(token);

  const branchId = uuid();
  http.post(`${BASE_URL}/api/branches`, JSON.stringify({
    id: branchId, name: `Remit-Race-${uuid().slice(0, 6)}`, branchType: "CLINIC",
  }), { headers });

  const clientId = uuid();
  http.post(`${BASE_URL}/api/clients`, JSON.stringify({
    id: clientId, firstName: "RemitRace", lastName: "Client",
    gender: "F", age: 30,
  }), { headers });

  const today = new Date().toISOString().slice(0, 10);

  http.post(`${BASE_URL}/api/sessions`, JSON.stringify({
    id: uuid(), clientId, branchId,
    isWalkIn: true, finalPrice: "2000.00",
  }), { headers });

  const remittanceId = uuid();
  const remitRes = http.post(`${BASE_URL}/api/remittances`, JSON.stringify({
    id: remittanceId,
    type: "SESSION",
    branchId: branchId,
    method: "BANK_TRANSFER",
    dateRangeStart: today,
    dateRangeEnd: today,
  }), { headers });

  check(remitRes, {
    "remittance draft created": (r) => r.status === 201 || r.status === 200,
  });

  return { token, remittanceId };
}

export default function (data) {
  const tags = { group: "remittance-race" };
  const headers = authHeaders(data.token);

  const submitRes = http.post(`${BASE_URL}/api/remittances/${data.remittanceId}/submit`, JSON.stringify({
    expectedVersion: 1,
  }), { headers });

  metrics.remittanceRaceLatency.add(submitRes.timings.duration, tags);

  if (submitRes.status === 200) {
    metrics.errorRate.add(0, tags);
  } else if (submitRes.status === 409) {
    check(true, { "concurrent submit returns 409 on conflict": true });
  } else {
    metrics.errorRate.add(1, tags);
  }

  check(submitRes, {
    "remittance submit returns 200 (winner) or 409 (loser)": (r) =>
      r.status === 200 || r.status === 409,
  });

  sleep(0.5);
}
