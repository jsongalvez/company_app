// Many phones at once: C workers each loop over the hot read routes for D seconds, as signed-in staff, and the
// client's wall time per request gives p50/p95 per route and the requests per second the app sustains.
//   node tools/loadtest/conc.mjs --base http://127.0.0.1:18402 --c 1,5,20 --seconds 20 [--out results/conc.jsonl]
// Wall time includes queueing in the app's pool, which is the point; Server-Timing is kept beside it.
import { appendFileSync } from "node:fs";
import { loadavg } from "node:os";

const arg = (name, dflt) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : dflt;
};
const BASE = arg("base", "http://127.0.0.1:18401");
const LEVELS = arg("c", "1,5,20").split(",").map(Number);
const SECONDS = Number(arg("seconds", "20"));
const OUT = arg("out", "");
const LABEL = arg("label", "");

const call = async (path, token, body) => {
    const t0 = performance.now();
    const r = await fetch(`${BASE}${path}`, {
        method: body ? "POST" : "GET",
        headers: { "content-type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: body ? JSON.stringify(body) : undefined,
    });
    const text = await r.text();
    const wall = performance.now() - t0;
    const st = Object.fromEntries([...(r.headers.get("server-timing") ?? "").matchAll(/(\w+);dur=([\d.]+)/g)].map((m) => [m[1], Number(m[2])]));
    return { status: r.status, wall, server: (st.db ?? 0) + (st.app ?? 0), json: () => JSON.parse(text) };
};

const staff = (await call("/auth/login", null, { username: "staff", password: "staff-dev-pass" })).json().token;
const b = (await call("/api/start", staff)).json().branchId;
const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Manila" }).format(new Date(Date.now() - 4 * 3600_000));
const routes = [
    ["GET /api/start", "/api/start"],
    ["GET today-screen", `/api/branches/${b}/today-screen`],
    ["GET sessions (day)", `/api/branches/${b}/sessions?date=${today}`],
    ["GET recent-clients", "/api/recent-clients"],
    ["GET clients search", "/api/clients?q=maria%20santos"],
];

const pct = (xs, p) => {
    const s = [...xs].sort((a, b) => a - b);
    return s.length ? Math.round(s[Math.min(s.length - 1, Math.floor(p * s.length))] * 10) / 10 : null;
};

for (const c of LEVELS) {
    const by = new Map(routes.map(([n]) => [n, { wall: [], server: [], errors: 0 }]));
    const end = performance.now() + SECONDS * 1000;
    const worker = async (w) => {
        for (let i = w; performance.now() < end; i++) {
            const [name, path] = routes[i % routes.length];
            const r = await call(path, staff);
            const g = by.get(name);
            if (r.status >= 400) g.errors++;
            else {
                g.wall.push(r.wall);
                g.server.push(r.server);
            }
        }
    };
    const load0 = loadavg()[0];
    await Promise.all(Array.from({ length: c }, (_, w) => worker(w)));
    let total = 0;
    for (const [route, g] of by) {
        total += g.wall.length;
        const row = { label: LABEL, c, route, n: g.wall.length, errors: g.errors, p50: pct(g.wall, 0.5), p95: pct(g.wall, 0.95), server_p50: pct(g.server, 0.5), server_p95: pct(g.server, 0.95), load1: load0 };
        console.log(`c=${String(c).padEnd(3)} ${route.padEnd(20)} n=${String(row.n).padEnd(5)} err=${row.errors} wall p50 ${row.p50} p95 ${row.p95}  server p50 ${row.server_p50} p95 ${row.server_p95}`);
        if (OUT) appendFileSync(OUT, JSON.stringify(row) + "\n");
    }
    console.log(`c=${c}: ${(total / SECONDS).toFixed(1)} req/s, load ${load0.toFixed(2)} at start`);
}
