import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

const API_BASE_URL = __ENV.API_BASE_URL || "http://localhost:3023";
const USERNAME = __ENV.TEST_USERNAME || "";
const PASSWORD = __ENV.TEST_PASSWORD || "";

const branchesLatency = new Trend("branches_latency");
const clientsSearchLatency = new Trend("clients_search_latency");
const sessionsLatency = new Trend("sessions_latency");
const errorRate = new Rate("errors");

export const options = {
  thresholds: {
    branches_latency: ["p(95)<500"],
    clients_search_latency: ["p(95)<1000"],
    sessions_latency: ["p(95)<1000"],
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
  const res = http.post(`${API_BASE_URL}/auth/login`, JSON.stringify({
    username: USERNAME,
    password: PASSWORD,
  }), { headers: { "Content-Type": "application/json" } });

  check(res, { "login success": (r) => r.status === 200 });
  return { token: res.json("token") };
}

export default function (data) {
  const headers = {
    "Content-Type": "application/json",
    "Authorization": `Bearer ${data.token}`,
  };

  const branchesRes = http.get(`${API_BASE_URL}/branches`, { headers });
  branchesLatency.add(branchesRes.timings.duration);
  errorRate.add(branchesRes.status >= 400);

  const clientsRes = http.get(`${API_BASE_URL}/clients?q=test`, { headers });
  clientsSearchLatency.add(clientsRes.timings.duration);
  errorRate.add(clientsRes.status >= 400);

  const productsRes = http.get(`${API_BASE_URL}/products`, { headers });
  sessionsLatency.add(productsRes.timings.duration);
  errorRate.add(productsRes.status >= 400);

  sleep(1);
}
