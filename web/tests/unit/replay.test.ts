import assert from "node:assert/strict";
import { test } from "node:test";
import { replayBody } from "../../src/lib/replay";

// Operational day turns at 04:00 Manila (UTC+8): 20:00Z is 04:00 the next morning.
const at = "2026-10-20T10:00:00Z"; // 18:00 Manila, day 2026-10-20
const sale = { path: "/api/product-sales", at, body: JSON.stringify({ id: "a", quantity: 1 }) };

test("a save sent the same day goes out untouched", () => {
    assert.equal(replayBody(sale, new Date("2026-10-20T15:00:00Z")), sale.body);
});

test("a save sent after the day turned over carries a reason", () => {
    const sent = JSON.parse(replayBody(sale, new Date("2026-10-20T21:00:00Z")));
    assert.equal(sent.reason, "Saved offline on 2026-10-20, sent on 2026-10-21");
    assert.equal(sent.id, "a");
});

test("a reason the person typed is kept", () => {
    const own = { ...sale, body: JSON.stringify({ id: "a", reason: "Late entry" }) };
    assert.equal(replayBody(own, new Date("2026-10-21T21:00:00Z")), own.body);
});

test("saves whose route takes no reason are left alone", () => {
    const x = { path: "/api/expenses", at, body: JSON.stringify({ amount: "5" }) };
    assert.equal(replayBody(x, new Date("2026-10-22T10:00:00Z")), x.body);
});

test("a mark replayed the next day carries a reason too", () => {
    const m = { path: "/api/sessions/s1/status", at, body: JSON.stringify({ status: "COMPLETED", version: 1 }) };
    assert.match(JSON.parse(replayBody(m, new Date("2026-10-21T10:00:00Z"))).reason, /^Saved offline on 2026-10-20/);
});
