import assert from "node:assert/strict";
import { test } from "node:test";
import { freshLine, step, type FreshEvent, type Freshness } from "../../src/lib/freshness";

const run = (events: FreshEvent[]) => events.reduce<Freshness | null>(step, null);
const line = (events: FreshEvent[]) =>
    freshLine(run(events), (ms) => `t${ms}`)
        .join(" ")
        .trim();

test("a refresh shows the saved time, Updating… only once slow, then the answer's time", () => {
    assert.equal(line([{ type: "saved", at: 5 }]), "Updated t5");
    assert.equal(line([{ type: "saved", at: 5 }, { type: "slow" }]), "Updating…");
    assert.equal(line([{ type: "saved", at: 5 }, { type: "slow" }, { type: "answer", at: 9 }]), "Updated t9");
});

test("an answer before the slow mark never shows Updating…", () => {
    assert.equal(line([{ type: "saved", at: 5 }, { type: "answer", at: 9 }, { type: "slow" }]), "Updated t9");
});

test("no answer keeps the saved time and says so; a later answer makes it current", () => {
    assert.equal(line([{ type: "saved", at: 5 }, { type: "slow" }, { type: "lost" }]), "Offline, showing t5");
    assert.equal(line([{ type: "saved", at: 5 }, { type: "lost" }, { type: "answer", at: 9 }]), "Updated t9");
});

test("a failure after a later answer leaves the screen current", () => {
    assert.equal(line([{ type: "saved", at: 5 }, { type: "slow" }, { type: "answer", at: 9 }, { type: "lost" }]), "Updated t9");
});

test("nothing saved shows nothing until an answer", () => {
    assert.equal(line([{ type: "none" }, { type: "slow" }, { type: "lost" }]), "");
    assert.equal(line([{ type: "none" }, { type: "answer", at: 9 }]), "Updated t9");
});
