import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { FAMILIES, key } from "../../src/lib/keys";
import { writes, type Write } from "../../src/lib/writes";
import pinned from "./fixtures/keys-f665552f.json";

/** Every loader's key at f665552f, before keys.ts existed, with each part written as X. */
const before = pinned as Record<string, string>;
const X = "X";
const builders = key as unknown as Record<string, (...a: unknown[]) => string>;
const full = (name: string) => builders[name](...Array.from({ length: builders[name].length }, () => X));

test("each key builder gives the key its loader had before", () => {
    for (const [name, k] of Object.entries(before)) {
        assert.ok(name in key, `keys.ts has no builder for loader ${name}`);
        assert.equal(full(name), k, name);
    }
});

test("every loader in data.ts takes its key from the builder of the same name", () => {
    const src = readFileSync("src/lib/data.ts", "utf8");
    const body = src.slice(src.indexOf("export const load = {"));
    const loaders = [...body.matchAll(/^ {4}(\w+): \(/gm)].map((m) => m[1]);
    assert.deepEqual(loaders.sort(), Object.keys(before).sort());
    for (const name of loaders) assert.match(body, new RegExp(`^ {4}${name}: \\([^)]*\\) =>\\s*L\\(key\\.${name}\\(`, "m"), name);
});

test("a family with no parts never matches a longer family's keys", () => {
    for (const f of FAMILIES) for (const g of FAMILIES) if (f !== g) assert.ok(!`${g}:`.startsWith(`${f}:`), `${f} vs ${g}`);
});

/** Each save's prefixes as its page typed them at f665552f, with each id written as X. */
const typedBefore: Record<Write, { args: unknown[]; prefixes: string[] }> = {
    rights: { args: [], prefixes: [""] },
    clockApplied: { args: [], prefixes: ["dash:", "roster:"] },
    clockOutHeld: { args: [], prefixes: ["dash:", "roster:"] },
    sessionAdded: { args: [X, X], prefixes: ["dash:", "dsess:", "dsum:", "clients:", "client:X", "session:X"] },
    sessionChanged: { args: [X], prefixes: ["session:X", "dash:", "dsess:", "audit:session:X"] },
    todayLists: { args: [X], prefixes: ["dash:X", "roster:X", "bookings:X"] },
    pastDayLists: { args: [X, X], prefixes: ["bday:X:X", "dsess:X:X", "dsum:X:X", "dash:waiting:X"] },
    dayRow: { args: [X, X], prefixes: ["bday:X:X"] },
    clientAdded: { args: [], prefixes: ["clients:"] },
    clientEdited: { args: [X], prefixes: ["client:X", "clients:", "audit:client:X"] },
    clientAnonymized: { args: [X], prefixes: ["client:X", "clients:"] },
    concerns: { args: [], prefixes: ["concerns"] },
    bookings: { args: [], prefixes: ["bookings:"] },
    productSale: { args: [X], prefixes: ["inv:X", "dash:X", "dsess:", "dsum:", "moves:", "sales:", "audit:product_sale:"] },
    stock: { args: [X], prefixes: ["inv:X", "moves:X"] },
    catalog: { args: [], prefixes: ["products", "categories", "inv:"] },
    finance: { args: [X, X, X, ["exp", "split", "summary"]], prefixes: ["exp:X", "split:X", "dsum:X:X", "audit:expense:"] },
    pay: { args: ["comp", X], prefixes: ["comp:X"] },
    remittances: { args: [X], prefixes: ["rem:X"] },
    remittance: { args: [X], prefixes: ["remd:X", "rem:"] },
    remitted: { args: [], prefixes: ["dash:", "dsum"] },
    relief: { args: [], prefixes: ["relief:"] },
    branches: { args: [], prefixes: ["branches"] },
    assignments: { args: [X, true], prefixes: ["assign:X", "users"] },
    rates: { args: [X], prefixes: ["rates:X"] },
    delegates: { args: [X], prefixes: ["deleg:X"] },
    users: { args: [], prefixes: ["users", "assign:"] },
    notifications: { args: [], prefixes: ["notif"] },
    auditFlags: { args: [], prefixes: ["audit:flagged"] },
};

/** Every key the app can hold, plus the variants a save names by table or id. */
const universe = [
    ...Object.values(before),
    "audit:product_sale:X",
    "audit:expense:X",
    "audit:session:X",
    "audit:client:X",
    "audit:product_sale_x:X",
    "boot:me",
    "boot:today:X:X",
];
const hits = (prefixes: string[]) => universe.filter((k) => prefixes.some((p) => k.startsWith(p))).sort();

test("each save drops the same keys as the prefixes its page typed before", () => {
    assert.deepEqual(Object.keys(writes).sort(), Object.keys(typedBefore).sort());
    for (const [name, { args, prefixes }] of Object.entries(typedBefore)) {
        const row = writes[name as Write];
        const now = (row.keys as (...a: unknown[]) => string[])(...args);
        assert.deepEqual(hits(now), hits(prefixes), name);
    }
});

test("the two finance and pay variants drop only their own lists", () => {
    assert.deepEqual(writes.finance.keys(X, X, X, ["split"]), ["split:X"]);
    assert.deepEqual(writes.pay.keys("allow", X), ["allow:X"]);
    assert.deepEqual(
        writes.productSale.keys(X, "D").filter((k) => k.startsWith("sales")),
        ["sales:D"],
    );
    assert.deepEqual(writes.assignments.keys(X, false), ["assign:X"]);
});

/** Lists no save in the app changes; a loader added here needs a write row in writes.ts or a reason. */
const NEVER_DROPPED: Record<string, string> = {
    members: "branch members change on the Branches page, which re-reads assignments",
    dayUsers: "who worked a day: read fresh after FRESH_MS",
    clientBookings: "a client's bookings drop with the bookings family",
    dailySummaries: "summary ranges: dropped with dsums by remitted",
    monthly: "monthly report: read fresh after FRESH_MS",
    myHistory: "own history: read fresh after FRESH_MS",
    auditTables: "fixed list",
    roles: "fixed list",
    reliefMine: "dropped with the relief family",
    audit: "one row's history: saves drop it by table and id, as audit:session:<id>",
};

test("every loader is dropped by some save, or says why not", () => {
    const samples = [...Object.entries(typedBefore), ["pay", { args: ["allow", X] }] as const];
    const dropped = samples.flatMap(([name, { args }]) => (writes[name as Write].keys as (...a: unknown[]) => string[])(...args).filter((p) => p !== ""));
    const missing = Object.entries(before)
        .filter(([name, k]) => !(name in NEVER_DROPPED) && !dropped.some((p) => k.startsWith(p)))
        .map(([name]) => name);
    assert.deepEqual(missing, [], "add a row to src/lib/writes.ts, or a reason to NEVER_DROPPED");
});
