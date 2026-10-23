import assert from "node:assert/strict";
import { test } from "node:test";
import { classify, parseBody } from "../../src/lib/outbox-classify";

test("any 2xx means the server has the save", () => {
    for (const s of [200, 201, 204]) assert.deepEqual(classify(s, null), { kind: "applied" });
});

test("signed out, too many tries and server trouble stop the round and keep the save", () => {
    for (const s of [401, 429, 500, 502, 503, 504, 599]) assert.deepEqual(classify(s, { error: "x" }), { kind: "retry" }, String(s));
});

test("a refusal fails the save with the server's own words", () => {
    assert.deepEqual(classify(400, { error: "Illegal session status transition from PENDING to PENDING" }), {
        kind: "failed",
        message: "Illegal session status transition from Waiting to Waiting",
    });
});

test("a refusal with no words gets one from its status", () => {
    assert.deepEqual(classify(409, null), { kind: "failed", message: "Someone changed this while you were offline." });
    assert.deepEqual(classify(403, "nope"), { kind: "failed", message: "You no longer have access to do this." });
    assert.deepEqual(classify(404, {}), { kind: "failed", message: "It no longer exists." });
    assert.deepEqual(classify(418, null), { kind: "failed", message: "The server refused it (418)." });
});

test("every status gets exactly one verdict", () => {
    for (let s = 200; s <= 599; s++) {
        const k = classify(s, null).kind;
        const want = s < 300 ? "applied" : s === 401 || s === 429 || s >= 500 ? "retry" : "failed";
        assert.equal(k, want, String(s));
    }
});

test("parseBody reads an object once and treats anything else as empty", () => {
    assert.deepEqual(parseBody('{"a":1}'), { a: 1 });
    for (const bad of ["", "nope", "[1]", "null", "3"]) assert.deepEqual(parseBody(bad), {}, bad);
});
