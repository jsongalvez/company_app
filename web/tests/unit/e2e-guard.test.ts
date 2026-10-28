// The local-run guard behind `npm run e2e` (web/scripts/e2e-guard.mjs).
import { test } from "node:test";
import assert from "node:assert/strict";
// @ts-expect-error plain .mjs without types
import { localTarget, refusal } from "../../scripts/e2e-guard.mjs";

const local = {};

test("a bare local run is refused and the message names the way to run one file", () => {
    const msg = refusal([], local);
    assert.match(msg, /npm run e2e -- tests\/<name>\.e2e\.ts/);
});

test("a shard without files is still the full suite, refused locally", () => {
    assert.ok(refusal(["--shard", "1/4"], local));
    assert.ok(refusal(["--headed"], local));
});

test("naming a file, a directory, a title or last-failed is allowed locally", () => {
    assert.equal(refusal(["tests/undo.e2e.ts"], local), null);
    assert.equal(refusal(["undo"], local), null);
    assert.equal(refusal(["--headed", "tests/undo.e2e.ts:12"], local), null);
    assert.equal(refusal(["--grep", "Undo"], local), null);
    assert.equal(refusal(["--last-failed"], local), null);
});

test("an option value is not mistaken for a file", () => {
    assert.ok(refusal(["--shard", "2/4", "--config", "e2e.config.ts"], local));
});

test("GitHub (CI set) runs the full suite", () => {
    assert.equal(refusal(["--shard", "1/4"], { CI: "true" }), null);
});

test("a local run targets the verify copy, not the dev port nothing listens on", () => {
    assert.equal(localTarget({}), "http://127.0.0.1:18181");
    assert.equal(localTarget({ VERIFY_PORT: "18282" }), "http://127.0.0.1:18282");
});

test("APP_URL and GitHub keep their own target", () => {
    assert.equal(localTarget({ APP_URL: "http://localhost:18080" }), null);
    assert.equal(localTarget({ CI: "true" }), null);
});
