import { ApiError } from "../api/client";
import { RECONNECTED } from "./net.svelte";
import { SENT } from "./outbox.svelte";
/**
 * Tiny query cache, McMaster-style snappiness: show cached data instantly,
 * revalidate in the background, prefetch on hover. Keys are strings.
 */

type Entry = { data: unknown; at: number; inflight?: Promise<unknown> };
const store = new Map<string, Entry>();
const FRESH_MS = 15_000;

export function fetchCached<T>(key: string, fn: () => Promise<T>, force = false): Promise<T> {
    const e = store.get(key);
    if (e?.inflight) return e.inflight as Promise<T>;
    if (!force && e && Date.now() - e.at < FRESH_MS) return Promise.resolve(e.data as T);
    const p = fn().then(
        (data) => {
            store.set(key, { data, at: Date.now() });
            return data;
        },
        (err) => {
            const cur = store.get(key);
            if (cur) delete cur.inflight;
            else store.delete(key);
            throw err;
        },
    );
    store.set(key, { data: e?.data, at: e?.at ?? 0, inflight: p });
    return p;
}

export function peek<T>(key: string): T | undefined {
    return store.get(key)?.data as T | undefined;
}

/** Mark cached keys starting with any prefix as stale (after a write): the next read fetches,
 *  but what we had keeps showing meanwhile, and stays if there's no signal. */
export function invalidate(...prefixes: string[]) {
    for (const [k, e] of store) if (prefixes.some((p) => k.startsWith(p))) e.at = 0;
}

export function clearCache() {
    store.clear();
}

/** Reactive query: stale data shows at once; `loading` only when nothing is cached. */
/** Pages showing data, so waiting saves that just went out can refresh what's on screen. */
const live = new Set<WeakRef<Query<unknown>>>();
window.addEventListener(SENT, () => {
    store.clear();
    for (const r of live) {
        const q = r.deref();
        if (q) q.refresh();
        else live.delete(r);
    }
});

export class Query<T> {
    data = $state<T | undefined>(undefined);
    error = $state<string | null>(null);
    loading = $state(false);
    #key = "";
    #fn: () => Promise<T>;

    /** With no args, nothing loads until load() (call it from an $effect). */
    constructor(key?: string, fn?: () => Promise<T>) {
        this.#fn = fn ?? (() => Promise.reject(new Error("no loader")));
        live.add(new WeakRef(this as Query<unknown>));
        if (key !== undefined && fn) this.load(key, fn);
        else this.loading = true;
    }

    load(key: string, fn: () => Promise<T> = this.#fn, force = false) {
        this.#key = key;
        this.#fn = fn;
        const cached = peek<T>(key);
        this.data = cached;
        this.error = null;
        this.loading = cached === undefined;
        fetchCached(key, fn, force).then(
            (d) => {
                if (this.#key !== key) return;
                this.data = d;
                this.loading = false;
            },
            (e: unknown) => {
                if (this.#key !== key) return;
                this.error = e instanceof Error ? e.message : String(e);
                this.loading = false;
                // Lost signal: load again by itself once the connection is back.
                if (e instanceof ApiError && e.status <= 0) window.addEventListener(RECONNECTED, () => this.#key === key && this.refresh(), { once: true });
            },
        );
    }

    refresh() {
        if (!this.#key) return;
        this.load(this.#key, this.#fn, true);
    }
}
