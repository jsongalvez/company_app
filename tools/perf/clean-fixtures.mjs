// Find the clients that perf runs left on a server and prove each one is a fixture before anything changes.
// A fixture: first name starts with "Probe", last name "Client", and every session it has is on the probe
// branch (only the probe user can reach that branch, see seed-probe.sql). The client the live probe uses
// now (its client file) is never touched. Prints the proof; with --apply it erases each fixture through
// the app's own audited command (POST /api/clients/{id}/anonymize), which also drops it from every phone's
// client list.
// Usage: PROBE_USER=probe PROBE_PASS=... node tools/perf/clean-fixtures.mjs --base URL [--apply]
import { existsSync, readFileSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

const arg = (name) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : undefined;
};
const BASE = arg("base");
const APPLY = process.argv.includes("--apply");
const PROBE_BRANCH = process.env.PROBE_BRANCH ?? "probe";
const CLIENT_FILE = process.env.PROBE_CLIENT_FILE ?? join(homedir(), ".local/state/company-app-perf/probe-client");
if (!BASE || !process.env.PROBE_USER || !process.env.PROBE_PASS) throw new Error("--base, PROBE_USER and PROBE_PASS are required");

const login = await fetch(`${BASE}/auth/login`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ username: process.env.PROBE_USER, password: process.env.PROBE_PASS }),
});
if (!login.ok) throw new Error(`sign-in ${login.status}`);
const { token } = await login.json();
const get = async (path) => {
    const r = await fetch(BASE + path, { headers: { authorization: `Bearer ${token}` } });
    if (!r.ok) throw new Error(`GET ${path} ${r.status}`);
    return r.json();
};

const active = existsSync(CLIENT_FILE) ? readFileSync(CLIENT_FILE, "utf8").trim() : "";
const named = (await get("/api/client-directory")).filter((c) => (c.firstName ?? "").startsWith("Probe") && c.lastName === "Client");
const proven = [];
for (const c of named) {
    const { sessions, sales } = await get(`/api/clients/${c.id}/history`);
    const elsewhere = sessions.filter((s) => s.branchName !== PROBE_BRANCH).length;
    const why =
        c.id === active ? "kept: the live probe's client" : elsewhere ? `kept: ${elsewhere} sessions off the probe branch` : sales.length ? "kept: has product sales" : "fixture";
    console.log(`${c.id}  ${c.firstName} ${c.lastName}  sessions ${sessions.length} (all on ${PROBE_BRANCH}: ${!elsewhere})  ${why}`);
    if (why === "fixture") proven.push(c);
}
console.log(`${proven.length} proven fixtures${APPLY ? "" : " (dry run: pass --apply to erase them)"}`);
if (APPLY)
    for (const c of proven) {
        const r = await fetch(`${BASE}/api/clients/${c.id}/anonymize`, { method: "POST", headers: { authorization: `Bearer ${token}` } });
        console.log(`${c.id} erased: ${r.status}`);
    }
