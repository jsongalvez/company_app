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

export function installRecorder() {
    recorder.record("boot", {
        v: __APP_VERSION__,
        r: route(),
        w: innerWidth,
        h: innerHeight,
        dpr: devicePixelRatio,
        online: navigator.onLine,
        sw: !!navigator.serviceWorker?.controller,
    });
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
    document.addEventListener("visibilitychange", () => document.visibilityState === "hidden" && void recorder.flush());

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
    });
}
