/**
 * Connection state for weak mobile signal. `online` follows the browser; `trouble` is set
 * when requests fail even though the browser thinks it's online (one bar, dead Wi-Fi).
 * While in trouble we ping /health until the server answers, then fire RECONNECTED so
 * failed page loads fetch again by themselves.
 */
export const net = $state({ online: navigator.onLine, trouble: false });

export const RECONNECTED = "app:reconnected";

let pinging: ReturnType<typeof setInterval> | undefined;

function reconnected() {
    net.trouble = false;
    clearInterval(pinging);
    pinging = undefined;
    window.dispatchEvent(new Event(RECONNECTED));
}

export function reportNetFailure() {
    net.trouble = true;
    if (pinging) return;
    pinging = setInterval(() => {
        if (!navigator.onLine) return;
        fetch("/health", { cache: "no-store", signal: AbortSignal.timeout(5000) }).then(
            (r) => r.ok && reconnected(),
            () => {},
        );
    }, 5000);
}

export function reportNetOk() {
    if (net.trouble) reconnected();
}

window.addEventListener("online", () => {
    net.online = true;
    reconnected();
});
window.addEventListener("offline", () => (net.online = false));

/** Resolves when the browser is back online, or after `ms`, whichever is first. */
export function onlineOrTimeout(ms: number): Promise<void> {
    if (navigator.onLine) return new Promise((r) => setTimeout(r, ms));
    return new Promise((r) => {
        const done = () => {
            window.removeEventListener("online", done);
            clearTimeout(t);
            r();
        };
        const t = setTimeout(done, ms);
        window.addEventListener("online", done);
    });
}
