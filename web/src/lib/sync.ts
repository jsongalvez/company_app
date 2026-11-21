import { watchSession } from "./app.svelte";
import { refreshOnSent } from "./cache.svelte";
import { keepDirectoryFresh } from "./clientdir.svelte";
import { watchConnection } from "./net.svelte";
import { startOutbox } from "./outbox.svelte";
import { watchLanded } from "./today.svelte";
import { watchForUpdates } from "./update.svelte";

let started = false;

/** Everything that runs by itself in the background: the connection, held saves, caches, updates. Call once. */
export function startBackgroundSync() {
    if (started) return;
    started = true;
    watchConnection();
    refreshOnSent();
    watchLanded();
    startOutbox();
    keepDirectoryFresh();
    watchSession();
    watchForUpdates();
    // Its own chunk, fetched once the first screen is up (first-open.e2e.ts counts what loads before).
    setTimeout(() => void import("./site.svelte").then((m) => m.startSite()), 3_000);
}
