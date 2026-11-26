import { api, getToken, OFFLINE_REFUSED, type OfflineRefused, uuid } from "../api/client";
import { REFUSED, type Refused } from "./outbox.svelte";
import { createRecorder, describe, fieldOf, norm, plainPath, routeOf, sendBatch } from "./telemetry";
import type { Middleware } from "openapi-fetch";
import { APP_VERSION } from "./version";
import { wiring, type Stop } from "./wiring";

type Wiring = ReturnType<typeof wiring>;

/** The live recorder for this page load; its id goes into problem reports so the replay tool can find it. */
export const recorder = createRecorder(uuid(), (body) => sendBatch(body, getToken(), fetch));

const route = () => routeOf(location.hash);

function serverTiming(header: string | null): Record<string, number> {
    const out: Record<string, number> = {};
    for (const m of (header ?? "").matchAll(/(\w+);dur=([\d.]+)/g)) out[m[1]!] = Math.round(Number(m[2]));
    return out;
}

/** The phone's own guess at its link, where the browser shares it (Chrome on Android does). */
function link(): Record<string, unknown> {
    const c = (navigator as Navigator & { connection?: { effectiveType?: string; rtt?: number; downlink?: number } }).connection;
    return c ? { et: c.effectiveType, rtt: c.rtt, dl: c.downlink } : {};
}

const ms = (a: number, b: number) => (a > 0 && b > 0 ? Math.round(b - a) : null);

/**
 * How one request crossed the network: protocol (h3, h2, http/1.1), whether it opened a new connection and how
 * long that took, how long it waited before it was sent, and time to first byte. A request that waits seconds
 * with the server idle shows here as a long `wait` or `cn`.
 */
function network(e: PerformanceResourceTiming): Record<string, unknown> {
    return {
        p: new URL(e.name).pathname,
        proto: e.nextHopProtocol || null,
        t: Math.round(e.startTime),
        dns: ms(e.domainLookupStart, e.domainLookupEnd),
        cn: ms(e.connectStart, e.connectEnd),
        tls: ms(e.secureConnectionStart, e.connectEnd),
        wait: ms(e.startTime, e.requestStart),
        ttfb: ms(e.requestStart, e.responseStart),
        all: Math.round(e.duration),
    };
}

function recordNetwork(w: Wiring) {
    const nav = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming | undefined;
    if (nav) recorder.record("net", { ...network(nav), nav: nav.type });
    if (typeof PerformanceObserver === "undefined") return;
    const watcher = new PerformanceObserver((list) => {
        for (const e of list.getEntries() as PerformanceResourceTiming[]) {
            const path = new URL(e.name).pathname;
            if (e.initiatorType === "fetch" && (path.startsWith("/api/") || path.startsWith("/auth/")) && path !== "/api/telemetry")
                recorder.record("net", network(e));
        }
    });
    watcher.observe({ type: "resource", buffered: false });
    w.also(() => watcher.disconnect());
    w.on(window, "resourcetimingbufferfull", () => performance.clearResourceTimings());
}

export function installRecorder(): Stop {
    const w = wiring();
    recorder.record("boot", {
        v: APP_VERSION,
        r: route(),
        w: innerWidth,
        h: innerHeight,
        dpr: devicePixelRatio,
        online: navigator.onLine,
        sw: !!navigator.serviceWorker?.controller,
        ...link(),
    });
    recordNetwork(w);
    let hiddenAt: number | null = null;
    w.on(document, "visibilitychange", () => {
        if (document.visibilityState === "hidden") {
            hiddenAt = performance.now();
            recorder.record("vis", { st: "hidden", r: route() });
            void recorder.flush();
        } else {
            recorder.record("vis", {
                st: "visible",
                r: route(),
                away: hiddenAt === null ? null : Math.round(performance.now() - hiddenAt),
                online: navigator.onLine,
                ...link(),
            });
            hiddenAt = null;
        }
    });
    w.on(window, "online", () => recorder.record("vis", { st: "online", ...link() }));
    w.on(window, "offline", () => recorder.record("vis", { st: "offline" }));
    w.on(window, "hashchange", () => recorder.record("nav", { r: route() }));
    w.on(document, "click", (e) => e.target instanceof Element && recorder.record("tap", { r: route(), el: describe(e.target) }), {
        capture: true,
        passive: true,
    });
    w.on(
        document,
        "change",
        (e) => {
            const t = e.target;
            if (t instanceof HTMLInputElement || t instanceof HTMLSelectElement || t instanceof HTMLTextAreaElement)
                recorder.record("input", { r: route(), ...fieldOf(t) });
        },
        true,
    );
    w.on<ErrorEvent>(window, "error", (e) => recorder.record("error", { r: route(), msg: norm(String(e.message)) }));
    w.on<PromiseRejectionEvent>(window, "unhandledrejection", (e) =>
        recorder.record("error", { r: route(), msg: norm(e.reason instanceof Error ? `${e.reason.name}: ${e.reason.message}` : String(e.reason)) }),
    );
    w.on<CustomEvent<Refused>>(window, REFUSED, (e) => {
        const f = e.detail;
        recorder.record("error", { r: route(), outbox: `${f.method} ${plainPath(f.path)}`, s: f.status });
        void recorder.flush();
    });
    // Where people hit "needs signal": which screen, which save.
    w.on<CustomEvent<OfflineRefused>>(window, OFFLINE_REFUSED, (e) => {
        const f = e.detail;
        recorder.record("net", { r: route(), offline: `${f.method} ${plainPath(f.path)}` });
    });
    w.on(window, "pagehide", () => void recorder.flush());

    const started = new WeakMap<Request, number>();
    const timing: Middleware = {
        onRequest({ request }) {
            started.set(request, performance.now());
        },
        onResponse({ request, response }) {
            const path = new URL(request.url).pathname;
            const t0 = started.get(request);
            recorder.record("api", {
                r: route(),
                m: request.method,
                p: path,
                s: response.status,
                ms: t0 === undefined ? null : Math.round(performance.now() - t0),
                tr: response.headers.get("X-Trace-Id"),
                ...serverTiming(response.headers.get("Server-Timing")),
            });
            // A problem report is read soon after it's sent: upload what led to it now.
            if (path === "/api/feedback-notes" && request.method === "POST") void recorder.flush();
        },
        onError({ request, error }) {
            const t0 = started.get(request);
            recorder.record("api", {
                r: route(),
                m: request.method,
                p: new URL(request.url).pathname,
                s: 0,
                ms: t0 === undefined ? null : Math.round(performance.now() - t0),
                err: error instanceof Error ? error.name : "unknown",
            });
        },
    };
    api.use(timing);
    w.also(() => api.eject(timing));
    return w.stop;
}
