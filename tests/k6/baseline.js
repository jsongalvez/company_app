import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, authHeaders, metrics, thresholdProfiles, uuid } from "./helpers.js";

const USERNAME = __ENV.TEST_USERNAME || "";
const PASSWORD = __ENV.TEST_PASSWORD || "";

export const options = {
  thresholds: thresholdProfiles.baseline,
  stages: [
    { duration: "10s", target: 5 },
    { duration: "20s", target: 5 },
    { duration: "10s", target: 0 },
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

  check(res, { "login success": (r) => r.status === 200 });
  const headers = authHeaders(res.json("token"));

  // #147 — the dashboard endpoint requires an active clock-in at a branch; the seeded dev
  // user has no branches, so create one (owner has the GLOBAL create gate) and clock in.
  // /api/me/branches is empty pre-clock-in for an unassigned owner — the created branch is
  // the clock-in target directly.
  let branches = http.get(`${BASE_URL}/api/branches`, { headers }).json();
  let branch = branches.find((b) => b.branchType === "CLINIC");
  if (!branch) {
    const created = http.post(`${BASE_URL}/api/branches`, JSON.stringify({
      id: uuid(),
      name: `K6 Branch ${Date.now()}`,
      branchType: "CLINIC",
    }), { headers });
    check(created, { "branch create success": (r) => r.status === 200 || r.status === 201 });
    branch = created.json();
  }

  // BranchResponse carries `id` (MeBranchResponse uses branchId — different DTOs).
  const clockIn = http.post(`${BASE_URL}/api/attendance/clock-in`, JSON.stringify({
    attendanceId: uuid(),
    branchId: branch.id,
  }), { headers });
  // 409 = already clocked in from an earlier run against the same test DB — fine, the
  // existing active clock-in satisfies the dashboard gate.
  check(clockIn, { "clock-in success": (r) => r.status === 200 || r.status === 201 || r.status === 409 });

  return { token: res.json("token"), branchId: branch.id };
}

export default function (data) {
  const headers = authHeaders(data.token);

  const branchesRes = http.get(`${BASE_URL}/api/branches`, { headers });
  metrics.branchesLatency.add(branchesRes.timings.duration);
  metrics.errorRate.add(branchesRes.status >= 400);

  // #94-grad — the BranchSelect data source (GET /api/me/branches, #98) gained its first
  // frontend consumer; baseline deferred by #98 until it saw real traffic.
  const myBranchesRes = http.get(`${BASE_URL}/api/me/branches`, { headers });
  metrics.myBranchesLatency.add(myBranchesRes.timings.duration);
  metrics.errorRate.add(myBranchesRes.status >= 400);

  // #147 — the session dashboard's 30s poll endpoint (attendance-gated).
  const dashboardRes = http.get(`${BASE_URL}/api/branches/${data.branchId}/dashboard/today`, { headers });
  metrics.dashboardLatency.add(dashboardRes.timings.duration);
  metrics.errorRate.add(dashboardRes.status >= 400);

  const clientsRes = http.get(`${BASE_URL}/api/clients?q=test`, { headers });
  metrics.clientsSearchLatency.add(clientsRes.timings.duration);
  metrics.errorRate.add(clientsRes.status >= 400);

  const productsRes = http.get(`${BASE_URL}/api/products`, { headers });
  metrics.productLatency.add(productsRes.timings.duration);
  metrics.errorRate.add(productsRes.status >= 400);

  sleep(1);
}
