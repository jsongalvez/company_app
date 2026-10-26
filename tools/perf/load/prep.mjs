// Sign in each seeded load user once, clock them in at their branch, give each a few clients, and
// write tokens.json for capacity.js. Login is limited to 10 per minute per IP (fixed window), so sign-ins go in bursts of 8, then wait out the window.
// Usage: node prep.mjs <baseUrl> <users> <clientsPerUser> <out.json>
import { writeFileSync } from "node:fs";
import { randomUUID } from "node:crypto";

const [base, usersArg, clientsArg, out] = process.argv.slice(2);
const users = Number(usersArg);
const clientsPer = Number(clientsArg);
const PASSWORD = process.env.LOAD_PASSWORD ?? "staff-dev-pass";
const BURST = 8;
const WINDOW_MS = 61000;
let windowStart = 0;
let inWindow = 0;
const opDate = () => new Date(Date.now() + 4 * 3600 * 1000).toISOString().slice(0, 10);

const call = async (method, path, token, body, allow409 = false) => {
    const res = await fetch(base + path, {
        method,
        headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
        body: body ? JSON.stringify(body) : undefined,
    });
    if (allow409 && res.status === 409) return null;
    if (!res.ok) throw new Error(`${method} ${path} ${res.status} ${await res.text()}`);
    return res.json();
};

const rows = [];
for (let n = 1; n <= users; n++) {
    const username = `load${String(n).padStart(3, "0")}`;
    if (inWindow >= BURST) {
        const wait = windowStart + WINDOW_MS - Date.now();
        if (wait > 0) await new Promise((r) => setTimeout(r, wait));
        inWindow = 0;
    }
    if (inWindow === 0) windowStart = Date.now();
    inWindow++;
    const { token } = await call("POST", "/auth/login", null, { username, password: PASSWORD });
    const branches = await call("GET", "/api/me/branches", token);
    const branchId = branches[0].branchId;
    const clockIn =
        (await call("POST", "/api/attendance/clock-in", token, { attendanceId: randomUUID(), branchId }, true)) ??
        (await call("GET", `/api/branches/${branchId}/daily-summary?date=${opDate()}`, token));
    const clients = [];
    for (let c = 0; c < clientsPer; c++) {
        const id = randomUUID();
        await call("POST", "/api/clients", token, { id, firstName: `L${n}`, lastName: `C${c}`, gender: "F", age: 30 + c });
        clients.push(id);
    }
    rows.push({ username, token, branchId, branchDayId: clockIn.branchDayId, clients });
    console.log(`${username} ready (${branchId.slice(0, 8)})`);
}
writeFileSync(out, JSON.stringify({ base, users: rows }));
console.log(`wrote ${out}`);
