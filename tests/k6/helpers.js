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
  branchLatency: new Trend("branch_latency"),
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
  reportLatency: new Trend("report_latency"),
  concurrencyLatency: new Trend("concurrency_latency"),
  errorRate: new Rate("errors"),
};

export const thresholds = {
  auth_latency: ["p(95)<500"],
  branch_latency: ["p(95)<500"],
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
  report_latency: ["p(95)<1000"],
  concurrency_latency: ["p(95)<1000"],
  errors: ["rate<0.05"],
};
