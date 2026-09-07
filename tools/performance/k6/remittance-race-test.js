import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, uuid, authHeaders, metrics, thresholdProfiles } from "./helpers.js";

const USERNAME = __ENV.TEST_USERNAME || "";
const PASSWORD = __ENV.TEST_PASSWORD || "";
const MANILA_OFFSET_MS = 8 * 60 * 60 * 1000;
const FIXTURE_BRANCH_NAME = "K6 Fixture Branch";

export const options = {
  thresholds: thresholdProfiles.remittanceRace,
  vus: 1,
  iterations: 1,
};

export function setup() {
  if (!USERNAME || !PASSWORD) {
    throw new Error("Set TEST_USERNAME and TEST_PASSWORD environment variables");
  }

   const res = http.post(`${BASE_URL}/auth/login`, JSON.stringify({
     username: USERNAME,
     password: PASSWORD,
   }), { headers: { "Content-Type": "application/json" } });
   requireResponse(res, "setup login", (r) => r.status === 200);
   const token = res.json("token");
   if (!token) throw new Error("Setup login did not return token");
   const headers = authHeaders(token);

   const branchRes = http.get(`${BASE_URL}/api/branches`, { headers });
   requireResponse(branchRes, "setup branch", (r) => r.status === 200);
   const branch = branchRes.json().find((candidate) => candidate.name === FIXTURE_BRANCH_NAME);
   if (!branch || !branch.id) throw new Error(`Setup did not find ${FIXTURE_BRANCH_NAME}`);
   const branchId = branch.id;

   const attendanceId = uuid();
   const clockInRes = http.post(`${BASE_URL}/api/attendance/clock-in`, JSON.stringify({
     attendanceId, branchId,
   }), { headers });
   requireResponse(clockInRes, "setup clock-in", (r) => r.status === 201);
   if (clockInRes.json("id") !== attendanceId) throw new Error("Setup clock-in returned unexpected id");
   if (!clockInRes.json("branchDayId")) throw new Error("Setup clock-in did not return branch day id");

   const clientId = uuid();
   const clientRes = http.post(`${BASE_URL}/api/clients`, JSON.stringify({
     id: clientId, firstName: "RemitRace", lastName: "Client",
     gender: "F", age: 30,
   }), { headers });
   requireResponse(clientRes, "setup client", (r) => r.status === 201);
   if (clientRes.json("id") !== clientId) throw new Error("Setup client returned unexpected id");

   const today = new Date(Date.now() + MANILA_OFFSET_MS).toISOString().slice(0, 10);

   const sessionId = uuid();
   const sessionRes = http.post(`${BASE_URL}/api/sessions`, JSON.stringify({
     id: sessionId, clientId, branchId,
     isWalkIn: true, finalPrice: "2000.00",
   }), { headers });
   requireResponse(sessionRes, "setup session", (r) => r.status === 201);
   if (sessionRes.json("id") !== sessionId) throw new Error("Setup session returned unexpected id");

   const requestedRemittanceId = uuid();
   const remitRes = http.post(`${BASE_URL}/api/remittances`, JSON.stringify({
     id: requestedRemittanceId,
     type: "SESSION",
     branchId: branchId,
     method: "BANK_TRANSFER",
     dateRangeStart: today,
     dateRangeEnd: today,
   }), { headers });
   requireResponse(remitRes, "setup remittance", (r) => r.status === 201);
   const remittanceId = remitRes.json("id");
   if (remittanceId !== requestedRemittanceId) throw new Error("Setup remittance returned unexpected id");
   const version = remitRes.json("version");
   if (version === undefined || version === null) throw new Error("Setup remittance did not return version");

   return { token, remittanceId, version };
}

function requireResponse(response, name, predicate) {
  if (!predicate(response)) {
    throw new Error(`${name} failed with HTTP ${response.status}: ${String(response.body).slice(0, 200)}`);
  }
}

export default function (data) {
  const tags = { group: "remittance-race" };
  const headers = authHeaders(data.token);

  const responses = http.batch([
    ["POST", `${BASE_URL}/api/remittances/${data.remittanceId}/submit`, JSON.stringify({ expectedVersion: data.version }), { headers }],
    ["POST", `${BASE_URL}/api/remittances/${data.remittanceId}/submit`, JSON.stringify({ expectedVersion: data.version }), { headers }],
  ]);
  const statuses = responses.map((response) => response.status);
  const winners = statuses.filter((status) => status === 200).length;
  const conflicts = statuses.filter((status) => status === 409).length;
  responses.forEach((response) => {
    metrics.remittanceRaceLatency.add(response.timings.duration, tags);
    metrics.errorRate.add(response.status === 200 || response.status === 409 ? 0 : 1, tags);
  });
  check(statuses, {
    "concurrent remittance has one winner": () => winners === 1,
    "concurrent remittance has one conflict": () => conflicts === 1,
  });
  sleep(0.5);
}
