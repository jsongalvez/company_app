import { watchSession } from "./app.svelte";
import { keepCacheFresh } from "./cache.svelte";
import { keepDirectoryFresh } from "./clientdir.svelte";
import { watchConnection } from "./net.svelte";
import { startOutbox } from "./outbox.svelte";
import { watchLanded } from "./today.svelte";
import { watchForUpdates } from "./update.svelte";
import { wiring, type Stop } from "./wiring";

let running: Stop | null = null;

/** Everything that runs by itself in the background: the connection, held saves, caches, updates. A second call
 *  while it runs does nothing; the stop handle ends all of it, so it can start again. */
export function startBackgroundSync(): Stop {
    if (running) return running;
    const w = wiring();
    let stopped = false;
    w.also(watchConnection());
    w.also(keepCacheFresh());
    w.also(watchLanded());
    w.also(startOutbox());
    w.also(keepDirectoryFresh());
    w.also(watchSession());
    w.also(watchForUpdates());
    // Its own chunk, fetched once the first screen is up (first-open.e2e.ts counts what loads before).
    w.after(3_000, () => void import("./site.svelte").then((m) => !stopped && w.also(m.startSite())));
    const stop: Stop = () => {
        if (stopped) return;
        stopped = true;
        w.stop();
        running = null;
    };
    running = stop;
    return stop;
}
