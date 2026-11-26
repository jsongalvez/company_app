/**
 * What goes stale after a save, from the server's own word: each signed-in answer names the tables its
 * request read (`X-Reads`) and changed (`X-Changed`), sorted and comma-separated, empty for none.
 * A missing header means the server didn't say, so it counts as every table (`*`).
 */
export const READS = "X-Reads";
export const CHANGED = "X-Changed";
/** An answer made on the phone (a save held in the queue, one refused for want of signal): nothing reached the server. */
export const QUEUED = "X-Queued";
export const NOT_SENT = "X-Not-Sent";

export type Tables = ReadonlySet<string> | "*";

export function tablesOf(header: string | null): Tables {
    if (header === null) return "*";
    const names = header
        .split(",")
        .map((s) => s.trim())
        .filter(Boolean);
    return names.includes("*") ? "*" : new Set(names);
}

/** A read meets a change when they share a table; `*` meets any change, and an empty side meets nothing. */
export function meets(reads: Tables, changed: Tables): boolean {
    const empty = (t: Tables) => t !== "*" && t.size === 0;
    if (empty(reads) || empty(changed)) return false;
    if (reads === "*" || changed === "*") return true;
    for (const t of reads) if (changed.has(t)) return true;
    return false;
}

/** The tables one loader's requests read. A loader that recorded none (or a seeded or restored answer) read `*`. */
export class Seen {
    #tables: Set<string> | "*" | null = null;
    add(t: Tables) {
        if (this.#tables === "*") return;
        if (t === "*") this.#tables = "*";
        else this.#tables = new Set([...(this.#tables ?? []), ...t]);
    }
    get tables(): Tables {
        return this.#tables ?? "*";
    }
}

/**
 * What a save's answer changed. Reads are left out: a GET changes only the day row its first read makes
 * (backend `RouteAccessTest`), and its own answer shows that row. An answer made on the phone changed nothing yet;
 * the queue reports the real answer when the save goes out.
 */
export function changesOf(method: string, headers: Headers): Tables {
    if (method === "GET" || method === "HEAD" || headers.has(QUEUED) || headers.has(NOT_SENT)) return new Set();
    return tablesOf(headers.get(CHANGED));
}

export type Cached = { reads?: Tables; seq?: number; staleAt?: number; inflight?: unknown; inflightSeq?: number };

/** Keys whose reads meet the change. An entry with no reads recorded counts as `*`. */
export function staleKeys<K>(entries: Iterable<[K, Cached]>, changed: Tables): K[] {
    const out: K[] = [];
    for (const [k, e] of entries) if (meets(e.reads ?? "*", changed)) out.push(k);
    return out;
}

/** Marked stale and not yet refreshed: no answer or request since the save. A seed, or a page's own reread, counts as refreshed. */
export function stillStale(e: Cached): boolean {
    const at = e.staleAt ?? 0;
    if (at <= (e.seq ?? 0)) return false;
    return !(e.inflight && (e.inflightSeq ?? 0) >= at);
}

type Listener = (changed: Tables) => void;
let listener: Listener = () => {};
/** The cache listens; the API client and the queue's sender report each answer. */
export function onChanged(fn: Listener) {
    listener = fn;
}
export function answered(method: string, headers: Headers) {
    const c = changesOf(method, headers);
    if (c === "*" || c.size) listener(c);
}
