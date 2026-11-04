// Clocks the user out and back in through the API the way the app does (?withToday=true), --runs times,
// and prints the server's own time per route (Server-Timing db+app ms; median, min, max) so the network
// is out of the number. The backend log then holds these requests for `sql-trace.py`.
//   node tools/perf/clock-flow.mjs --base http://127.0.0.1:18181 --user owner --pass owner-dev-pass [--runs 5]
import { randomUUID } from "node:crypto";

const arg = (name, dflt) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : dflt;
};
const BASE = arg("base", "http://127.0.0.1:18181");
const USER = arg("user", process.env.CALLS_USER ?? "owner");
const PASS = arg("pass", process.env.CALLS_PASS ?? "owner-dev-pass");
const RUNS = Number(arg("runs", "1"));

const times = {};
const call = async (name, method, path, token, body) => {
    const r = await fetch(`${BASE}${path}`, {
        method,
        headers: { "content-type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: body ? JSON.stringify(body) : undefined,
    });
    const text = await r.text();
    if (!r.ok) throw new Error(`${method} ${path} ${r.status} ${text.slice(0, 200)}`);
    const t = Object.fromEntries(
        [...(r.headers.get("server-timing") ?? "").matchAll(/(\w+);dur=([\d.]+)/g)].map((m) => [m[1], Number(m[2])]),
    );
    if (t.db !== undefined) (times[name] ??= []).push(t.db + (t.app ?? 0));
    return text ? JSON.parse(text) : null;
};

const { token } = await call("sign-in", "POST", "/auth/login", null, { username: USER, password: PASS });
const start = await call("start", "GET", "/api/start", token);
const branchId = start.branchId;
const open = (await call("active", "GET", "/api/me/active-attendance", token))?.shift;
if (open?.attendanceId)
    await call("clock-out", "POST", "/api/attendance/clock-out?withToday=true", token, {
        attendanceId: open.attendanceId,
    });
for (let i = 0; i < RUNS; i++) {
    const id = randomUUID();
    await call("clock-in", "POST", "/api/attendance/clock-in?withToday=true", token, { attendanceId: id, branchId });
    await call("start", "GET", "/api/start?withToday=true", token);
    await call("clock-out", "POST", "/api/attendance/clock-out?withToday=true", token, { attendanceId: id });
}
if (open?.attendanceId)
    await call("clock-in", "POST", "/api/attendance/clock-in?withToday=true", token, { attendanceId: randomUUID(), branchId });

const med = (xs) => [...xs].sort((a, b) => a - b)[Math.floor(xs.length / 2)];
console.log(`clock-flow ${BASE} runs=${RUNS} (server ms: median / min / max, n)`);
for (const [name, xs] of Object.entries(times))
    console.log(`${name.padEnd(10)} ${med(xs)} / ${Math.min(...xs)} / ${Math.max(...xs)}  n=${xs.length}`);
