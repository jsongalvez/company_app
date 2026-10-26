// One probe run against the app: sign in as the probe user, load Today, save one session and void it, load Finance.
// Appends one JSON row per route to PROBE_LOG (client time beside the server's Server-Timing split) and
// exits 1 when any step failed. check-latency.py reads the rows; failed rows are not slowness.
//   PROBE_BASE=http://127.0.0.1:3023 PROBE_USER=probe PROBE_PASS=... node probe.mjs
import { appendFileSync, mkdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { randomUUID } from "node:crypto";
import { homedir } from "node:os";
import { dirname, join } from "node:path";

const BASE = process.env.PROBE_BASE;
const USER = process.env.PROBE_USER;
const PASS = process.env.PROBE_PASS;
if (!BASE || !USER || !PASS) {
    console.error("PROBE_BASE, PROBE_USER and PROBE_PASS are required");
    process.exit(2);
}
const LOG = process.env.PROBE_LOG ?? join(homedir(), "logs/company_app/probe.jsonl");
const CLIENT_FILE = process.env.PROBE_CLIENT_FILE ?? join(homedir(), ".local/state/company-app-perf/probe-client");
const BRANCH_NAME = process.env.PROBE_BRANCH ?? "probe";
mkdirSync(dirname(LOG), { recursive: true });
mkdirSync(dirname(CLIENT_FILE), { recursive: true });

const opDate = () => new Date(Date.now() + 4 * 3600 * 1000).toISOString().slice(0, 10);

const serverTiming = (res) => {
    const h = res.headers.get("server-timing") ?? "";
    const pick = (name) => Number(new RegExp(`${name};dur=([0-9.]+)`).exec(h)?.[1] ?? NaN);
    return { dbMs: Math.round(pick("db")), appMs: Math.round(pick("app")) };
};

const call = async (method, path, token, body) => {
    const started = performance.now();
    const res = await fetch(BASE + path, {
        method,
        headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
        body: body ? JSON.stringify(body) : undefined,
        signal: AbortSignal.timeout(30000),
    });
    const clientMs = Math.round(performance.now() - started);
    const text = await res.text();
    return { status: res.status, clientMs, trace: res.headers.get("x-trace-id") ?? "", ...serverTiming(res), json: () => JSON.parse(text), text };
};

const rows = [];
const record = (route, r) => {
    const good = r.status >= 200 && r.status < 300 && Number.isFinite(r.dbMs) && Number.isFinite(r.appMs);
    rows.push({ ts: new Date().toISOString(), route, ok: good, status: r.status, clientMs: r.clientMs, dbMs: good ? r.dbMs : 0, appMs: good ? r.appMs : 0, trace: r.trace });
    return good;
};
const flush = () => appendFileSync(LOG, rows.map((r) => JSON.stringify(r)).join("\n") + "\n");

let failed = null;
try {
    const login = await call("POST", "/auth/login", null, { username: USER, password: PASS });
    if (!record("signin", login)) throw new Error(`sign-in ${login.status} ${login.text.slice(0, 120)}`);
    const token = login.json().token;

    const branches = (await call("GET", "/api/me/branches", token)).json();
    const branch = branches.find((b) => b.branchName === BRANCH_NAME || b.name === BRANCH_NAME) ?? (branches.length === 1 ? branches[0] : null);
    if (!branch) throw new Error(`probe user has no branch named ${BRANCH_NAME}`);
    const branchId = branch.branchId;

    const clock = await call("POST", "/api/attendance/clock-in", token, { attendanceId: randomUUID(), branchId });
    if (clock.status !== 409 && clock.status >= 300) throw new Error(`clock-in ${clock.status} ${clock.text.slice(0, 120)}`);

    const today = await call("GET", `/api/branches/${branchId}/dashboard/today`, token);
    if (!record("today", today)) throw new Error(`today ${today.status}`);

    let clientId = existsSync(CLIENT_FILE) ? readFileSync(CLIENT_FILE, "utf8").trim() : "";
    if (!clientId) {
        clientId = randomUUID();
        const c = await call("POST", "/api/clients", token, { id: clientId, firstName: "Probe", lastName: "Client", gender: "F", age: 30 });
        if (c.status >= 300) throw new Error(`probe client ${c.status} ${c.text.slice(0, 120)}`);
        writeFileSync(CLIENT_FILE, clientId);
    }
    const sessionId = randomUUID();
    const save = await call("POST", "/api/sessions", token, { id: sessionId, clientId, branchId, isWalkIn: true, finalPrice: "1.00" });
    const saved = record("save", save);
    if (saved) {
        const voided = await call("POST", `/api/sessions/${sessionId}/void`, token, { id: randomUUID(), voidReason: "latency probe" });
        if (voided.status >= 300) throw new Error(`void ${voided.status} ${voided.text.slice(0, 120)}`);
    } else if (save.status === 409 || save.status === 400) {
        // A pending session from a run that died before its void would block the probe client for good.
        writeFileSync(CLIENT_FILE, "");
        throw new Error(`save ${save.status} ${save.text.slice(0, 160)}`);
    } else throw new Error(`save ${save.status}`);

    const fin = await call("GET", `/api/branches/${branchId}/daily-summary?date=${opDate()}`, token);
    if (!record("finance", fin)) throw new Error(`finance ${fin.status}`);
} catch (e) {
    failed = e;
    for (const route of ["signin", "today", "save", "finance"]) {
        if (!rows.some((r) => r.route === route)) rows.push({ ts: new Date().toISOString(), route, ok: false, status: 0, clientMs: 0, dbMs: 0, appMs: 0, trace: "" });
    }
}
flush();
for (const r of rows) console.log(`${r.route.padEnd(8)} ${r.ok ? "ok  " : "FAIL"} client ${r.clientMs}ms db ${r.dbMs}ms app ${r.appMs}ms trace ${r.trace}`);
if (failed) {
    console.error(`probe failed: ${failed.message}`);
    process.exit(1);
}
