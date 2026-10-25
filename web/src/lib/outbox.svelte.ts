/**
 * Offline save queue. Front-desk saves made with no signal (new sessions, clients, sales,
 * expenses, clock-ins, marking sessions) wait on this phone and are sent, in order, once the
 * server answers again. Each one carries its own id or the record's version, so sending it
 * twice can't make a duplicate, and a change someone else made in the meantime is refused by
 * the server rather than overwritten: those land in "couldn't send" for the person to check.
 */
import type { S } from "../api/client";
import { label, nowIso } from "./format";
import { classify, parseBody } from "./outbox-classify";
import { replayBody } from "./replay";
import { RECONNECTED, reportNetFailure, reportNetOk } from "./net.svelte";
import { track } from "./track";

export type Item = {
    id: string;
    at: string;
    user: string;
    method: string;
    path: string;
    body: string;
    what: string;
    failed?: string;
    sentAt?: number;
    /** For a mark: the status the server will have once the marks before this one have landed. */
    from?: string;
    /** A request for this save has gone out at least once, so the server may already hold it. */
    tried?: boolean;
};

type Rule = { method: string; path: RegExp; what: (b: Record<string, unknown>) => string; echo?: (b: Record<string, unknown>) => unknown };

const name = (b: Record<string, unknown>) => [b.firstName, b.lastName].filter(Boolean).join(" ");

/** The saves that may wait. Everything else needs the server there and then. */
const RULES: Rule[] = [
    { method: "POST", path: /^\/api\/clients$/, what: (b) => `New client ${name(b)}`, echo: (b) => heldClient(b) },
    { method: "POST", path: /^\/api\/sessions$/, what: () => "New session" },
    { method: "POST", path: /^\/api\/sessions\/[^/]+\/practitioners$/, what: () => "Practitioner on a session" },
    { method: "PATCH", path: /^\/api\/sessions\/[^/]+\/status$/, what: (b) => `Session marked ${label(String(b.status))}` },
    { method: "POST", path: /^\/api\/product-sales$/, what: () => "Product sale" },
    { method: "POST", path: /^\/api\/expenses$/, what: (b) => `Expense ${String(b.amount ?? "")}`.trim() },
    { method: "POST", path: /^\/api\/allowances$/, what: (b) => `Allowance ${String(b.amount ?? "")}`.trim() },
    { method: "POST", path: /^\/api\/attendance\/clock-in$/, what: () => "Clock in" },
    { method: "POST", path: /^\/api\/attendance\/clock-out$/, what: () => "Clock out" },
    { method: "POST", path: /^\/api\/branches\/[^/]+\/attendance\/marks$/, what: (b) => (b.present ? "Marked present" : "Marked absent") },
];

const KEY = "outbox";
export const SENT = "app:outbox-sent";

function load(): Item[] {
    try {
        const v = JSON.parse(localStorage.getItem(KEY) ?? "[]") as Item[];
        return Array.isArray(v) ? v : [];
    } catch {
        return [];
    }
}

/**
 * A create that has gone out stays in the queue, marked `sentAt`, until the server's own list shows
 * it: a session for up to a day (a weak signal can leave the list stale for minutes), a client for
 * two minutes. It is no longer waiting, but pages still show it, so it never drops off the screen
 * between the send and the next list. The server's list always wins over it.
 */
const KEEP_CLIENT_MS = 120_000;
const KEEP_SESSION_MS = 24 * 3600_000;
const keepsAfterSend = (i: Item) => i.method === "POST" && (i.path === "/api/sessions" || i.path === "/api/clients");
const stillShown = (i: Item) => i.sentAt === undefined || i.sentAt + (i.path === "/api/sessions" ? KEEP_SESSION_MS : KEEP_CLIENT_MS) > Date.now();

localStorage.removeItem("outbox-recent");
export const outbox = $state({ items: load().filter(stillShown), sending: false });

/** The server's list now shows these sessions: stop holding them up ourselves. */
export function sessionsSeen(ids: Set<string>) {
    const left = outbox.items.filter((i) => i.sentAt === undefined || i.path !== "/api/sessions" || !ids.has(String(parseBody(i.body).id)));
    if (left.length === outbox.items.length) return;
    outbox.items = left;
    save();
}

function save() {
    localStorage.setItem(KEY, JSON.stringify(outbox.items));
}

/** Who the stored token belongs to (JWT subject), so one person's queue never sends as another. */
export function tokenUser(): string {
    const t = localStorage.getItem("token");
    if (!t) return "";
    try {
        return (JSON.parse(atob(t.split(".")[1]!.replace(/-/g, "+").replace(/_/g, "/"))) as { sub?: string }).sub ?? "";
    } catch {
        return "";
    }
}

/** This person's queue. */
export function mine(): Item[] {
    const u = tokenUser();
    return outbox.items.filter((i) => i.user === u);
}
export const waiting = () => mine().filter((i) => !i.failed && i.sentAt === undefined);
export const failed = () => mine().filter((i) => !!i.failed);

/** This person's saves to a path that the server's list may not show yet: waiting, or just sent. */
function pendingTo(method: string, path: RegExp): { item: Item; body: Record<string, unknown> }[] {
    return mine()
        .filter((i) => !i.failed && stillShown(i) && i.method === method && path.test(i.path))
        .map((item) => ({ item, body: parseBody(item.body) }));
}
const waitingBodies = (method: string, path: RegExp) => pendingTo(method, path).filter((x) => x.item.sentAt === undefined);

/** A client made with no signal, shaped like the server's answer. */
function heldClient(b: Record<string, unknown>): S["ClientResponse"] {
    return {
        id: String(b.id),
        firstName: (b.firstName as string) ?? null,
        lastName: (b.lastName as string) ?? null,
        middleName: (b.middleName as string) ?? null,
        suffix: (b.suffix as string) ?? null,
        phoneNumber: (b.phoneNumber as string) ?? null,
        address: null,
        gender: (b.gender as S["ClientResponse"]["gender"]) ?? "UNKNOWN",
        age: Number(b.age ?? 0),
        systolicBp: null,
        diastolicBp: null,
        medicalConditions: null,
        sessionCount: 0,
    };
}

/** Clients made on this phone that the saved client list may not have yet (waiting, or just sent). */
export function pendingClients(): S["ClientResponse"][] {
    return pendingTo("POST", /^\/api\/clients$/).map((x) => heldClient(x.body));
}

/** New sessions made on this phone that the server's list may not show yet; `unsent` while still waiting. */
export function pendingSessions(): { at: string; body: Record<string, unknown>; unsent: boolean }[] {
    return pendingTo("POST", /^\/api\/sessions$/).map((x) => ({ at: x.item.at, body: x.body, unsent: x.item.sentAt === undefined }));
}

/** A session's mark still waiting to send, so lists can show it straight away. */
export function heldMark(sessionId: string): string | undefined {
    const m = waitingBodies("PATCH", /^\/api\/sessions\/[^/]+\/status$/).filter((x) => x.item.path === `/api/sessions/${sessionId}/status`);
    return m.length ? String(m[m.length - 1]!.body.status) : undefined;
}

/** Was the last waiting clock action a clock-in (its body), a clock-out ("out"), or none? */
export function heldClock(): { attendanceId: string; branchId: string } | "out" | undefined {
    const c = waiting().filter((i) => /^\/api\/attendance\/clock-(in|out)$/.test(i.path));
    const last = c[c.length - 1];
    if (!last) return undefined;
    if (last.path.endsWith("clock-out")) return "out";
    const b = parseBody(last.body);
    return typeof b.attendanceId === "string" && typeof b.branchId === "string" ? { attendanceId: b.attendanceId, branchId: b.branchId } : undefined;
}

function rule(method: string, path: string): Rule | undefined {
    return RULES.find((r) => r.method === method && r.path.test(path));
}

/** May this request wait for signal? (Not while viewing as someone else.) */
export function canQueue(method: string, path: string): boolean {
    return !!rule(method, path) && !sessionStorage.getItem("viewAs") && !!tokenUser();
}

/** Saves already waiting must go first, so later ones queue behind them (a session after its new client). */
export function mustQueue(method: string, path: string): boolean {
    return canQueue(method, path) && waiting().length > 0;
}

/** Store a save for later; answer the page as if it went through (the record's own fields). */
export function enqueue(method: string, path: string, body: string, shown?: string): Response {
    const r = rule(method, path)!;
    const b = parseBody(body);
    let from: string | undefined;
    let drop = false;
    if (method === "PATCH") {
        // Marking the same session again before the first mark has gone out: only the last one counts.
        // A mark that already went out once may be on the server, so it stays and this one follows it.
        const mine = (i: Item) => i.method === method && i.path === path && !i.failed;
        const replaced = outbox.items.filter((i) => mine(i) && !i.tried);
        const sentOnce = outbox.items.filter((i) => mine(i) && i.tried);
        // What the server will hold before this mark: the replaced mark's own starting point, else the
        // last mark already sent, else what the person was looking at.
        from = replaced[0]?.from ?? (sentOnce.length ? bodyStatus(sentOnce[sentOnce.length - 1]!) : undefined) ?? shown;
        // Back to where it started: nothing to send (the server refuses Waiting -> Waiting).
        drop = from !== undefined && from === String(b.status);
        outbox.items = outbox.items.filter((i) => !(mine(i) && !i.tried));
    }
    if (!drop)
        outbox.items.push({
            id: crypto.getRandomValues(new Uint32Array(2)).join("-"),
            at: nowIso(),
            user: tokenUser(),
            method,
            path,
            body,
            what: r.what(b),
            ...(from !== undefined ? { from } : {}),
        });
    save();
    queuedCount++;
    setTimeout(() => void flush(), 0);
    return new Response(JSON.stringify(r.echo ? r.echo(b) : b), { status: 202, headers: { "Content-Type": "application/json", "X-Queued": "1" } });
}

function bodyStatus(i: Item): string | undefined {
    const s = parseBody(i.body).status;
    return typeof s === "string" ? s : undefined;
}

let queuedCount = 0;
/** Bumped on every save that was held; act()/forms compare it to say "saved on this phone". */
export const heldSoFar = () => queuedCount;

export const HELD = "Saved on this phone. It will send by itself when the signal is back.";

/** The toast for a finished save: the usual one, or "saved on this phone" if it had to wait. */
export function doneText(before: number, done?: string): string | undefined {
    return heldSoFar() > before ? HELD : done;
}

export function discard(id: string) {
    outbox.items = outbox.items.filter((i) => i.id !== id);
    save();
}

async function send(i: Item): Promise<Response> {
    return fetch(i.path, {
        method: i.method,
        body: replayBody(i) || undefined,
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${localStorage.getItem("token") ?? ""}` },
        signal: AbortSignal.timeout(20_000),
    });
}

/** Marks queued behind one that has just landed were made from the version it was made from: move them up. */
function chainVersions(landed: Item, newVersion: number | undefined) {
    const from = parseBody(landed.body).version;
    if (typeof from !== "number") return;
    for (const x of outbox.items) {
        if (x.id === landed.id || x.method !== landed.method || x.path !== landed.path || x.failed) continue;
        const o = parseBody(x.body);
        if (o.version === from) x.body = JSON.stringify({ ...o, version: newVersion ?? from + 1 });
    }
}

/** The answer's JSON, or null when it has none. */
async function readBody(res: Response): Promise<unknown> {
    try {
        return await res.json();
    } catch {
        return null;
    }
}

/** Send what's waiting, oldest first. Stops at the first sign the server can't be reached. */
export async function flush(): Promise<void> {
    if (outbox.sending) return;
    outbox.sending = true;
    let sent = 0;
    let finished = false;
    try {
        for (const i of waiting()) {
            // The person replaced this one while earlier saves were going out: don't send the old one.
            const live = outbox.items.find((x) => x.id === i.id);
            if (!live || live.failed) continue;
            // A mark made after one the server refused would only be refused too (it assumed that one landed).
            if (live.method === "PATCH" && outbox.items.some((x) => x.failed && x.method === live.method && x.path === live.path)) continue;
            live.tried = true;
            save();
            let res: Response;
            try {
                res = await send(live);
            } catch {
                reportNetFailure();
                return;
            }
            const body = await readBody(res);
            const verdict = classify(res.status, body);
            if (verdict.kind === "retry") return;
            reportNetOk();
            if (verdict.kind === "applied") {
                if (i.method === "PATCH") {
                    const v = (body as { version?: unknown } | null)?.version;
                    chainVersions(i, typeof v === "number" ? v : undefined);
                }
                if (keepsAfterSend(i)) live.sentAt = Date.now();
                else outbox.items = outbox.items.filter((x) => x.id !== i.id);
                save();
                sent++;
                continue;
            }
            const cur = outbox.items.find((x) => x.id === i.id);
            if (cur) cur.failed = verdict.message;
            track.captureOutboxFailure({ method: live.method, path: live.path, status: res.status });
            save();
        }
        finished = true;
    } finally {
        outbox.sending = false;
        if (sent) window.dispatchEvent(new Event(SENT));
        // Saves made while this round was going out are next (only if the round got to its end).
        if (finished && waiting().some((x) => !x.tried)) setTimeout(() => void flush(), 0);
    }
}

/** Send what's waiting when the signal is back, every 30 s, and once just after the app opens. */
export function startOutbox() {
    window.addEventListener(RECONNECTED, () => void flush());
    window.addEventListener("online", () => void flush());
    setInterval(() => waiting().length && navigator.onLine && void flush(), 30_000);
    setTimeout(() => void flush(), 1000);
}
