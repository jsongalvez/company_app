// Drives the four key routes with fixed data against one backend. The gate reads timings and query
// counts from the backend's own request log, so this script only has to make the requests and fail
// on errors. Env: BASE (url), SIGNINS (default 7, login is limited to 10 per minute per IP),
// TODAY (default 40), SAVES (default 10), FINANCE (default 30).
import http from "k6/http";
import { check, fail } from "k6";

const BASE = __ENV.BASE;
const BRANCH = "00000000-0000-4000-8000-000000000001";
const SIGNINS = Number(__ENV.SIGNINS || 7);
const TODAY = Number(__ENV.TODAY || 40);
const SAVES = Number(__ENV.SAVES || 10);
const FINANCE = Number(__ENV.FINANCE || 30);

export const options = {
    vus: 1,
    iterations: 1,
    thresholds: { checks: ["rate==1"] },
};

const uuid = () =>
    "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
        const r = Math.floor(Math.random() * 16);
        return (c === "x" ? r : (r & 0x3) | 0x8).toString(16);
    });
const ok = (r) => r.status >= 200 && r.status < 300;
const opDate = () => new Date(Date.now() + 4 * 3600 * 1000).toISOString().slice(0, 10);
const json = { "content-type": "application/json" };

function login() {
    const r = http.post(`${BASE}/auth/login`, JSON.stringify({ username: "staff", password: "staff-dev-pass" }), { headers: json });
    check(r, { "sign-in ok": ok });
    return r;
}

export function setup() {
    const r = login();
    if (!ok(r)) fail(`setup sign-in ${r.status} ${r.body}`);
    const token = r.json("token");
    const p = { headers: { ...json, authorization: `Bearer ${token}` } };
    const clock = http.post(`${BASE}/api/attendance/clock-in`, JSON.stringify({ attendanceId: uuid(), branchId: BRANCH }), p);
    if (!ok(clock) && clock.status !== 409) fail(`clock-in ${clock.status} ${clock.body}`);
    const day = ok(clock) ? clock : http.get(`${BASE}/api/branches/${BRANCH}/daily-summary?date=${opDate()}`, p);
    return { token, branchDayId: day.json("branchDayId") };
}

export default function (data) {
    const p = { headers: { ...json, authorization: `Bearer ${data.token}` } };
    for (let i = 0; i < SIGNINS - 1; i++) login();
    for (let i = 0; i < TODAY; i++) {
        const rs = http.batch([["GET", `${BASE}/api/branches/${BRANCH}/today-screen`, null, p]]);
        check(rs, { "today ok": (r) => ok(r[0]) });
    }
    for (let i = 0; i < SAVES; i++) {
        const clientId = uuid();
        const c = http.post(`${BASE}/api/clients`, JSON.stringify({ id: clientId, firstName: "Gate", lastName: "Client", gender: "F", age: 35 }), p);
        check(c, { "client ok": ok });
        const s = http.post(`${BASE}/api/sessions`, JSON.stringify({ id: uuid(), clientId, branchId: BRANCH, isWalkIn: true, finalPrice: "2500.00" }), p);
        check(s, { "save session ok": ok });
    }
    for (let i = 0; i < FINANCE; i++) {
        const rs = http.batch([
            ["GET", `${BASE}/api/branches/${BRANCH}/daily-summary?date=${opDate()}`, null, p],
            ["GET", `${BASE}/api/expenses?branchDayId=${data.branchDayId}`, null, p],
        ]);
        check(rs[0], { "finance ok": ok });
    }
}
