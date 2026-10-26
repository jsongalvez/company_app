import assert from "node:assert/strict";
import { test } from "node:test";
import { createTracker, parseDsn, plainPath } from "../../src/lib/error-tracker";

const DSN = "https://abc123@tracker.example/42";
const UUID = "123e4567-e89b-42d3-a456-426614174000";

function fake(dsn: string | undefined = DSN) {
    const sent: { url: string; event: any; raw: string }[] = [];
    let t = 1_000_000;
    const tracker = createTracker(
        dsn,
        (url, raw) => sent.push({ url, raw, event: JSON.parse(raw.split("\n")[2]!) }),
        "2026-10-25 05:34",
        () => t,
    );
    return { tracker, sent, wait: (ms: number) => (t += ms) };
}

test("a forced 500 sends one event with the trace id", () => {
    const { tracker, sent } = fake();
    tracker.captureApiFailure({ method: "GET", path: `/api/clients/${UUID}/sessions`, status: 500, traceId: "tr-abc" });
    assert.equal(sent.length, 1);
    assert.equal(sent[0]!.url, "https://tracker.example/api/42/envelope/?sentry_version=7&sentry_key=abc123");
    assert.equal(sent[0]!.event.tags.trace_id, "tr-abc");
    assert.equal(sent[0]!.event.tags.path, "/api/clients/{id}/sessions");
    assert.equal(sent[0]!.event.release, "2026-10-25 05:34");
    assert.ok(!sent[0]!.raw.includes(UUID), "ids stay out of the event");
});

test("a thrown error sends its class and short message, and an unhandled rejection too", () => {
    const { tracker, sent } = fake();
    tracker.captureError(new TypeError("x is undefined"), "error");
    tracker.captureError(new RangeError("bad"), "unhandledrejection");
    assert.equal(sent.length, 2);
    assert.equal(sent[0]!.event.exception.values[0].type, "TypeError");
    assert.equal(sent[0]!.event.exception.values[0].value, "x is undefined");
    assert.equal(sent[1]!.event.tags.kind, "unhandledrejection");
});

test("server words in an ApiError never leave the phone", () => {
    class ApiError extends Error {
        status = 400;
    }
    const { tracker, sent } = fake();
    tracker.captureError(new ApiError("Maria Santos already has a session"), "unhandledrejection");
    assert.equal(sent.length, 0, "its request is the report; the text stays home");
});

test("a refused offline save is reported by method, path and status only", () => {
    const { tracker, sent } = fake();
    tracker.captureOutboxFailure({ method: "POST", path: `/api/branches/${UUID}/product-sales?q=Maria`, status: 400 });
    assert.equal(sent.length, 1);
    assert.equal(sent[0]!.event.tags.kind, "outbox");
    assert.ok(!sent[0]!.raw.includes("Maria") && !sent[0]!.raw.includes(UUID));
});

test("expected statuses are not reported", () => {
    const { tracker, sent } = fake();
    for (const status of [200, 304, 401, 409, 429]) tracker.captureApiFailure({ method: "GET", path: "/api/x", status, traceId: null });
    assert.equal(sent.length, 0);
});

test("the same failure repeats once a minute, and a page sends at most 20", () => {
    const { tracker, sent, wait } = fake();
    const f = { method: "GET", path: "/api/x", status: 500, traceId: null };
    tracker.captureApiFailure(f);
    tracker.captureApiFailure(f);
    assert.equal(sent.length, 1);
    wait(60_001);
    tracker.captureApiFailure(f);
    assert.equal(sent.length, 2);
    for (let i = 0; i < 40; i++) tracker.captureApiFailure({ ...f, path: `/api/p${i}` });
    assert.equal(sent.length, 20);
});

test("with no DSN, or a bad one, nothing is sent and nothing throws", () => {
    for (const dsn of ["", "nope", "https://tracker.example/42"]) {
        const { tracker, sent } = fake(dsn);
        assert.equal(tracker.enabled, false, String(dsn));
        tracker.captureError(new Error("x"), "error");
        tracker.captureApiFailure({ method: "GET", path: "/api/x", status: 500, traceId: "t" });
        assert.equal(sent.length, 0);
    }
});

test("with the DSN left out entirely, nothing is sent", () => {
    const sent: string[] = [];
    const tracker = createTracker(undefined, (_u, b) => sent.push(b), "v");
    tracker.captureError(new Error("x"), "error");
    assert.equal(tracker.enabled, false);
    assert.equal(sent.length, 0);
});

test("a transport that throws does not break the app", () => {
    const tracker = createTracker(
        DSN,
        () => {
            throw new Error("offline");
        },
        "v",
    );
    tracker.captureApiFailure({ method: "GET", path: "/api/x", status: 500, traceId: null });
});

test("dsn and path helpers", () => {
    assert.equal(parseDsn("https://k@o.example:8443/7")?.url, "https://o.example:8443/api/7/envelope/?sentry_version=7&sentry_key=k");
    assert.equal(plainPath(`/api/a/${UUID}?x=1`), "/api/a/{id}");
});

test("Today's 'clock in first' refusal is not an error (COMPANY-APP-4)", () => {
    const { tracker, sent } = fake();
    tracker.captureApiFailure({ method: "GET", path: `/api/branches/${UUID}/dashboard/today`, status: 403, traceId: "t" });
    assert.equal(sent.length, 0);
    tracker.captureApiFailure({ method: "GET", path: `/api/branches/${UUID}/inventory`, status: 403, traceId: "t" });
    assert.equal(sent.length, 1, "other refusals still report");
});

test("a rejected server answer is reported once, as the request (COMPANY-APP-3)", () => {
    const { tracker, sent } = fake();
    const e = Object.assign(new Error("Invalid token"), { status: 401 });
    tracker.captureError(e, "unhandledrejection");
    assert.equal(sent.length, 0);
});
