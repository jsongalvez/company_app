import { ApiError } from "../api/client";
import { RECONNECTED } from "./net.svelte";
import { LANDED, opOf, pendingOps, SENT, tokenUser, type Landed } from "./outbox.svelte";
import type { PendingOp } from "./mutators";
import { disk } from "./disk";
import type { Key } from "./keys";
import { onChanged, Seen, staleKeys, stillStale, type Tables } from "./tables";
/**
 * Tiny query cache, McMaster-style snappiness: show cached data instantly,
 * revalidate in the background, prefetch on hover. Keys are strings.
 */

type Entry = {
    data: unknown;
    at: number;
    inflight?: Promise<unknown>;
    got?: number;
    /** Order of the request that gave `data`, of the one in flight, and the first one that sees the latest save. */
    seq?: number;
    inflightAt?: number;
    inflightSeq?: number;
    staleAt?: number;
    /** Tables the answer read (`X-Reads`); none recorded (seeded, restored from the phone) counts as every table. */
    reads?: Tables;
};
const store = new Map<Key, Entry>();
const FRESH_MS = 15_000;

/**
 * What the screens last showed is kept on the phone too (per person), so the app opens with
 * yesterday's view at once, even with no signal, and refreshes it in the background.
 */
/** IndexedDB holds far more than this; the cap keeps the copy quick to load at open. */
const SAVE_MAX_CHARS = 8_000_000;
const saveKey = () => `qcache:${tokenUser()}`;
let restoredFor: string | null = null;

/** Load what this person's phone saved. Call after sign-in; cheap to call again. */
export function restoreCache() {
    const u = tokenUser();
    if (!u || restoredFor === u) return;
    restoredFor = u;
    try {
        const rows = JSON.parse(disk.get(saveKey()) ?? "[]") as [Key, unknown, number][];
        for (const [k, data, got] of rows) if (!store.has(k)) store.set(k, { data, at: 0, got });
    } catch {
        /* nothing usable saved */
    }
}

let saveTimer: ReturnType<typeof setTimeout> | undefined;
function saveSoon() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(saveNow, 800);
}

/** Write the cache to the phone now: before the phone forgets a copy the cache alone holds from then on. */
export function saveNow() {
    clearTimeout(saveTimer);
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
    disk.set(saveKey(), `[${out.join(",")}]`);
}

let seq = 0;
export function fetchCached<T>(key: Key, fn: (seen: Seen) => Promise<T>, force = false): Promise<T> {
    const e = store.get(key);
    // A forced read (after a save) must not reuse a request that started before the save.
    if (e?.inflight && !(force && e.inflightAt! < (e.staleAt ?? 0))) return e.inflight as Promise<T>;
    if (!force && e && Date.now() - e.at < FRESH_MS) return Promise.resolve(e.data as T);
    const mine = ++seq;
    const seen = new Seen();
    const p = fn(seen).then(
        (data) => {
            const cur = store.get(key);
            // An older request finishing late never overwrites a newer answer.
            if (cur && (cur.seq ?? 0) > mine) return cur.data as T;
            const newer = cur?.inflight && (cur.inflightSeq ?? 0) > mine;
            // Started before the latest save: show it, but it's not fresh.
            const stale = (cur?.staleAt ?? 0) > mine;
            store.set(key, {
                data,
                at: stale ? 0 : Date.now(),
                got: Date.now(),
                seq: mine,
                staleAt: cur?.staleAt,
                reads: seen.tables,
                ...(newer ? { inflight: cur!.inflight, inflightAt: cur!.inflightAt, inflightSeq: cur!.inflightSeq } : {}),
            });
            saveSoon();
            return data;
        },
        (err) => {
            const cur = store.get(key);
            if (cur?.inflightSeq === mine) delete cur.inflight;
            else if (!cur) store.delete(key);
            throw err;
        },
    );
    store.set(key, { ...(e ?? { data: undefined, at: 0 }), inflight: p, inflightAt: mine, inflightSeq: mine });
    return p;
}

/** Keep an answer that came with another request (the start bundle) as if this key had been read now. */
export function seed(key: Key, data: unknown) {
    store.set(key, { data, at: Date.now(), got: Date.now(), seq: ++seq });
    saveSoon();
}

export function peek<T>(key: Key): T | undefined {
    return store.get(key)?.data as T | undefined;
}

function markStale(e: Entry) {
    e.at = 0;
    e.staleAt = seq + 1;
}

/** A save's answer named the tables it changed: every entry whose reads meet them is stale. The next read asks again,
 *  but what we had keeps showing meanwhile, and stays if there's no signal. Pages re-read what they show themselves. */
onChanged((changed) => {
    for (const k of staleKeys(store, changed)) markStale(store.get(k)!);
});

/** The local mutators load on first use: a phone with nothing queued opens Today without their code. */
type Mutators = typeof import("./mutators");
let mut = $state<Mutators | null>(null);
let loading: Promise<Mutators> | null = null;
function mutators(): Promise<Mutators> {
    return (loading ??= import("./mutators").then((m) => (mut = m)));
}

/** A queued save landed with its record: it takes the place of what each cached document held, so a page keeps
 *  showing the change between the save leaving the queue and the next read (lib/mutators.ts). */
async function takeLanded(op: PendingOp, answer: unknown) {
    const m = await mutators();
    let hit = false;
    for (const [k, e] of store) {
        if (e.data === undefined || !m.mutates(k)) continue;
        const data = m.takeAnswer(k, e.data, op, answer);
        if (data !== e.data) ((e.data = data), (hit = true));
    }
    if (hit) saveSoon();
    for (const r of live) r.deref()?.take(m, op, answer);
}
if (typeof window !== "undefined")
    window.addEventListener(LANDED, (e) => {
        const { item, answer } = (e as CustomEvent<Landed>).detail;
        void takeLanded(opOf(item), answer);
    });

/** What a page shows for a key: the cached answer with this person's unsent saves applied. Until the mutators
 *  have loaded it is the answer as read. */
function shownDoc<T>(key: Key, doc: T | undefined): T | undefined {
    const ops = pendingOps();
    if (doc === undefined || !ops.length) return doc;
    if (!mut) {
        void mutators();
        return doc;
    }
    return mut.mutates(key) ? (mut.applyOps(key, doc, ops) as T) : doc;
}

/** Rights or branches changed: any answer may differ. */
export function everythingStale() {
    for (const e of store.values()) markStale(e);
}

/** Drop cached keys starting with any prefix (on the phone too), and reload screens showing them:
 *  for data that is no longer allowed (e.g. Today after clocking out), not merely stale. */
export function forgetShown(prefixes: Key[]) {
    const hit = (k: string) => prefixes.some((p) => k.startsWith(p));
    for (const k of [...store.keys()]) if (hit(k)) store.delete(k);
    saveSoon();
    for (const r of live) {
        const q = r.deref();
        if (q && hit(q.key)) q.refresh();
    }
}

/** Re-read, in place, the screens whose entry a save made stale and nothing has refreshed since. Call it once the
 *  save's answer is taken in: what the answer seeded, or a page re-read itself, is not asked for again. */
export function rereadStale(): Promise<unknown> {
    // Decide before refreshing: once one screen asks again, the key no longer looks stale, yet another screen on
    // the same key (a page opened again before the old one is collected) only gets the answer by asking too.
    // The asks share one request.
    const stale = new Set<Key>();
    for (const [k, e] of store) if (stillStale(e)) stale.add(k);
    const done: Promise<void>[] = [];
    for (const r of live) {
        const q = r.deref();
        if (q && stale.has(q.key)) done.push(q.refresh());
    }
    return Promise.all(done);
}

/** Sign-out: forget everything, on the phone too. */
export function clearCache() {
    if (tokenUser()) disk.del(saveKey());
    store.clear();
    restoredFor = null;
}

/** Reactive query: stale data shows at once; `loading` only when nothing is cached. */
/** Pages showing data, so waiting saves that just went out can refresh what's on screen. */
const live = new Set<WeakRef<Query<unknown>>>();
/** After held saves go out, what's cached is out of date: refresh what's on screen. */
export function refreshOnSent() {
    window.addEventListener(SENT, () => {
        everythingStale();
        for (const r of live) {
            const q = r.deref();
            if (q) q.refresh();
            else live.delete(r);
        }
    });
}

export class Query<T> {
    /** The server's answer as read; `data` is what the page shows (unsent saves applied, [shownDoc]). */
    #key = $state("" as Key);
    #raw = $state<T | undefined>(undefined);
    #shown = $derived(shownDoc(this.#key, this.#raw));
    get data(): T | undefined {
        return this.#shown;
    }
    set data(v: T | undefined) {
        this.#raw = v;
    }
    /** A queued save landed: its answer takes the record's place in what this page read. */
    take(m: Mutators, op: PendingOp, answer: unknown) {
        if (this.#raw === undefined || !m.mutates(this.#key)) return;
        const next = m.takeAnswer(this.#key, this.#raw, op, answer) as T;
        if (next !== this.#raw) this.#raw = next;
    }
    error = $state<string | null>(null);
    loading = $state(false);
    /** No signal and nothing saved for this yet: it loads by itself once the signal is back. */
    waiting = $state(false);
    #fn: (seen: Seen) => Promise<T>;
    get key() {
        return this.#key;
    }

    /** With no args, nothing loads until load() (call it from an $effect). */
    constructor(key?: Key, fn?: (seen: Seen) => Promise<T>) {
        this.#fn = fn ?? (() => Promise.reject(new Error("no loader")));
        live.add(new WeakRef(this as Query<unknown>));
        if (key !== undefined && fn) this.load(key, fn);
        else this.loading = true;
    }

    /** inPlace (a refresh): keep showing what's on screen, data or refusal, until the answer comes. */
    load(key: Key, fn: (seen: Seen) => Promise<T> = this.#fn, force = false, inPlace = false): Promise<void> {
        this.#key = key;
        this.#fn = fn;
        if (!inPlace) {
            const cached = peek<T>(key);
            this.data = cached;
            this.error = null;
            this.waiting = false;
            this.loading = cached === undefined;
        }
        return fetchCached(key, fn, force).then(
            (d) => {
                if (this.#key !== key) return;
                this.data = d;
                this.error = null;
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

    refresh(): Promise<void> {
        if (!this.#key) return Promise.resolve();
        return this.load(this.#key, this.#fn, true, true);
    }
}
