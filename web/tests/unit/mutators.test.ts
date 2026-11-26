import assert from "node:assert/strict";
import { test } from "node:test";
import { applyOp, applyOps, takeAnswer, type PendingOp } from "../../src/lib/mutators";
import captured from "./fixtures/mutators.json";

/**
 * Guards the offline queue's promise that a save shown on this phone is what the server will hold:
 * each case is a real server's document before and after one save (scripts/mutator-fixtures.mjs, run
 * against tools/verify/verify.sh up), and the local apply of that save must give the after document.
 */
type Case = { name: string; key: string; op: PendingOp; before: unknown; answer: unknown; after: unknown };
const cases = captured as Case[];

/** Times the server stamps itself: only whether one is set must agree. A null field and a missing one read the same on a page. */
const STAMPS = new Set(["createdAt", "deletedAt", "updatedAt", "updatedByName"]);
const norm = (doc: unknown): unknown =>
    JSON.parse(JSON.stringify(doc), (k, v) => (STAMPS.has(k) ? (v == null ? undefined : "set") : v === null ? undefined : v));

const CASES = [
    "expense create",
    "expense edit",
    "expense delete",
    "expense restore",
    "stock movement",
    "sale edit",
    "sale delete",
    "sale restore",
    "client edit",
    "session price",
    "session void",
    "session unvoid",
    "booking edit",
];

test("every op kind has a captured case", () => {
    assert.deepEqual(cases.map((c) => c.name).sort(), [...CASES].sort());
});

for (const c of cases) {
    test(`${c.name}: the local apply gives the server's document`, () => {
        assert.deepEqual(norm(applyOp(c.key, c.before, c.op)), norm(c.after));
    });

    test(`${c.name}: applying it to the server's document after it landed changes nothing`, () => {
        const again = applyOp(c.key, c.after, c.op);
        // A replay of a create or a versioned change must not add a second row or move the version again.
        if (!["session void", "session unvoid", "booking edit", "client edit"].includes(c.name)) assert.equal(again, c.after);
        assert.deepEqual(norm(again), norm(c.after));
    });

    test(`${c.name}: the server's answer, once it lands, leaves the shown document as the server's`, () => {
        const shown = applyOp(c.key, c.before, c.op);
        assert.deepEqual(norm(takeAnswer(c.key, shown, c.op, c.answer)), norm(c.after));
    });
}

/** The cases whose answer is the record itself (a void's answer is the void). */
const answersRecord = (c: Case) => {
    const a = c.answer as { id?: string; productId?: string; currentStock?: number };
    const id =
        c.op.method === "POST" && c.op.path.split("/").length === 3
            ? c.op.body.id
            : c.op.path.split("/")[c.key.startsWith("inv:") || c.key.startsWith("bookings:") ? 5 : 3];
    return c.key.startsWith("inv:") ? a.productId === id && a.currentStock !== undefined : a.id === id;
};

test("an answer that is the record, taken into the document the page still had, gives the server's document", () => {
    const taken = cases.filter(answersRecord);
    assert.ok(taken.length >= 10, `only ${taken.length} cases answer with the record`);
    for (const c of taken) assert.deepEqual(norm(takeAnswer(c.key, c.before, c.op, c.answer)), norm(c.after), c.name);
});

test("ops on other documents leave them as they are", () => {
    for (const c of cases) {
        const other = cases.find((x) => x.key !== c.key && !x.key.startsWith(c.key.split(":")[0]!));
        if (other) assert.equal(applyOp(other.key, other.before, c.op), other.before);
    }
});

test("two edits made offline in a row chain their versions, so both land", () => {
    const edit = cases.find((c) => c.name === "expense edit")!;
    const v = (edit.before as { id: string; version: number }[]).find((r) => edit.op.path.endsWith(r.id))!.version;
    const second: PendingOp = { ...edit.op, body: { ...edit.op.body, amount: "140", expectedVersion: v + 1 } };
    const shown = applyOps(edit.key, edit.before, [edit.op, second]) as { id: string; amount: string; version: number }[];
    const row = shown.find((r) => edit.op.path.endsWith(r.id))!;
    assert.equal(row.amount, "140.00");
    assert.equal(row.version, v + 2);
});
