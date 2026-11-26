import assert from "node:assert/strict";
import { test } from "node:test";
import { applyOps, type PendingOp } from "../../src/lib/mutators";
import { discarded, heldBehindRefused, landed, type Queued } from "../../src/lib/outbox-versions";

/**
 * Guards the offline promise that one person's own saves, made in any sensible order with no signal,
 * all land when the signal is back, while a change someone else made in between is still refused.
 * Each save is made the way its page makes it: from the record as the page shows it (the server's
 * last answer with the person's waiting saves applied by lib/mutators.ts). `Server` keeps each
 * record's version the way the backend does: which saves check it, and which move it on.
 */
type Item = Queued & { at: string; user: string };
type Doc = Record<string, unknown>;

const RULES: [string, RegExp, "version" | "expectedVersion" | null, (m: RegExpMatchArray) => string, boolean][] = [
    // method, path, field the server checks (null: none), the record, does the answer carry the record
    ["PATCH", /^\/api\/sessions\/([^/]+)\/status$/, "version", (m) => `s:${m[1]}`, true],
    ["PATCH", /^\/api\/sessions\/([^/]+)\/final-price$/, "version", (m) => `s:${m[1]}`, true],
    // SessionPractitionerService: the session moves on; the version is checked when the save names one.
    ["POST", /^\/api\/sessions\/([^/]+)\/practitioners$/, "version", (m) => `s:${m[1]}`, false],
    ["PATCH", /^\/api\/sessions\/([^/]+)\/practitioners\/[^/]+$/, "version", (m) => `s:${m[1]}`, false],
    ["DELETE", /^\/api\/sessions\/([^/]+)\/practitioners\/[^/]+$/, "version", (m) => `s:${m[1]}`, false],
    ["PATCH", /^\/api\/expenses\/([^/]+)$/, "expectedVersion", (m) => `e:${m[1]}`, true],
    ["DELETE", /^\/api\/expenses\/([^/]+)$/, "expectedVersion", (m) => `e:${m[1]}`, true],
    ["PATCH", /^\/api\/product-sales\/([^/]+)$/, "expectedVersion", (m) => `x:${m[1]}`, true],
    ["DELETE", /^\/api\/product-sales\/([^/]+)$/, "expectedVersion", (m) => `x:${m[1]}`, true],
    ["PATCH", /^\/api\/compensation\/([^/]+)$/, "expectedVersion", (m) => `c:${m[1]}`, true],
    // InventoryService.recordMovement: stock moves by its change from whatever the card holds.
    ["POST", /^\/api\/branches\/([^/]+)\/inventory\/([^/]+)\/(restock|movement)$/, null, (m) => `i:${m[1]}/${m[2]}`, false],
];

class Server {
    v = new Map<string, number>();
    constructor(start: Record<string, number>) {
        for (const [k, n] of Object.entries(start)) this.v.set(k, n);
    }
    /** Someone else saves the record. */
    foreign(rec: string) {
        this.v.set(rec, this.v.get(rec)! + 1);
    }
    answer(i: Item): { status: number; version?: number } {
        for (const [method, path, field, recOf, carries] of RULES) {
            const m = i.path.match(path);
            if (!m || method !== i.method) continue;
            const rec = recOf(m);
            const now = this.v.get(rec)!;
            const said = field ? JSON.parse(i.body)[field] : undefined;
            if (typeof said === "number" && said !== now) return { status: 409 };
            if (field && field !== "version" && typeof said !== "number") return { status: 400 };
            this.v.set(rec, now + 1);
            return { status: 200, ...(carries ? { version: now + 1 } : {}) };
        }
        throw new Error(`no rule for ${i.method} ${i.path}`);
    }
}

/** flush(): oldest first; a refused save stays as "couldn't send". */
function flush(server: Server, q: Item[]): Item[] {
    let items = q;
    for (const i of [...items.filter((x) => !x.failed)]) {
        const live = items.find((x) => x.id === i.id);
        if (!live || live.failed) continue;
        if (heldBehindRefused(items, live)) continue;
        const a = server.answer(live);
        if (a.status === 200) {
            landed(items, live, a.version);
            items = items.filter((x) => x.id !== live.id);
        } else live.failed = String(a.status);
    }
    return items;
}

let n = 0;
/** The person, offline: each save is made from `doc` as the page shows it now. */
function phone(key: string, doc: Doc) {
    const q: Item[] = [];
    const op = (i: Item): PendingOp => ({ method: i.method, path: i.path, body: JSON.parse(i.body), at: i.at, user: i.user });
    return {
        q,
        shown: () => applyOps(key, doc, q.filter((i) => !i.failed).map(op)) as Doc,
        save(method: string, path: string, body: Doc) {
            q.push({ id: `op${++n}`, method, path, body: JSON.stringify(body), at: "2026-11-27T02:00:00Z", user: "u1" });
        },
    };
}
const rows = (d: Doc) => d as unknown as Doc[];

const session = (version: number): Doc => ({
    id: "s1",
    version,
    finalPrice: "500.00",
    isVoided: false,
    practitioners: [{ practitionerId: "p1", remarks: null }],
});

function sessionPhone(server: Server) {
    const p = phone("session:s1", session(server.v.get("s:s1")!));
    const v = () => p.shown().version as number;
    const at = "/api/sessions/s1";
    return {
        ...p,
        price: (finalPrice: string) => p.save("PATCH", `${at}/final-price`, { finalPrice, version: v(), reason: null }),
        mark: (status: string) => p.save("PATCH", `${at}/status`, { status, version: v(), reason: null }),
        add: (pid: string) => p.save("POST", `${at}/practitioners`, { id: `sp-${pid}`, practitionerId: pid, reason: null, version: v() }),
        remarks: (pid: string, remarks: string | null) => p.save("PATCH", `${at}/practitioners/${pid}`, { remarks, reason: null, version: v() }),
        remove: (pid: string) => p.save("DELETE", `${at}/practitioners/${pid}`, { reason: null, version: v() }),
    };
}

const allLand = (q: Item[]) => {
    assert.deepEqual(
        q.map((i) => `${i.method} ${i.path} ${i.failed ?? "waiting"}`),
        [],
    );
};

test("a practitioner added, then the price changed: both land", () => {
    const server = new Server({ "s:s1": 3 });
    const s = sessionPhone(server);
    s.add("p2");
    s.price("650");
    allLand(flush(server, s.q));
    assert.equal(server.v.get("s:s1"), 5);
});

test("a practitioner added, then the session marked done: both land", () => {
    const server = new Server({ "s:s1": 1 });
    const s = sessionPhone(server);
    s.add("p2");
    s.mark("COMPLETED");
    allLand(flush(server, s.q));
});

test("a mark, then a price: the mark shows no new version, the price follows it", () => {
    const server = new Server({ "s:s1": 1 });
    const s = sessionPhone(server);
    s.mark("COMPLETED");
    s.price("650");
    s.add("p2");
    allLand(flush(server, s.q));
    assert.equal(server.v.get("s:s1"), 4);
});

test("a price shown at once, then a mark and remarks made on top of it, are not moved on twice", () => {
    const server = new Server({ "s:s1": 1 });
    const s = sessionPhone(server);
    s.price("650");
    assert.equal(s.shown().version, 2);
    s.mark("COMPLETED");
    s.remarks("p1", "Left side");
    s.remove("p1");
    s.price("700");
    allLand(flush(server, s.q));
    assert.equal(server.v.get("s:s1"), 6);
});

test("remarks saved unchanged still move the session on, and the price after them lands", () => {
    const server = new Server({ "s:s1": 1 });
    const s = sessionPhone(server);
    s.remarks("p1", null);
    s.price("650");
    allLand(flush(server, s.q));
});

test("a practitioner added and removed again before sending, then a price: all land", () => {
    const server = new Server({ "s:s1": 1 });
    const s = sessionPhone(server);
    s.add("p2");
    s.remove("p2");
    s.price("650");
    allLand(flush(server, s.q));
    assert.equal(server.v.get("s:s1"), 4);
});

test("an expense changed twice, then removed: all land", () => {
    const server = new Server({ "e:e1": 1 });
    const p = phone("exp:d1", [{ id: "e1", version: 1, amount: "10.00", category: "WATER", notes: null, deletedAt: null }] as unknown as Doc);
    const v = () => rows(p.shown())[0]!.version;
    p.save("PATCH", "/api/expenses/e1", { amount: "12", category: "WATER", notes: null, expectedVersion: v() });
    p.save("PATCH", "/api/expenses/e1", { amount: "15", category: "WATER", notes: null, expectedVersion: v() });
    p.save("DELETE", "/api/expenses/e1", { reason: "typo", expectedVersion: v() });
    allLand(flush(server, p.q));
});

test("a sale moved to another product, then removed: both land", () => {
    const server = new Server({ "x:x1": 2 });
    const sale = { id: "x1", productId: "pA", quantity: 1, unitPriceAtTime: "10.00", totalAmountAtTime: "10.00", version: 2, deletedAt: null };
    const p = phone("sales:d1", [sale] as unknown as Doc);
    const v = () => rows(p.shown())[0]!.version;
    p.save("PATCH", "/api/product-sales/x1", { productId: "pB", quantity: 1, expectedVersion: v(), reason: null });
    p.save("DELETE", "/api/product-sales/x1", { reason: "wrong", expectedVersion: v() });
    allLand(flush(server, p.q));
});

test("a sale's quantity changed, then moved to another product, then its quantity again: all land", () => {
    const server = new Server({ "x:x1": 1 });
    const sale = { id: "x1", productId: "pA", quantity: 1, unitPriceAtTime: "10.00", totalAmountAtTime: "10.00", version: 1, deletedAt: null };
    const p = phone("sales:d1", [sale] as unknown as Doc);
    const v = () => rows(p.shown())[0]!.version;
    p.save("PATCH", "/api/product-sales/x1", { productId: "pA", quantity: 2, expectedVersion: v(), reason: null });
    p.save("PATCH", "/api/product-sales/x1", { productId: "pB", quantity: 2, expectedVersion: v(), reason: null });
    p.save("PATCH", "/api/product-sales/x1", { productId: "pB", quantity: 3, expectedVersion: v(), reason: null });
    allLand(flush(server, p.q));
});

test("a pay entry changed twice: both land", () => {
    const server = new Server({ "c:c1": 1 });
    const p = phone("pay:d1", { id: "c1", version: 1, amount: "100.00" });
    const v = () => p.shown().version;
    p.save("PATCH", "/api/compensation/c1", { amount: "120", expectedVersion: v(), note: null, reason: null });
    p.save("PATCH", "/api/compensation/c1", { amount: "130", expectedVersion: v(), note: null, reason: null });
    allLand(flush(server, p.q));
});

test("stock moved on one product leaves another product's waiting move as it was made", () => {
    const server = new Server({ "i:b1/pA": 4, "i:b1/pB": 4 });
    const p = phone("inv:b1", [
        { productId: "pA", currentStock: 5, version: 4 },
        { productId: "pB", currentStock: 5, version: 4 },
    ] as unknown as Doc);
    const v = (pid: string) => rows(p.shown()).find((r) => r.productId === pid)!.version;
    const move = (pid: string) => ({ movementId: `m-${pid}-${++n}`, reason: "ADJUSTMENT", quantityChange: -1, branchDayId: "d1", expectedVersion: v(pid) });
    p.save("POST", "/api/branches/b1/inventory/pA/restock", { id: "r1", quantity: 3, branchDayId: "d1" });
    p.save("POST", "/api/branches/b1/inventory/pA/movement", move("pA"));
    const b = move("pB");
    p.save("POST", "/api/branches/b1/inventory/pB/movement", b);
    const [, a, mB] = p.q;
    landed(p.q, p.q[0]!, undefined);
    landed(p.q, a!, undefined);
    assert.equal(JSON.parse(mB!.body).expectedVersion, b.expectedVersion);
    allLand(flush(server, p.q.slice(2)));
});

// Someone else's change must still be refused, never written over.

test("someone else changed the session before the price went: the price is refused and the mark made on it waits", () => {
    const server = new Server({ "s:s1": 1 });
    const s = sessionPhone(server);
    s.price("650");
    s.mark("COMPLETED");
    server.foreign("s:s1");
    const left = flush(server, s.q);
    assert.deepEqual(
        left.map((i) => [i.path.split("/").pop(), i.failed ?? "waiting"]),
        [
            ["final-price", "409"],
            ["status", "waiting"],
        ],
    );
    assert.equal(server.v.get("s:s1"), 2);
});

test("someone else changed the session between two saves: the second is refused", () => {
    const server = new Server({ "s:s1": 1 });
    const s = sessionPhone(server);
    s.mark("COMPLETED");
    s.price("650");
    const [mark] = s.q;
    const first = server.answer(mark!);
    landed(s.q, mark!, first.version);
    server.foreign("s:s1");
    const left = flush(server, s.q.slice(1));
    assert.deepEqual(
        left.map((i) => i.failed),
        ["409"],
    );
});

test("someone else changed the session before a practitioner was added offline: refused, and the price after it waits", () => {
    const server = new Server({ "s:s1": 1 });
    const s = sessionPhone(server);
    s.add("p2");
    s.price("650");
    server.foreign("s:s1");
    const left = flush(server, s.q);
    assert.deepEqual(
        left.map((i) => i.failed ?? "waiting"),
        ["409", "waiting"],
    );
    assert.equal(server.v.get("s:s1"), 2);
});

test("someone else changed a pay entry first: the first edit is refused, the second waits on it", () => {
    const server = new Server({ "c:c1": 1 });
    const p = phone("pay:d1", { id: "c1", version: 1 });
    p.save("PATCH", "/api/compensation/c1", { amount: "120", expectedVersion: 1, note: null, reason: null });
    p.save("PATCH", "/api/compensation/c1", { amount: "130", expectedVersion: 1, note: null, reason: null });
    server.foreign("c:c1");
    assert.deepEqual(
        flush(server, p.q).map((i) => i.failed ?? "waiting"),
        ["409", "waiting"],
    );
    assert.equal(server.v.get("c:c1"), 2);
});

test("dropping a refused save never sends the ones made on top of it over someone else's change", () => {
    const server = new Server({ "s:s1": 1 });
    const s = sessionPhone(server);
    s.price("650");
    s.mark("COMPLETED");
    server.foreign("s:s1");
    let left = flush(server, s.q);
    left = discarded(left, left[0]!.id);
    left = flush(server, left);
    assert.equal(server.v.get("s:s1"), 2);
    assert.deepEqual(
        left.map((i) => [i.path.split("/").pop(), !!i.failed]),
        [["status", true]],
    );
});
