import assert from "node:assert/strict";
import { test } from "node:test";
import { BUFFER_MAX, createRecorder, hash, routeOf, type Outcome } from "../../src/lib/telemetry";

test("hash is stable FNV-1a in base 36", () => {
    assert.equal(hash(""), (0x811c9dc5).toString(36));
    assert.equal(hash("Clock out"), hash("Clock out"));
    assert.notEqual(hash("Clock out"), hash("Clock in"));
});

test("a screen keeps only the query values that pick what's shown", () => {
    assert.equal(routeOf("#/finance?date=2026-10-27&q=Maria%20Santos"), "#/finance?date=2026-10-27");
    assert.equal(routeOf("#/clients?q=Maria"), "#/clients");
    assert.equal(routeOf(""), "#/");
});

const never = () => {};

function fakePost(outcomes: Outcome[]) {
    const bodies: { sessionId: string; events: { seq: number; kind: string; data: string }[] }[] = [];
    const post = async (body: string): Promise<Outcome> => {
        bodies.push(JSON.parse(body));
        return outcomes.shift() ?? "done";
    };
    return { bodies, post };
}

test("events upload in order with their session and sequence", async () => {
    const { bodies, post } = fakePost([]);
    const r = createRecorder("s1", post, () => 5, never);
    r.record("nav", { r: "#/today" });
    r.record("tap", { r: "#/today" });
    await r.flush();
    assert.equal(bodies.length, 1);
    assert.equal(bodies[0]!.sessionId, "s1");
    assert.deepEqual(
        bodies[0]!.events.map((e) => [e.seq, e.kind]),
        [
            [0, "nav"],
            [1, "tap"],
        ],
    );
    assert.equal(r.pending(), 0);
});

test("a failed upload keeps the events for the next try", async () => {
    const { bodies, post } = fakePost(["retry"]);
    const r = createRecorder("s1", post, Date.now, never);
    r.record("nav", { r: "#/today" });
    await r.flush();
    assert.equal(r.pending(), 1);
    await r.flush();
    assert.equal(r.pending(), 0);
    assert.equal(bodies[1]!.events[0]!.seq, 0);
});

test("the waiting buffer is bounded, oldest dropped first", () => {
    const r = createRecorder("s1", async () => "retry", Date.now, never);
    for (let i = 0; i < BUFFER_MAX + 5; i++) r.record("tap", { i });
    assert.equal(r.pending(), BUFFER_MAX);
});

test("an oversized event is cut to a marker, never sent whole", async () => {
    const { bodies, post } = fakePost([]);
    const r = createRecorder("s1", post, Date.now, never);
    r.record("error", { msg: "x".repeat(5000) });
    await r.flush();
    assert.equal(bodies[0]!.events[0]!.data, JSON.stringify({ cut: 5010 }));
});
