import assert from "node:assert/strict";
import { test } from "node:test";
import { buildSiteView, entryFromOp, isEntryUnsent, type EntryContext, type SiteEntry } from "../../src/lib/site-view";

const ctx = (extra: Partial<EntryContext> = {}): EntryContext => ({
    branchId: "b1",
    date: "2026-11-28",
    branchDayId: "day1",
    displayName: "Ana",
    clientNameById: (id) => (id === "c1" ? "Maria Cruz" : undefined),
    sessionClientById: (id) => (id === "s1" ? "Maria Cruz" : id === "s-old" ? "Juan Reyes" : undefined),
    ...extra,
});

test("session Done mark uses active branchId when body and query carry none", () => {
    const e = entryFromOp(
        {
            id: "op-mark",
            at: "2026-11-28T05:32:24.000Z",
            user: "u1",
            method: "PATCH",
            path: "/api/sessions/s1/status",
            body: JSON.stringify({ status: "COMPLETED", version: 1, reason: null }),
            what: "Session marked Done",
            branchId: "b1",
        },
        ctx(),
    );
    assert.deepEqual(e, {
        user: "u1",
        name: "Ana",
        at: "2026-11-28T05:32:24.000Z",
        what: "Session marked Done",
        kind: "mark",
        sessionId: "s1",
        status: "COMPLETED",
        client: "Maria Cruz",
    });
});

test("Done mark moves a session from Waiting to Done, and Undo moves it back", () => {
    const created: SiteEntry = {
        opId: "op1",
        kind: "session",
        user: "u1",
        name: "Ana",
        at: "2026-11-28T05:32:19.000Z",
        what: "New session",
        client: "Maria Cruz",
        sessionId: "s1",
        own: true,
    };
    const marked: SiteEntry = {
        opId: "op2",
        kind: "mark",
        user: "u1",
        name: "Ana",
        at: "2026-11-28T05:32:24.000Z",
        what: "Session marked Done",
        client: "Maria Cruz",
        sessionId: "s1",
        status: "COMPLETED",
        own: true,
    };
    const v1 = buildSiteView([created, marked]);
    assert.equal(v1.waiting.length, 0);
    assert.equal(v1.done.length, 1);
    assert.equal(v1.done[0]!.client, "Maria Cruz");
    assert.deepEqual(v1.done[0]!.relatedOpIds, ["op1", "op2"]);

    const undone: SiteEntry = {
        opId: "op3",
        kind: "mark",
        user: "u1",
        name: "Ana",
        at: "2026-11-28T05:32:30.000Z",
        what: "Session marked Waiting",
        sessionId: "s1",
        status: "PENDING",
        own: true,
    };
    const v2 = buildSiteView([created, marked, undone]);
    assert.equal(v2.waiting.length, 1);
    assert.equal(v2.done.length, 0);
});

test("marking an earlier server-created session Done still shows in Done", () => {
    const markOnly: SiteEntry = {
        opId: "op-old",
        kind: "mark",
        user: "u1",
        name: "Ana",
        at: "2026-11-28T05:30:16.000Z",
        what: "Session marked Done",
        client: "Juan Reyes",
        sessionId: "s-old",
        status: "COMPLETED",
    };
    const v = buildSiteView([markOnly]);
    assert.equal(v.waiting.length, 0);
    assert.equal(v.done.length, 1);
    assert.equal(v.done[0]!.client, "Juan Reyes");
});

test("server status clears stale Waiting when mark never reached hub", () => {
    const created: SiteEntry = {
        opId: "op1",
        kind: "session",
        user: "u1",
        name: "Ana",
        at: "2026-11-28T05:33:34.000Z",
        what: "New session",
        client: "Maria Cruz",
        sessionId: "s1",
    };
    const v = buildSiteView([created], [{ id: "s1", sessionStatus: "COMPLETED", isVoided: false, clientName: "Maria Cruz" }]);
    assert.equal(v.waiting.length, 0);
    assert.equal(v.done.length, 1);
});

test("own unsent badge clears immediately from live outbox even when hub unsent set is stale", () => {
    const ownEntry: SiteEntry = {
        opId: "op1",
        kind: "session",
        user: "u1",
        name: "Ana",
        at: "2026-11-28T05:32:19.000Z",
        what: "New session",
        client: "Maria Cruz",
        sessionId: "s1",
        own: true,
    };
    const staleHub = new Set(["op1"]);
    const emptyServer = new Map();
    assert.equal(isEntryUnsent(ownEntry, new Set(["op1"]), staleHub, emptyServer), true);
    assert.equal(isEntryUnsent(ownEntry, new Set(), staleHub, emptyServer), false);
});

test("other device unsent badge clears once the server list carries the session", () => {
    const otherEntry: SiteEntry = {
        opId: "op-other",
        kind: "session",
        user: "u2",
        name: "Ben",
        at: "2026-11-28T05:32:19.000Z",
        what: "New session",
        client: "Maria Cruz",
        sessionId: "s1",
        own: false,
    };
    const hubUnsent = new Set(["op-other"]);
    assert.equal(isEntryUnsent(otherEntry, new Set(), hubUnsent, new Map()), true);
    assert.equal(isEntryUnsent(otherEntry, new Set(), hubUnsent, new Map([["s1", { id: "s1", sessionStatus: "PENDING", isVoided: false }]])), false);
});
