// Server time of the hot routes on a seeded load-test database, from the app's own Server-Timing (db + app ms),
// so the network and the client are out of the number. Writes one JSON line per request to --out.
//   node tools/loadtest/bench.mjs --base http://127.0.0.1:18401 --scale 10 --n 30 --out results/raw.jsonl [--label before] [--writes 0] [--round 1]
// Reads run as `owner` (sees every branch) and `staff` (member of the fixture branch); writes as `staff`.
// Sign-in is limited to 10 a minute per address, so it is timed apart, --login-n times, 7s apart.
import { randomUUID } from "node:crypto";
import { appendFileSync } from "node:fs";
import { loadavg } from "node:os";

const arg = (name, dflt) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : dflt;
};
const BASE = arg("base", "http://127.0.0.1:18401");
const N = Number(arg("n", "30"));
const LOGIN_N = Number(arg("login-n", "0"));
const SCALE = arg("scale", "?");
const LABEL = arg("label", "before");
const OUT = arg("out", "/dev/stdout");
const ONLY = arg("only", "");
const WRITES = arg("writes", "1") !== "0";
const ROUND = arg("round", "");

const timing = (h) => {
    const t = Object.fromEntries([...(h.get("server-timing") ?? "").matchAll(/(\w+);dur=([\d.]+)/g)].map((m) => [m[1], Number(m[2])]));
    return { db: t.db ?? null, app: t.app ?? null };
};
const call = async (method, path, token, body) => {
    const t0 = performance.now();
    const r = await fetch(`${BASE}${path}`, {
        method,
        headers: { "content-type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: body ? JSON.stringify(body) : undefined,
    });
    const text = await r.text();
    const wall = performance.now() - t0;
    if (!r.ok) throw new Error(`${method} ${path} ${r.status} ${text.slice(0, 300)}`);
    const isJson = (r.headers.get("content-type") ?? "").includes("json");
    return { json: text && isJson ? JSON.parse(text) : null, wall, ...timing(r.headers) };
};
const record = (route, res) =>
    appendFileSync(OUT, JSON.stringify({ label: LABEL, scale: SCALE, round: ROUND, route, db: res.db, app: res.app, wall: Math.round(res.wall * 10) / 10, load1: loadavg()[0] }) + "\n");

const signIn = async (username, password) => (await call("POST", "/auth/login", null, { username, password })).json.token;
const owner = await signIn("owner", "owner-dev-pass");
const staff = await signIn("staff", "staff-dev-pass");

const start = (await call("GET", "/api/start", staff)).json;
const b = start.branchId;
const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Manila" }).format(new Date(Date.now() - 4 * 3600_000));
const monthAgo = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Manila" }).format(new Date(Date.now() - 34 * 86400_000));
const remittances = (await call("GET", `/api/remittances?branchId=${b}`, owner)).json;
const remittanceId = (remittances.items ?? remittances)[0]?.id;
const sessions = (await call("GET", `/api/branches/${b}/sessions?date=${today}`, staff)).json;
const [yy, mm] = today.split("-").map(Number);
const firstSession = (sessions.items ?? sessions.sessions ?? sessions)[0];
// A day with no sessions yet (a seed older than today) falls back to the newest client, so history is still timed.
const clientId = firstSession?.clientId ?? firstSession?.client?.id ?? (await call("GET", "/api/recent-clients", staff)).json?.[0]?.id;
const dayId = start.today?.branchDayId ?? firstSession?.branchDayId;

const reads = [
    ["GET /api/start", staff, "/api/start"],
    ["GET today-screen", staff, `/api/branches/${b}/today-screen`],
    ["GET sessions (day)", staff, `/api/branches/${b}/sessions?date=${today}`],
    ["GET daily-summary", staff, `/api/branches/${b}/daily-summary?date=${today}`],
    ["GET product-sales (day)", staff, `/api/product-sales?branchDayId=${dayId}`],
    ["GET expenses (day)", staff, `/api/expenses?branchDayId=${dayId}`],
    ["GET members", staff, `/api/branches/${b}/members`],
    ["GET me/history", staff, `/api/me/history?from=${monthAgo}&to=${today}`],
    ["GET notifications/history", staff, "/api/notifications/history"],
    ["GET remittance-days", owner, `/api/branches/${b}/remittance-days?from=${monthAgo}&to=${today}`],
    ["GET remittances (branch)", owner, `/api/remittances?branchId=${b}`],
    ["GET remittance detail", owner, `/api/remittances/${remittanceId}`],
    ["GET monthly-summary", owner, `/api/branches/${b}/monthly-summary?year=${yy}&month=${mm}`],
    ["GET export/report (30d)", owner, `/api/branches/${b}/export/report?from=${monthAgo}&to=${today}&format=xlsx`],
    ["GET audit-log/entries", owner, "/api/audit-log/entries"],
    ["GET audit-log (record)", owner, `/api/audit-log?tableName=session&recordId=${firstSession?.id}`],
    ["GET audit-log/flagged", owner, "/api/audit-log/flagged"],
    ["GET client-directory", owner, "/api/client-directory"],
    ["GET clients search", staff, "/api/clients?q=maria%20santos"],
    ["GET recent-clients", staff, "/api/recent-clients"],
    ["GET client history", staff, `/api/clients/${clientId}/history`],
].filter(([name]) => !ONLY || name.includes(ONLY));

for (const [name, token, path] of reads) {
    try {
        for (let i = 0; i < 3; i++) await call("GET", path, token);
        for (let i = 0; i < N; i++) record(name, await call("GET", path, token));
    } catch (e) {
        console.error(`skip ${name}: ${e.message}`);
    }
}

// Writes: clock in and out, save a walk-in session (as the phone does, with withToday).
if (WRITES && (!ONLY || "writes".includes(ONLY))) {
    const open = (await call("GET", "/api/me/active-attendance", staff)).json?.shift;
    if (open?.attendanceId) await call("POST", "/api/attendance/clock-out", staff, { attendanceId: open.attendanceId });
    for (let i = 0; i < N; i++) {
        const id = randomUUID();
        record("POST clock-in", await call("POST", "/api/attendance/clock-in?withToday=true", staff, { attendanceId: id, branchId: b }));
        const c = randomUUID();
        await call("POST", "/api/clients", staff, { id: c, firstName: "Bench", lastName: `C${c.slice(0, 6)}`, gender: "F", age: 30 });
        record("POST session (save)", await call("POST", "/api/sessions?withToday=true", staff, { id: randomUUID(), clientId: c, branchId: b, isWalkIn: true, finalPrice: "500.00" }));
        record("POST clock-out", await call("POST", "/api/attendance/clock-out?withToday=true", staff, { attendanceId: id }));
    }
}

for (let i = 0; i < LOGIN_N; i++) {
    await new Promise((r) => setTimeout(r, 7000));
    record("POST /auth/login", await call("POST", "/auth/login", null, { username: "staff", password: "staff-dev-pass" }));
}
console.error(`bench done: scale ${SCALE} ${LABEL} n=${N} branch ${b} day ${dayId} remittances ${(remittances.items ?? remittances).length ?? "?"}`);
