import { Trend, Rate } from "k6/metrics";

export const BASE_URL = __ENV.API_BASE_URL || "http://localhost:3023";

export function uuid() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export function authHeaders(token) {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${token}`,
  };
}

export const metrics = {
  authLatency: new Trend("auth_latency"),
  branchesLatency: new Trend("branches_latency"),
  branchLatency: new Trend("branch_latency"),
  clientsSearchLatency: new Trend("clients_search_latency"),
  clientLatency: new Trend("client_latency"),
  sessionLatency: new Trend("session_latency"),
  attendanceLatency: new Trend("attendance_latency"),
  productLatency: new Trend("product_latency"),
  inventoryLatency: new Trend("inventory_latency"),
  saleLatency: new Trend("sale_latency"),
  compensationLatency: new Trend("compensation_latency"),
  expenseLatency: new Trend("expense_latency"),
  allowanceLatency: new Trend("allowance_latency"),
  remittanceLatency: new Trend("remittance_latency"),
  notificationLatency: new Trend("notification_latency"),
  myBranchesLatency: new Trend("my_branches_latency"),
  dashboardLatency: new Trend("dashboard_latency"),
  reportLatency: new Trend("report_latency"),
  concurrencyLatency: new Trend("concurrency_latency"),
  authzLatency: new Trend("authz_latency"),
  remittanceRaceLatency: new Trend("remittance_race_latency"),
  errorRate: new Rate("errors"),
};

const fullThresholds = {
  auth_latency: ["p(95)<500"],
  branches_latency: ["p(95)<500"],
  branch_latency: ["p(95)<500"],
  clients_search_latency: ["p(95)<1000"],
  client_latency: ["p(95)<1000"],
  session_latency: ["p(95)<1000"],
  attendance_latency: ["p(95)<500"],
  product_latency: ["p(95)<500"],
  inventory_latency: ["p(95)<500"],
  sale_latency: ["p(95)<1000"],
  compensation_latency: ["p(95)<500"],
  expense_latency: ["p(95)<500"],
  allowance_latency: ["p(95)<500"],
  remittance_latency: ["p(95)<2000"],
  notification_latency: ["p(95)<500"],
  my_branches_latency: ["p(95)<200"],
  dashboard_latency: ["p(95)<200"],
  report_latency: ["p(95)<1000"],
  concurrency_latency: ["p(95)<1000"],
  authz_latency: ["p(95)<1000"],
  remittance_race_latency: ["p(95)<3000"],
  errors: ["rate<0.05"],
};

const baselineThresholds = {
  branches_latency: fullThresholds.branches_latency,
  clients_search_latency: fullThresholds.clients_search_latency,
  // Baseline covers product listing under a broader early-load budget.
  product_latency: ["p(95)<1000"],
  my_branches_latency: fullThresholds.my_branches_latency,
  dashboard_latency: fullThresholds.dashboard_latency,
  errors: fullThresholds.errors,
};

export const thresholdProfiles = {
  baseline: baselineThresholds,
  full: fullThresholds,
  concurrency: {
    concurrency_latency: fullThresholds.concurrency_latency,
    errors: ["rate<0.10"],
  },
  remittanceRace: {
    remittance_race_latency: fullThresholds.remittance_race_latency,
    errors: fullThresholds.errors,
    checks: ["rate==1"],
  },
  authz: {
    authz_latency: fullThresholds.authz_latency,
    errors: ["rate<0.10"],
  },
};
