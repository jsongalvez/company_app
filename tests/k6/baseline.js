import http from "k6/http";
import { check, sleep } from "k6";
import { BASE_URL, authHeaders, metrics } from "./helpers.js";

const USERNAME = __ENV.TEST_USERNAME || "";
const PASSWORD = __ENV.TEST_PASSWORD || "";

export const options = {
  thresholds: {
    branches_latency: ["p(95)<500"],
    clients_search_latency: ["p(95)<1000"],
    product_latency: ["p(95)<1000"],
    errors: ["rate<0.05"],
  },
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
  return { token: res.json("token") };
}

export default function (data) {
  const headers = authHeaders(data.token);

  const branchesRes = http.get(`${BASE_URL}/api/branches`, { headers });
  metrics.branchesLatency.add(branchesRes.timings.duration);
  metrics.errorRate.add(branchesRes.status >= 400);

  const clientsRes = http.get(`${BASE_URL}/api/clients?q=test`, { headers });
  metrics.clientsSearchLatency.add(clientsRes.timings.duration);
  metrics.errorRate.add(clientsRes.status >= 400);

  const productsRes = http.get(`${BASE_URL}/api/products`, { headers });
  metrics.productLatency.add(productsRes.timings.duration);
  metrics.errorRate.add(productsRes.status >= 400);

  sleep(1);
}
