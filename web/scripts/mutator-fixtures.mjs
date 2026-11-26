// Captures the server's answers that tests/unit/mutators.test.ts checks the local mutators against.
// Run against a local stack: tools/verify/verify.sh up, then `node scripts/mutator-fixtures.mjs` from web/.
// Each case: the request, the cached document it changes before and after (read from the server), and its answer.
import { writeFileSync } from "node:fs";

const BASE = process.env.APP_URL ?? "http://127.0.0.1:18181";
const OUT = new URL("../tests/unit/fixtures/mutators.json", import.meta.url);
const login = await (
    await fetch(`${BASE}/auth/login`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username: process.env.FIX_USER ?? "staff", password: process.env.FIX_PASS ?? "staff-dev-pass" }),
    })
).json();
const token = login.token;
const me = login.start.me.id;
let n = 0;
const opAt = () => new Date(Date.now() - 1000 * ++n).toISOString();

async function call(method, path, body) {
    const at = opAt();
    const res = await fetch(BASE + path, {
        method,
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}`, "X-Occurred-At": at, "X-Op-Id": `fix-${Date.now()}-${n}` },
        body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    if (!res.ok) throw new Error(`${method} ${path} ${res.status} ${text}`);
    return { at, answer: text ? JSON.parse(text) : null };
}
const get = async (path) => (await call("GET", path)).answer;

const cases = [];
async function step(name, key, read, method, path, body) {
    const before = await get(read);
    const { at, answer } = await call(method, path, body);
    const after = await get(read);
    cases.push({ name, key, op: { method, path, body: body ?? {}, at, user: me }, before, answer, after });
}

const branchId = login.start.branchId;
const today = (await get(`/api/branches/${branchId}/today-screen`)).date;

// Expenses. The first one names the day by date; its answer gives the day's id.
const first = (await call("POST", "/api/expenses", { id: crypto.randomUUID(), branchId, date: today, amount: "10.00", category: "WATER" })).answer;
const day = first.branchDayId;
const exp = `/api/expenses?branchDayId=${day}`;
const eid = crypto.randomUUID();
await step("expense create", `exp:${day}`, exp, "POST", "/api/expenses", {
    id: eid,
    branchDayId: day,
    amount: "125.50",
    category: "PANTRY",
    notes: "rice",
    reason: null,
});
await step("expense edit", `exp:${day}`, exp, "PATCH", `/api/expenses/${eid}`, {
    amount: "130",
    category: "WATER",
    notes: null,
    expectedVersion: cases.at(-1).answer.version,
    reason: null,
});
await step("expense delete", `exp:${day}`, exp, "DELETE", `/api/expenses/${eid}`, { reason: "typo", expectedVersion: cases.at(-1).answer.version });
await step("expense restore", `exp:${day}`, exp, "POST", `/api/expenses/${eid}/restore`, { reason: null });

// Stock, then a sale on it.
const inv = `/api/branches/${branchId}/inventory`;
if (!(await get(inv)).length) {
    const product = (await get("/api/products"))[0];
    await call("POST", `/api/branches/${branchId}/inventory/${product.id}/restock`, { id: crypto.randomUUID(), quantity: 10, branchDayId: day });
}
const item = (await get(inv))[0];
await step("stock movement", `inv:${branchId}`, inv, "POST", `/api/branches/${branchId}/inventory/${item.productId}/movement`, {
    movementId: crypto.randomUUID(),
    reason: "ADJUSTMENT",
    quantityChange: 5,
    notes: null,
    branchDayId: day,
    expectedVersion: item.version,
});
const sales = `/api/product-sales?branchDayId=${day}`;
const sale = (await call("POST", "/api/product-sales", { id: crypto.randomUUID(), branchDayId: day, isWalkIn: true, productId: item.productId, quantity: 1 }))
    .answer;
await step("sale edit", `sales:${day}`, sales, "PATCH", `/api/product-sales/${sale.id}`, {
    productId: item.productId,
    quantity: 3,
    expectedVersion: sale.version,
    reason: null,
});
await step("sale delete", `sales:${day}`, sales, "DELETE", `/api/product-sales/${sale.id}`, { reason: "wrong", expectedVersion: cases.at(-1).answer.version });
await step("sale restore", `sales:${day}`, sales, "POST", `/api/product-sales/${sale.id}/restore`, { reason: null });

// A client's details.
const client = (
    await call("POST", "/api/clients", {
        id: crypto.randomUUID(),
        firstName: "Fixture",
        lastName: `Client${Date.now() % 100000}`,
        gender: "F",
        age: 30,
        medicalConditions: "asthma",
    })
).answer;
await step("client edit", `client:${client.id}`, `/api/clients/${client.id}`, "PATCH", `/api/clients/${client.id}`, {
    phoneNumber: `0917${String(Date.now()).slice(-7)}`,
    age: 20 + (Date.now() % 50),
    address: `Lot ${Date.now() % 100}`,
    clearFields: ["medicalConditions"],
});

// A session on today's list: price, void, restore.
const dsess = `/api/branches/${branchId}/sessions?date=${today}`;
await call("POST", "/api/attendance/clock-in", { attendanceId: crypto.randomUUID(), branchId }).catch((e) => console.error(String(e)));
const sid = crypto.randomUUID();
await call("POST", "/api/sessions", { id: sid, clientId: client.id, branchId, isWalkIn: true, finalPrice: "500.00", otherConcerns: "neck" });
const s = (await get(dsess)).find((x) => x.id === sid);
if (s) {
    await step("session price", `dsess:${branchId}:${today}`, dsess, "PATCH", `/api/sessions/${s.id}/final-price`, {
        finalPrice: "777.00",
        version: s.version,
        reason: null,
    });
    // A practitioner and an illness on it, then their changes.
    await call("POST", `/api/sessions/${s.id}/practitioners`, { id: crypto.randomUUID(), practitionerId: me, reason: null });
    await step("practitioner remarks", `dsess:${branchId}:${today}`, dsess, "PATCH", `/api/sessions/${s.id}/practitioners/${me}`, {
        remarks: "left shoulder",
        reason: null,
    });
    await step("practitioner remove", `dsess:${branchId}:${today}`, dsess, "DELETE", `/api/sessions/${s.id}/practitioners/${me}`, { reason: null });
    const concernId = crypto.randomUUID();
    await step("illness by name", `dsess:${branchId}:${today}`, dsess, "POST", `/api/sessions/${s.id}/promote-concern`, {
        id: concernId,
        label: ` Fixture ache ${Date.now() % 100000} `,
        reason: null,
    });
    await step("illness remove", `dsess:${branchId}:${today}`, dsess, "DELETE", `/api/sessions/${s.id}/concerns/${concernId}`, { reason: null });
    await step("session void", `dsess:${branchId}:${today}`, dsess, "POST", `/api/sessions/${s.id}/void`, { id: crypto.randomUUID(), voidReason: "test" });
    await step("session unvoid", `dsess:${branchId}:${today}`, dsess, "POST", `/api/sessions/${s.id}/unvoid`, { unvoidedReason: "test" });
} else console.error("no session on today's list: session cases skipped");

// A booking, then its change.
const booking = (
    await call("POST", `/api/branches/${branchId}/bookings`, { id: crypto.randomUUID(), clientId: client.id, visitDate: today, visitTime: null, note: null })
).answer;
await step("booking edit", `bookings:${branchId}`, `/api/branches/${branchId}/today-screen`, "PATCH", `/api/branches/${branchId}/bookings/${booking.id}`, {
    visitDate: today,
    visitTime: "15:30",
    note: "call first",
    status: "CAME",
});
const last = cases.at(-1);
last.before = last.before.bookings;
last.after = last.after.bookings;

// Lists keep the case's record and one other, so a mutator is seen to leave the rest alone.
const target = (c) => c.op.path.split("/")[c.key.startsWith("inv:") || c.key.startsWith("bookings:") ? 5 : 3];
for (const c of cases) {
    if (!Array.isArray(c.before)) continue;
    const id = c.key.startsWith("inv:") ? "productId" : "id";
    const t = c.op.method === "POST" && !c.op.path.split("/")[3] ? c.op.body.id : target(c);
    const other = c.before.find((r) => r[id] !== t)?.[id];
    const keep = (r) => r[id] === t || r[id] === other;
    c.before = c.before.filter(keep);
    c.after = c.after.filter(keep);
}
writeFileSync(OUT, `[\n${cases.map((c) => JSON.stringify(c)).join(",\n")}\n]\n`);
console.log(`${cases.length} cases -> ${OUT.pathname}`);
