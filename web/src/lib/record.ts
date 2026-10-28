import { api, getToken, uuid } from "../api/client";
import { createRecorder, describe, fieldOf, norm, routeOf, type Outcome } from "./telemetry";

/** The live recorder for this page load; its id goes into problem reports so the replay tool can find it. */
export const recorder = createRecorder(uuid(), async (body): Promise<Outcome> => {
    const token = getToken();
    if (!token) return "retry";
    try {
        const res = await fetch("/api/telemetry", {
            method: "POST",
            body,
            keepalive: true,
            headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        });
        return res.status === 401 || res.status === 429 || res.status >= 500 ? "retry" : "done";
    } catch {
        return "retry";
    }
});

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

function recordNetwork() {
    const nav = performance.getEntriesByType("navigation")[0] as PerformanceNavigationTiming | undefined;
    if (nav) recorder.record("net", { ...network(nav), nav: nav.type });
    if (typeof PerformanceObserver === "undefined") return;
    new PerformanceObserver((list) => {
        for (const e of list.getEntries() as PerformanceResourceTiming[]) {
            const path = new URL(e.name).pathname;
            if (e.initiatorType === "fetch" && (path.startsWith("/api/") || path.startsWith("/auth/")) && path !== "/api/telemetry")
                recorder.record("net", network(e));
        }
    }).observe({ type: "resource", buffered: false });
    addEventListener("resourcetimingbufferfull", () => performance.clearResourceTimings());
}

export function installRecorder() {
    recorder.record("boot", {
        v: __APP_VERSION__,
        r: route(),
        w: innerWidth,
        h: innerHeight,
        dpr: devicePixelRatio,
        online: navigator.onLine,
        sw: !!navigator.serviceWorker?.controller,
        ...link(),
    });
    recordNetwork();
    let hiddenAt: number | null = null;
    document.addEventListener("visibilitychange", () => {
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
    addEventListener("online", () => recorder.record("vis", { st: "online", ...link() }));
    addEventListener("offline", () => recorder.record("vis", { st: "offline" }));
    addEventListener("hashchange", () => recorder.record("nav", { r: route() }));
    document.addEventListener("click", (e) => e.target instanceof Element && recorder.record("tap", { r: route(), el: describe(e.target) }), {
        capture: true,
        passive: true,
    });
    document.addEventListener(
        "change",
        (e) => {
            const t = e.target;
            if (t instanceof HTMLInputElement || t instanceof HTMLSelectElement || t instanceof HTMLTextAreaElement)
                recorder.record("input", { r: route(), ...fieldOf(t) });
        },
        true,
    );
    addEventListener("error", (e) => recorder.record("error", { r: route(), msg: norm(String(e.message)) }));
    addEventListener("unhandledrejection", (e) =>
        recorder.record("error", { r: route(), msg: norm(e.reason instanceof Error ? `${e.reason.name}: ${e.reason.message}` : String(e.reason)) }),
    );
    addEventListener("pagehide", () => void recorder.flush());

    const started = new WeakMap<Request, number>();
    api.use({
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
    });
}
