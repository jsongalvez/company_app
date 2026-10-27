// Capacity test for a local verify stack, never prod. Staff users loop: load Today (dashboard, bookings,
// roster), sometimes save a session, sometimes open Finance (daily summary, expenses). One extra
// sign-in probe runs alongside. Tokens come from prep.mjs because login is limited to 10 per minute per IP.
//   k6 run -e TOKENS=/tmp/tokens.json -e VUS=100 -e HOLD_S=300 -e THINK_MIN=4 -e THINK_MAX=8 capacity.js
// THINK_MIN=THINK_MAX=0 gives the no-think-time ceiling.
import http from "k6/http";
import { check, sleep } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";
import { SharedArray } from "k6/data";

const uuidv4 = () =>
    "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
        const r = Math.floor(Math.random() * 16);
        return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
    });

const data = JSON.parse(open(__ENV.TOKENS));
const users = new SharedArray("users", () => data.users);
const BASE = data.base;
const VUS = Number(__ENV.VUS || 20);
const HOLD_S = Number(__ENV.HOLD_S || 300);
const RAMP_S = Number(__ENV.RAMP_S || 30);
const THINK_MIN = Number(__ENV.THINK_MIN ?? 4);
const THINK_MAX = Number(__ENV.THINK_MAX ?? 8);
const SAVE_P = Number(__ENV.SAVE_P ?? 0.05);
const FINANCE_P = Number(__ENV.FINANCE_P ?? 0.25);
const PROBE = __ENV.PROBE !== "0";

const todayMs = new Trend("today_view_ms", true);
const saveMs = new Trend("save_session_ms", true);
const financeMs = new Trend("finance_view_ms", true);
const signInMs = new Trend("sign_in_ms", true);
const failures = new Rate("failures");
const rateLimited = new Counter("status_429");
const sessionsSaved = new Counter("sessions_saved");

export const options = {
    scenarios: {
        staff: { executor: "ramping-vus", stages: [{ duration: `${RAMP_S}s`, target: VUS }, { duration: `${HOLD_S}s`, target: VUS }], gracefulRampDown: "5s", exec: "staff" },
        ...(PROBE ? { signin: { executor: "constant-arrival-rate", rate: 1, timeUnit: "7s", duration: `${RAMP_S + HOLD_S}s`, preAllocatedVUs: 1, exec: "signIn" } } : {}),
    },
    thresholds: {
        failures: ["rate<0.01"],
        today_view_ms: ["p(95)<300"],
        save_session_ms: ["p(95)<400"],
        finance_view_ms: ["p(95)<800"],
        sign_in_ms: ["p(95)<2500"],
    },
    summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "p(99)", "max"],
};

const authed = (token) => ({ headers: { authorization: `Bearer ${token}`, "content-type": "application/json" } });
const ok = (r) => r.status >= 200 && r.status < 300;
let logged = 0;
const record = (r) => {
    if (r.status === 429) rateLimited.add(1);
    failures.add(!ok(r));
    if (!ok(r) && logged++ < 5) console.error(`FAIL ${r.status} ${r.request.method} ${r.url} ${String(r.body).slice(0, 160)}`);
};

function opDate() {
    return new Date(Date.now() + 4 * 3600 * 1000).toISOString().slice(0, 10);
}

export function staff() {
    const u = users[(__VU - 1) % users.length];
    const p = authed(u.token);
    const b = u.branchId;

    const today = http.get(`${BASE}/api/branches/${b}/today-screen`, p);
    record(today);
    todayMs.add(today.timings.duration);

    if (Math.random() < SAVE_P) {
        // A client can hold one PENDING session, so each save starts with a new client.
        const clientId = uuidv4();
        const c = http.post(`${BASE}/api/clients`, JSON.stringify({ id: clientId, firstName: "Load", lastName: "Client", gender: "F", age: 35 }), p);
        record(c);
        const body = JSON.stringify({ id: uuidv4(), clientId, branchId: b, isWalkIn: true, finalPrice: "2500.00" });
        const r = http.post(`${BASE}/api/sessions`, body, p);
        record(r);
        saveMs.add(r.timings.duration);
        if (ok(r)) sessionsSaved.add(1);
    }

    if (Math.random() < FINANCE_P) {
        const fin = http.batch([
            ["GET", `${BASE}/api/branches/${b}/daily-summary?date=${opDate()}`, null, p],
            ["GET", `${BASE}/api/expenses?branchDayId=${u.branchDayId}`, null, p],
        ]);
        fin.forEach(record);
        financeMs.add(Math.max(...fin.map((r) => r.timings.duration)));
    }

    if (THINK_MAX > 0) sleep(THINK_MIN + Math.random() * (THINK_MAX - THINK_MIN));
}

export function signIn() {
    const r = http.post(`${BASE}/auth/login`, JSON.stringify({ username: "load001", password: "staff-dev-pass" }), { headers: { "content-type": "application/json" } });
    record(r);
    signInMs.add(r.timings.duration);
    check(r, { "sign-in ok": ok });
}
