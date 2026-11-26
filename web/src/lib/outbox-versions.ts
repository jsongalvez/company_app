/**
 * How queued saves keep the record versions they carry in step with the server while they wait.
 *
 * A save carries the version of the record as the page showed it (`version`, or `expectedVersion`),
 * and the server refuses it if the record has moved on since. The page shows the person's waiting
 * saves applied (lib/mutators.ts), and some of those move the version on as the server will, some
 * can't: so a save queued behind another on the same record was made either from the version that
 * one was made from, or from a later one. Every save that moves a record's version on carries the
 * version it was made from, and each moves it by one, so when one lands the saves still carrying
 * its starting version are the ones that didn't see it: only those move up. A save made from a
 * later version saw it already and is left alone, so nothing is moved on twice.
 */
import { parseBody } from "./outbox-classify";

export type Queued = { id: string; method: string; path: string; body: string; failed?: string };

/**
 * The record a save changes, whose version it carries: `/api/sessions/<id>` for every save under it
 * (a price, a mark and a practitioner share one version line), and a branch's stock card or booking
 * by its own id: `/api/branches/<b>/inventory/<product>`.
 */
export const recordOf = (path: string) =>
    path
        .split("/")
        .slice(0, path.startsWith("/api/branches/") ? 6 : 4)
        .join("/");

/** A save that makes its record (its path is the collection). */
export const isCreate = (i: Queued) => i.method === "POST" && recordOf(i.path) === i.path;

const FIELDS = ["version", "expectedVersion"] as const;
/** The field holding the record version a save was made from, and that version. */
function versionOf(body: Record<string, unknown>): [field: string, version: number] | undefined {
    for (const f of FIELDS) if (typeof body[f] === "number") return [f, body[f] as number];
    return undefined;
}

/** Each waiting save on `rec` still made from version `from` now goes from `to`. */
function move(items: Queued[], skip: string, rec: string, from: number, to: number) {
    for (const x of items) {
        if (x.id === skip || recordOf(x.path) !== rec || x.failed) continue;
        const o = parseBody(x.body);
        const v = versionOf(o);
        if (v && v[1] === from) x.body = JSON.stringify({ ...o, [v[0]]: to });
    }
}

/**
 * `i` is on the server, which answered with record version `answered` if its answer is the record.
 * A record made on this phone: saves queued on it were made from version 0, the server's first is the answer's.
 */
export function landed(items: Queued[], i: Queued, answered: number | undefined) {
    const o = parseBody(i.body);
    if (isCreate(i)) {
        if (typeof o.id === "string" && typeof answered === "number") move(items, i.id, `${i.path}/${o.id}`, 0, answered);
        return;
    }
    const v = versionOf(o);
    if (v) move(items, i.id, recordOf(i.path), v[1], answered ?? v[1] + 1);
}

const versioned = (i: Queued) => versionOf(parseBody(i.body)) !== undefined;
const refusedOn = (items: Queued[], rec: string) => items.some((x) => x.failed && versioned(x) && recordOf(x.path) === rec);

/**
 * A save on a record behind one the server refused waits: it was made as if that one landed, so it
 * would be refused too, or, if someone else has moved the record on, land on their change unseen.
 */
export function heldBehindRefused(items: Queued[], live: Queued): boolean {
    return versioned(live) && refusedOn(items, recordOf(live.path));
}

export const MADE_ON_DROPPED = "Not sent: it was made on a change that was dropped. Check the record and save it again.";

/**
 * The person dropped a refused save. Saves waiting behind it on the same record were made as if it
 * landed: they are refused here too, for the person to check, rather than sent over what the server holds.
 */
export function discarded<T extends Queued>(items: T[], id: string): T[] {
    const gone = items.find((i) => i.id === id);
    const left = items.filter((i) => i.id !== id);
    if (!gone?.failed || !versioned(gone)) return left;
    const rec = recordOf(gone.path);
    if (refusedOn(left, rec)) return left;
    return left.map((i) => (!i.failed && versioned(i) && recordOf(i.path) === rec ? { ...i, failed: MADE_ON_DROPPED } : i));
}
