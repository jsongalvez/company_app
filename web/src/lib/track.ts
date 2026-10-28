import { createTracker } from "./error-tracker";
import { APP_VERSION } from "./version";

/** The live tracker: on only when the build was given VITE_SENTRY_DSN, otherwise every call does nothing. */
export const track = createTracker(
    import.meta.env.VITE_SENTRY_DSN,
    (url, body) => {
        void fetch(url, { method: "POST", body, keepalive: true, headers: { "Content-Type": "text/plain;charset=UTF-8" } }).catch(() => {});
    },
    APP_VERSION,
);

export function installTracker() {
    if (!track.enabled) return;
    addEventListener("error", (e) => track.captureError(e.error ?? e.message, "error"));
    addEventListener("unhandledrejection", (e) => track.captureError(e.reason, "unhandledrejection"));
}
