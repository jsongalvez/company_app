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

/** Drop cached keys starting with any prefix (after a write). */
export function invalidate(...prefixes: string[]) {
    for (const k of [...store.keys()]) if (prefixes.some((p) => k.startsWith(p))) store.delete(k);
}

export function clearCache() {
    store.clear();
}

/** Reactive query: stale data shows at once; `loading` only when nothing is cached. */
export class Query<T> {
    data = $state<T | undefined>(undefined);
    error = $state<string | null>(null);
    loading = $state(false);
    #key = "";
    #fn: () => Promise<T>;

    /** With no args, nothing loads until load() (call it from an $effect). */
    constructor(key?: string, fn?: () => Promise<T>) {
        this.#fn = fn ?? (() => Promise.reject(new Error("no loader")));
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
            },
        );
    }

    refresh() {
        this.load(this.#key, this.#fn, true);
    }
}
