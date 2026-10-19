import { ApiError } from "../api/client";
import { RECONNECTED } from "./net.svelte";
import { SENT, tokenUser } from "./outbox.svelte";
/**
 * Tiny query cache, McMaster-style snappiness: show cached data instantly,
 * revalidate in the background, prefetch on hover. Keys are strings.
 */

type Entry = { data: unknown; at: number; inflight?: Promise<unknown>; got?: number };
const store = new Map<string, Entry>();
const FRESH_MS = 15_000;

/**
 * What the screens last showed is kept on the phone too (per person), so the app opens with
 * yesterday's view at once, even with no signal, and refreshes it in the background.
 */
const SAVE_MAX_CHARS = 1_500_000;
const saveKey = () => `qcache:${tokenUser()}`;
let restoredFor: string | null = null;

/** Load what this person's phone saved. Call after sign-in; cheap to call again. */
export function restoreCache() {
    const u = tokenUser();
    if (!u || restoredFor === u) return;
    restoredFor = u;
    try {
        const rows = JSON.parse(localStorage.getItem(saveKey()) ?? "[]") as [string, unknown, number][];
        for (const [k, data, got] of rows) if (!store.has(k)) store.set(k, { data, at: 0, got });
    } catch {
        /* nothing usable saved */
    }
}

let saveTimer: ReturnType<typeof setTimeout> | undefined;
function saveSoon() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => {
        if (!tokenUser()) return;
        const rows = [...store]
            .filter(([, e]) => e.data !== undefined)
            .sort((a, b) => (b[1].got ?? 0) - (a[1].got ?? 0))
            .map(([k, e]) => [k, e.data, e.got ?? 0] as [string, unknown, number]);
        // Newest first, until the phone's storage budget is used.
        const out: string[] = [];
        let size = 2;
        for (const r of rows) {
            const j = JSON.stringify(r);
            if (size + j.length > SAVE_MAX_CHARS) continue;
            size += j.length + 1;
            out.push(j);
        }
        try {
            localStorage.setItem(saveKey(), `[${out.join(",")}]`);
        } catch {
            /* storage full: keep it in memory only */
        }
    }, 800);
}

export function fetchCached<T>(key: string, fn: () => Promise<T>, force = false): Promise<T> {
    const e = store.get(key);
    if (e?.inflight) return e.inflight as Promise<T>;
    if (!force && e && Date.now() - e.at < FRESH_MS) return Promise.resolve(e.data as T);
    const p = fn().then(
        (data) => {
            store.set(key, { data, at: Date.now(), got: Date.now() });
            saveSoon();
            return data;
        },
        (err) => {
            const cur = store.get(key);
            if (cur) delete cur.inflight;
            else store.delete(key);
            throw err;
        },
    );
    store.set(key, { data: e?.data, at: e?.at ?? 0, got: e?.got, inflight: p });
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

/** Drop cached keys starting with any prefix (on the phone too), and reload screens showing them:
 *  for data that is no longer allowed (e.g. Today after clocking out), not merely stale. */
export function forget(...prefixes: string[]) {
    const hit = (k: string) => prefixes.some((p) => k.startsWith(p));
    for (const k of [...store.keys()]) if (hit(k)) store.delete(k);
    saveSoon();
    for (const r of live) {
        const q = r.deref();
        if (q && hit(q.key)) q.refresh();
    }
}

/** When the data for a key was last fetched from the server (ms), if we have it. */
export function fetchedAt(key: string): number | undefined {
    return store.get(key)?.got;
}

/** Sign-out: forget everything, on the phone too. */
export function clearCache() {
    if (tokenUser()) localStorage.removeItem(saveKey());
    store.clear();
    restoredFor = null;
}

/** Reactive query: stale data shows at once; `loading` only when nothing is cached. */
/** Pages showing data, so waiting saves that just went out can refresh what's on screen. */
const live = new Set<WeakRef<Query<unknown>>>();
window.addEventListener(SENT, () => {
    for (const e of store.values()) e.at = 0;
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
    /** No signal and nothing saved for this yet: it loads by itself once the signal is back. */
    waiting = $state(false);
    #key = "";
    #fn: () => Promise<T>;
    get key() {
        return this.#key;
    }

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
        this.waiting = false;
        this.loading = cached === undefined;
        fetchCached(key, fn, force).then(
            (d) => {
                if (this.#key !== key) return;
                this.data = d;
                this.loading = false;
                this.waiting = false;
            },
            (e: unknown) => {
                if (this.#key !== key) return;
                this.loading = false;
                const lost = e instanceof ApiError && e.status <= 0;
                // Lost signal: keep showing what we had (the bar at the bottom says why), and load
                // again by itself once the connection is back. Only real refusals show as errors.
                if (lost) {
                    this.waiting = this.data === undefined;
                    window.addEventListener(RECONNECTED, () => this.#key === key && this.refresh(), { once: true });
                } else {
                    // A real refusal (e.g. clocked out): don't keep showing what was saved before,
                    // and don't keep it on the phone either, or it flashes up on the next open.
                    this.data = undefined;
                    if (store.get(key)?.data !== undefined) {
                        store.delete(key);
                        saveSoon();
                    }
                    this.error = e instanceof Error ? e.message : String(e);
                }
            },
        );
    }

    refresh() {
        if (!this.#key) return;
        this.load(this.#key, this.#fn, true);
    }
}
