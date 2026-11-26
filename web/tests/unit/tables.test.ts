// What a save makes stale comes from the server's X-Reads and X-Changed (src/lib/tables.ts), not a hand-kept list.
import assert from "node:assert/strict";
import { test } from "node:test";
import { changesOf, Seen, staleKeys, stillStale, tablesOf, type Cached } from "../../src/lib/tables";

const t = (...names: string[]) => new Set(names);
const entries = (e: Record<string, Cached>) => Object.entries(e);

test("a save makes stale the entries whose reads meet its change, and only those", () => {
    const store = entries({ expenses: { reads: t("expense", "app_user") }, allowances: { reads: t("allowance") }, totals: { reads: t("expense", "session") } });
    assert.deepEqual(staleKeys(store, t("audit_log", "expense")), ["expenses", "totals"]);
    assert.deepEqual(staleKeys(store, t("allowance", "audit_log")), ["allowances"]);
    assert.deepEqual(staleKeys(store, t("product")), []);
});

test("what the server didn't name counts as every table", () => {
    assert.equal(tablesOf(null), "*");
    assert.equal(tablesOf("expense,*"), "*");
    assert.deepEqual(tablesOf(""), t());
    assert.deepEqual(tablesOf("audit_log,expense"), t("audit_log", "expense"));
    // A seeded or restored entry has no reads: any change makes it stale.
    assert.deepEqual(staleKeys(entries({ seeded: {}, star: { reads: "*" }, other: { reads: t("product") } }), t("expense")), ["seeded", "star"]);
    // A save answer with no X-Changed makes everything stale.
    const noHeader = changesOf("POST", new Headers());
    assert.equal(noHeader, "*");
    assert.deepEqual(staleKeys(entries({ a: { reads: t("product") }, b: { reads: t("expense") } }), noHeader), ["a", "b"]);
});

test("a save that changed nothing, a read, and an answer made on the phone make nothing stale", () => {
    const store = entries({ a: { reads: "*" } });
    assert.deepEqual(staleKeys(store, changesOf("PATCH", new Headers({ "X-Changed": "" }))), []);
    assert.deepEqual(staleKeys(store, changesOf("GET", new Headers({ "X-Changed": "branch_day" }))), []);
    assert.deepEqual(staleKeys(store, changesOf("POST", new Headers({ "X-Queued": "1" }))), []);
    assert.deepEqual(staleKeys(store, changesOf("POST", new Headers({ "X-Not-Sent": "1" }))), []);
});

test("a loader's reads are the union of its requests', and a loader that noted none read everything", () => {
    const s = new Seen();
    assert.equal(s.tables, "*");
    s.add(t("session"));
    s.add(t("attendance", "session"));
    assert.deepEqual(s.tables, t("attendance", "session"));
    s.add("*");
    s.add(t("expense"));
    assert.equal(s.tables, "*");
});

test("a re-read after a save skips what the save's answer or the page already refreshed", () => {
    // Marked stale at 5: the answer in the store came from request 4.
    assert.equal(stillStale({ seq: 4, staleAt: 5 }), true);
    // The page re-read it itself (request 5 in flight), or the answer seeded it (seq 6, no stale mark).
    assert.equal(stillStale({ seq: 4, staleAt: 5, inflight: {}, inflightSeq: 5 }), false);
    assert.equal(stillStale({ seq: 6 }), false);
    // A request that started before the save does not count.
    assert.equal(stillStale({ seq: 3, staleAt: 5, inflight: {}, inflightSeq: 4 }), true);
    // Its answer, once in, still predates the save.
    assert.equal(stillStale({ seq: 4, staleAt: 5 }), true);
    assert.equal(stillStale({ seq: 5, staleAt: 5 }), false);
});
