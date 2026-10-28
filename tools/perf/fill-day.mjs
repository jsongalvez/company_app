// Fill today's business day at one branch with a realistic load, through the API, to measure how Today
// copes as the day fills up. Each session gets a new client (a client holds one waiting session); two
// in three are marked Done; one sale per three sessions, half tied to a session. Adds sessions on top of
// what the day already has, so 50 then 50 more gives 100.
// Usage: node tools/perf/fill-day.mjs --base http://127.0.0.1:18181 --sessions 50 [--user owner --pass owner-dev-pass]
// Only for a verify stack or another throwaway database: it writes real rows.
import { randomUUID } from "node:crypto";

const arg = (name, fallback) => {
    const i = process.argv.indexOf(`--${name}`);
    return i > 0 ? process.argv[i + 1] : fallback;
};
const BASE = arg("base");
const N = Number(arg("sessions", "50"));
const USER = arg("user", process.env.FILL_USER ?? "owner");
const PASS = arg("pass", process.env.FILL_PASS ?? "owner-dev-pass");
if (!BASE) throw new Error("--base is required");
if (/nip\.io|https:/.test(BASE) && !process.env.FILL_ALLOW_REMOTE) throw new Error("refusing a remote base: set FILL_ALLOW_REMOTE=1 for a throwaway server");

let token;
const call = async (method, path, body, allow = []) => {
    const res = await fetch(BASE + path, {
        method,
        headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
        body: body ? JSON.stringify(body) : undefined,
    });
    if (allow.includes(res.status)) return null;
    if (!res.ok) throw new Error(`${method} ${path} ${res.status} ${await res.text()}`);
    const text = await res.text();
    return text ? JSON.parse(text) : null;
};

({ token } = await call("POST", "/auth/login", { username: USER, password: PASS }));
const [{ branchId }] = await call("GET", "/api/me/branches");
await call("POST", "/api/attendance/clock-in", { attendanceId: randomUUID(), branchId }, [409]);
const start = await call("GET", `/api/start?branchId=${branchId}&withToday=true`);
const branchDayId = start.todayScreen?.today?.branchDayId ?? start.today?.branchDayId;
if (!branchDayId) throw new Error("no open business day in the start answer");

let inv = await call("GET", `/api/branches/${branchId}/inventory`);
let product = inv.find((i) => i.productName === "Fill oil");
if (!product) {
    const categoryId = randomUUID();
    const productId = randomUUID();
    await call("POST", "/api/product-categories", { id: categoryId, name: `Fill ${categoryId.slice(0, 6)}` });
    await call("POST", "/api/products", { id: productId, name: "Fill oil", productCategoryId: categoryId, unitPrice: "350.00", commissionAmount: "35.00" });
    await call("POST", `/api/branches/${branchId}/inventory`, { productId });
    inv = await call("GET", `/api/branches/${branchId}/inventory`);
    product = inv.find((i) => i.productName === "Fill oil");
}
if (product.currentStock < N) await call("POST", `/api/branches/${branchId}/inventory/${product.productId}/restock`, { id: randomUUID(), quantity: 5000, branchDayId });

let sales = 0;
for (let n = 1; n <= N; n++) {
    const clientId = randomUUID();
    await call("POST", "/api/clients", { id: clientId, firstName: "Fill", lastName: `C${randomUUID().slice(0, 6)}`, gender: n % 2 ? "F" : "M", age: 20 + (n % 50) });
    const s = await call("POST", "/api/sessions", { id: randomUUID(), clientId, branchId, isWalkIn: true, finalPrice: n % 4 ? "2500.00" : "2000.00" });
    if (n % 3 !== 0) await call("PATCH", `/api/sessions/${s.id}/status`, { status: "COMPLETED", version: s.version });
    if (n % 3 === 0) {
        const tied = n % 6 === 0;
        await call("POST", "/api/product-sales", { id: randomUUID(), branchDayId, sessionId: tied ? s.id : null, clientId: null, isWalkIn: !tied, productId: product.productId, quantity: 1 });
        sales++;
    }
}
const today = await call("GET", `/api/branches/${branchId}/today-screen`);
console.log(`added ${N} sessions, ${sales} sales; day now holds ${today.dashboard.sessions.length} sessions (${branchDayId})`);
