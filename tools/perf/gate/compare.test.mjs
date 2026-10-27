import { test } from "node:test";
import assert from "node:assert/strict";
import { compare, parseLog, render, KEY_ROUTES } from "./compare.mjs";

const TODAY = "GET /api/branches/{branchId}/today-screen";
const line = (method, template, elapsedMs, queries, status = 200) =>
    `2026-10-27T07:03:55,402 [Jetty] INFO c.c.companyapp.logging.RequestLog - {"event":"request_completed","traceId":"t","method":"${method}","route":"/x","template":"${template}","status":${status},"elapsedMs":${elapsedMs},"dbMs":1,"queries":${queries}}`;

// 20 samples per key route per round: the p95 is the 19th smallest.
function round({ today = 20, todayQ = 6, save = 30, saveQ = 4, noise = false } = {}) {
    const rows = new Map();
    const add = (key, ms, q, n = 20) => {
        const arr = Array.from({ length: n }, (_, i) => ({ ms: ms + (i % 3), queries: q }));
        if (noise) arr[n - 1].ms = ms * 20;
        rows.set(key, arr);
    };
    add(TODAY, today, todayQ);
    add("POST /api/sessions", save, saveQ);
    add("POST /auth/login", 350, 2);
    add("GET /api/branches/{branchId}/daily-summary", 60, 7);
    add("GET /api/expenses", 15, 2);
    rows.set("GET /api/other", Array.from({ length: 20 }, () => ({ ms: 5, queries: 1 })));
    return rows;
}
const four = (opts) => Array.from({ length: 4 }, () => round(opts));
const failures = (r) => r.rows.filter((x) => x.problems.length).map((x) => x.name);

test("identical sides pass", () => {
    assert.deepEqual(failures(compare({ base: four(), head: four() })), []);
});

test("Today going from 20ms to 420ms fails and the summary says so", () => {
    const r = compare({ base: four(), head: four({ today: 420 }) });
    assert.deepEqual(failures(r), ["Today"]);
    assert.match(render(r, 4), /Today p95 base 22ms head 422ms, queries 6 to 6, FAIL/);
});

test("one extra query on a key route fails with no change in time", () => {
    assert.deepEqual(failures(compare({ base: four(), head: four({ saveQ: 5 }) })), ["Save session"]);
});

test("one slow round in four passes (a noisy runner)", () => {
    const head = four();
    head[2] = round({ today: 420 });
    assert.deepEqual(failures(compare({ base: four(), head })), []);
});

test("two slow rounds in four pass, three fail", () => {
    const slow = (n) => Array.from({ length: 4 }, (_, i) => (i < n ? round({ today: 420 }) : round()));
    assert.deepEqual(failures(compare({ base: four(), head: slow(2) })), []);
    assert.deepEqual(failures(compare({ base: four(), head: slow(3) })), ["Today"]);
});

test("a noisy base round, one outlier request, passes", () => {
    assert.deepEqual(failures(compare({ base: [round({ noise: true }), ...four().slice(1)], head: four() })), []);
});

test("slower by over 30% but under 25ms passes", () => {
    assert.deepEqual(failures(compare({ base: four({ save: 40 }), head: four({ save: 60 }) })), []);
});

test("slower by over 25ms but under 30% passes", () => {
    assert.deepEqual(failures(compare({ base: four({ save: 300 }), head: four({ save: 380 }) })), []);
});

test("a slower route that is not a key route is listed and not gated", () => {
    const head = four();
    head.forEach((r) => r.set("GET /api/other", Array.from({ length: 20 }, () => ({ ms: 500, queries: 40 }))));
    const r = compare({ base: four(), head });
    assert.deepEqual(failures(r), []);
    assert.match(render(r, 4), /\| GET \/api\/other \| 5ms \| 500ms \| 4 of 4 \| 1 to 40 \| not gated \|/);
});

test("faster passes", () => {
    assert.deepEqual(failures(compare({ base: four({ today: 200 }), head: four({ today: 20 }) })), []);
});

test("a key route missing from the head fails", () => {
    const head = four();
    head.forEach((r) => r.delete(TODAY));
    assert.deepEqual(failures(compare({ base: four(), head })), ["Today"]);
});

test("a base without query counts skips the query check", () => {
    const base = four();
    base.forEach((r) => r.set("POST /api/sessions", r.get("POST /api/sessions").map(({ ms }) => ({ ms }))));
    assert.deepEqual(failures(compare({ base, head: four() })), []);
});

test("parseLog reads request_completed lines among other output and drops errors", () => {
    const text = ["INFO something else", line("GET", "/a/{id}", 12, 3), line("GET", "/a/{id}", 9, 3, 500), line("GET", "UNMATCHED", 1, 0, 200), "\u001b[0m" + line("GET", "/a/{id}", 14, 3)].join("\n");
    const m = parseLog(text);
    assert.deepEqual([...m.keys()], ["GET /a/{id}"]);
    assert.deepEqual(m.get("GET /a/{id}"), [{ ms: 12, queries: 3 }, { ms: 14, queries: 3 }]);
});

test("every key route is named once", () => {
    assert.equal(new Set(Object.values(KEY_ROUTES)).size, Object.keys(KEY_ROUTES).length);
});
