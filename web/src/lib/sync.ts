import { watchSession } from "./app.svelte";
import { refreshOnSent } from "./cache.svelte";
import { keepDirectoryFresh } from "./clientdir.svelte";
import { watchConnection } from "./net.svelte";
import { startOutbox } from "./outbox.svelte";
import { watchForUpdates } from "./update.svelte";

let started = false;

/** Everything that runs by itself in the background: the connection, held saves, caches, updates. Call once. */
export function startBackgroundSync() {
    if (started) return;
    started = true;
    watchConnection();
    refreshOnSent();
    startOutbox();
    keepDirectoryFresh();
    watchSession();
    watchForUpdates();
}
