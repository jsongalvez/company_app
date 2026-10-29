/**
 * A phone that keeps the app open for days would keep running the old version. When the app
 * comes back to the front (or the signal returns), ask the server which build is current; if
 * it's newer, offer a one-tap update. Waiting saves are kept on the phone, so reloading is safe.
 */
import { RECONNECTED } from "./net.svelte";
import { BUILD } from "./version";

export const update = $state({ ready: false });

const mine = () => BUILD.exec([...document.scripts].map((s) => s.src).join(" "))?.[0];

export async function checkForUpdate(): Promise<void> {
    const have = mine();
    if (!have || update.ready) return;
    try {
        const res = await fetch("/", { cache: "no-store", signal: AbortSignal.timeout(10_000) });
        if (!res.ok) return;
        const now = BUILD.exec(await res.text())?.[0];
        if (now && now !== have && ((await reloadOpens()) ?? now) === now) update.ready = true;
    } catch {
        /* no signal: try next time */
    }
}

/** The build a reload would open when the service worker serves it: its saved page, which stays the old build until the new build's files are saved. */
async function reloadOpens(): Promise<string | undefined> {
    if (!navigator.serviceWorker?.controller) return undefined;
    const saved = await caches.match("/");
    return saved ? BUILD.exec(await saved.text())?.[0] : undefined;
}

/** Look for a newer build soon after start (the phone opens its saved copy), when the app is shown again, the signal returns, and every 15 minutes. */
export function watchForUpdates() {
    if (!import.meta.env.PROD) return;
    // After start-up settles: sent first, it took the open connection and /api/start waited for a new one.
    setTimeout(() => void checkForUpdate(), 5_000);
    document.addEventListener("visibilitychange", () => document.visibilityState === "visible" && void checkForUpdate());
    window.addEventListener(RECONNECTED, () => void checkForUpdate());
    setInterval(() => void checkForUpdate(), 15 * 60_000);
    // The service worker says when it has saved a build, so the offer shows as soon as Update would open it.
    navigator.serviceWorker?.addEventListener("message", (e: MessageEvent) => {
        if (typeof e.data?.saved === "string" && e.data.saved !== mine()) void checkForUpdate();
    });
}
