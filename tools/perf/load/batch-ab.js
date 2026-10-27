// Server cost of one screen's 8 reads sent as one /api/batch vs as 8 parallel GETs. Local verify stack only.
//   k6 run -e TOKENS=/tmp/capacity-tokens.json -e MODE=batch|parallel -e VUS=10 -e HOLD_S=60 batch-ab.js
// No think time: the box's ceiling in screens per second, and the screen's p95/p99 at that load.
import http from "k6/http";
import { Trend, Rate, Counter } from "k6/metrics";
import { SharedArray } from "k6/data";

const data = JSON.parse(open(__ENV.TOKENS));
const users = new SharedArray("users", () => data.users);
const BASE = data.base;
const MODE = __ENV.MODE || "batch";
const screenMs = new Trend("screen_ms", true);
const screens = new Counter("screens");
const failures = new Rate("failures");

export const options = {
    scenarios: { s: { executor: "constant-vus", vus: Number(__ENV.VUS || 10), duration: `${__ENV.HOLD_S || 60}s` } },
    summaryTrendStats: ["med", "p(95)", "p(99)", "max"],
    batchPerHost: 8,
};

const day = () => new Date(Date.now() + 4 * 3600 * 1000).toISOString().slice(0, 10);

export default function () {
    const u = users[(__VU - 1) % users.length];
    const h = { headers: { authorization: `Bearer ${u.token}`, "content-type": "application/json" } };
    const b = u.branchId;
    const d = u.branchDayId;
    const paths = [
        `/api/branches/${b}/daily-summary?date=${day()}`,
        `/api/expenses?branchDayId=${d}`,
        `/api/commission-splits/${d}`,
        `/api/branch-days/${d}/users`,
        `/api/branches/${b}/sessions?date=${day()}`,
        `/api/branches/${b}/members`,
        `/api/branches/${b}/today-screen`,
        `/api/notifications`,
    ];
    const t0 = Date.now();
    let ok;
    if (MODE === "batch") {
        const r = http.post(`${BASE}/api/batch`, JSON.stringify({ paths }), h);
        ok = r.status === 200 && JSON.parse(r.body).results.every((x) => x.status < 400);
    } else {
        const rs = http.batch(paths.map((p) => ["GET", BASE + p, null, h]));
        ok = rs.every((r) => r.status < 400);
    }
    screenMs.add(Date.now() - t0);
    failures.add(!ok);
    screens.add(1);
}
