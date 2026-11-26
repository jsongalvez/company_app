/**
 * Offline save queue. Front-desk saves made with no signal (new sessions, clients, sales,
 * expenses, clock-ins, marking sessions) wait on this phone and are sent, in order, once the
 * server answers again. Each one carries its own id or the record's version, so sending it
 * twice can't make a duplicate, and a change someone else made in the meantime is refused by
 * the server rather than overwritten: those land in "couldn't send" for the person to check.
 */
import { refreshToken, tokenExpired, type S } from "../api/client";
import { label, nowIso } from "./format";
import { classify, parseBody } from "./outbox-classify";
import { disk } from "./disk";
import { replayBody } from "./replay";
import { discarded, heldBehindRefused, landed } from "./outbox-versions";
import { RECONNECTED, reportNetFailure, reportNetOk } from "./net.svelte";
import { traceparent } from "./telemetry";
import { answered, QUEUED } from "./tables";
import type { PendingOp } from "./mutators";

export type Item = {
    id: string;
    at: string;
    user: string;
    method: string;
    path: string;
    /** The request's query (no "?"), sent with it: `withToday=true` brings Today back with the save. */
    query?: string;
    body: string;
    what: string;
    failed?: string;
    sentAt?: number;
    /** For a mark: the status the server will have once the marks before this one have landed. */
    from?: string;
    /** A request for this save has gone out at least once, so the server may already hold it. */
    tried?: boolean;
    /** Made with good signal: shown as going out, not as waiting, and its answer alone updates Today. */
    eager?: true;
};

type Rule = {
    method: string;
    path: RegExp;
    what: (b: Record<string, unknown>) => string;
    echo?: (b: Record<string, unknown>) => unknown;
    /** Always goes through the queue, signal or not: the page shows it at once and the server answers behind it. */
    eager?: true;
};

const name = (b: Record<string, unknown>) => [b.firstName, b.lastName].filter(Boolean).join(" ");

/**
 * Saves with their own words and page behaviour. Any other save may wait too (see [ONLINE_ONLY]):
 * it shows in the waiting list under [LABELS]' words and the page hears back the record it sent.
 */
const RULES: Rule[] = [
    { method: "POST", path: /^\/api\/clients$/, what: (b) => `New client ${name(b)}`, echo: (b) => heldClient(b) },
    { method: "POST", path: /^\/api\/sessions$/, what: () => "New session", eager: true },
    { method: "POST", path: /^\/api\/sessions\/[^/]+\/practitioners$/, what: () => "Practitioner on a session" },
    { method: "PATCH", path: /^\/api\/sessions\/[^/]+\/status$/, what: (b) => `Session marked ${label(String(b.status))}` },
    { method: "POST", path: /^\/api\/product-sales$/, what: () => "Product sale", eager: true },
    { method: "POST", path: /^\/api\/expenses$/, what: (b) => `Expense ${String(b.amount ?? "")}`.trim() },
    { method: "POST", path: /^\/api\/allowances$/, what: (b) => `Allowance ${String(b.amount ?? "")}`.trim() },
    { method: "POST", path: /^\/api\/attendance\/clock-in$/, what: () => "Clock in" },
    { method: "POST", path: /^\/api\/attendance\/clock-out$/, what: () => "Clock out" },
    { method: "POST", path: /^\/api\/branches\/[^/]+\/attendance\/marks$/, what: (b) => (b.present ? "Marked present" : "Marked absent") },
];

/** What the waiting list calls the other day-work saves. */
const LABELS: [string, RegExp, string][] = [
    ["PATCH", /^\/api\/sessions\/[^/]+\/final-price$/, "Session price"],
    ["POST", /^\/api\/sessions\/[^/]+\/concerns$/, "Illness on a session"],
    ["DELETE", /^\/api\/sessions\/[^/]+\/concerns\/[^/]+$/, "Illness removed"],
    ["DELETE", /^\/api\/concerns\/[^/]+$/, "Illness removed"],
    ["POST", /^\/api\/sessions\/[^/]+\/promote-concern$/, "Illness made main"],
    ["POST", /^\/api\/sessions\/[^/]+\/void$/, "Session voided"],
    ["POST", /^\/api\/sessions\/[^/]+\/unvoid$/, "Session restored"],
    ["PATCH", /^\/api\/sessions\/[^/]+\/practitioners\/[^/]+$/, "Practitioner changed"],
    ["DELETE", /^\/api\/sessions\/[^/]+\/practitioners\/[^/]+$/, "Practitioner removed"],
    ["POST", /^\/api\/branches\/[^/]+\/bookings$/, "New booking"],
    ["PATCH", /^\/api\/branches\/[^/]+\/bookings\/[^/]+$/, "Booking changed"],
    ["POST", /^\/api\/branches\/[^/]+\/inventory\/[^/]+\/restock$/, "Restock"],
    ["POST", /^\/api\/branches\/[^/]+\/inventory\/[^/]+\/movement$/, "Stock moved"],
    ["POST", /^\/api\/compensation$/, "Pay"],
    ["PATCH", /^\/api\/compensation\/[^/]+$/, "Pay changed"],
    ["PATCH", /^\/api\/clients\/[^/]+$/, "Client details"],
    ["PATCH", /^\/api\/expenses\/[^/]+$/, "Expense changed"],
    ["DELETE", /^\/api\/expenses\/[^/]+$/, "Expense removed"],
    ["POST", /^\/api\/expenses\/[^/]+\/restore$/, "Expense restored"],
    ["PATCH", /^\/api\/product-sales\/[^/]+$/, "Sale changed"],
    ["DELETE", /^\/api\/product-sales\/[^/]+$/, "Sale removed"],
    ["POST", /^\/api\/product-sales\/[^/]+\/restore$/, "Sale restored"],
    ["POST", /^\/api\/commission-inclusions$/, "Commission share"],
    ["POST", /^\/api\/feedback$/, "Problem report"],
];

/**
 * Saves that need the server there and then. A remittance freezes the day's totals, so it must see
 * every save first; signing in, passwords, roles and wiping a client change who may do what, so
 * they must take effect at once or say plainly that they didn't.
 */
const ONLINE_ONLY = [
    /^\/api\/auth\//,
    /^\/api\/me\/password$/,
    /^\/api\/view-as/,
    /^\/api\/remittances/,
    /^\/api\/users\//,
    /^\/api\/clients\/[^/]+\/anonymize$/,
    /^\/api\/telemetry$/,
    /^\/api\/feedback-notes/,
];

const WRITES = new Set(["POST", "PUT", "PATCH", "DELETE"]);

const KEY = "outbox";
/** Request headers every queued save carries (server `OpTime.HEADER`, `OpLog.HEADER`). */
export const OCCURRED_AT = "X-Occurred-At";
export const OP_ID = "X-Op-Id";
export const SENT = "app:outbox-sent";
/** One save the server took: `detail` is { item, answer }. Today takes the answer in (lib/today.svelte.ts). */
export const LANDED = "app:outbox-landed";
export type Landed = { item: Item; answer: unknown };
/** A queued save the server refused on replay: `detail` is { method, path, status }. The recorder logs it. */
export const REFUSED = "app:outbox-refused";
export type Refused = { method: string; path: string; status: number };

/** Run `fn` once the save carrying record `id` reaches the server. Returns how to stop waiting (a closing page). */
export function whenLanded(id: string, fn: () => void): () => void {
    const on = (e: Event) => {
        if (parseBody((e as CustomEvent<Landed>).detail.item.body).id !== id) return;
        stop();
        fn();
    };
    const stop = () => window.removeEventListener(LANDED, on);
    window.addEventListener(LANDED, on);
    return stop;
}

function load(): Item[] {
    try {
        const v = JSON.parse(disk.get(KEY) ?? "[]") as Item[];
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
/** `keepList` writes the list that shows them to the phone first, so a reload right after still has them. */
export function sessionsSeen(ids: Set<string>, keepList: () => void) {
    const left = outbox.items.filter((i) => i.sentAt === undefined || i.path !== "/api/sessions" || !ids.has(String(parseBody(i.body).id)));
    if (left.length === outbox.items.length) return;
    keepList();
    outbox.items = left;
    save();
}

function save() {
    disk.set(KEY, JSON.stringify(outbox.items));
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

/** A queued save as the local mutators read it (lib/mutators.ts). */
export const opOf = (i: Item): PendingOp => ({ method: i.method, path: i.path, body: parseBody(i.body), at: i.at, user: i.user });

/** This person's saves the server may not hold yet, oldest first: pages show them applied to what they read. */
export function pendingOps(): PendingOp[] {
    if (!outbox.items.length) return [];
    return mine()
        .filter((i) => !i.failed && i.sentAt === undefined)
        .map(opOf);
}

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
        gender: b.gender as S["ClientResponse"]["gender"],
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

/** Where a save made on this phone stands: going out now, waiting for signal, refused by the server, or taken. */
export type Standing = "sending" | "waiting" | "failed" | "sent";
function standing(i: Item): Standing {
    if (i.failed) return "failed";
    if (i.sentAt !== undefined) return "sent";
    return i.eager && (!i.tried || outbox.sending) ? "sending" : "waiting";
}

/** New sessions made on this phone that the server's list may not show yet, refused ones included. */
export function pendingSessions(): { id: string; at: string; body: Record<string, unknown>; state: Standing; failed?: string }[] {
    return mine()
        .filter((i) => (i.failed || stillShown(i)) && i.method === "POST" && i.path === "/api/sessions")
        .map((i) => ({ id: i.id, at: i.at, body: parseBody(i.body), state: standing(i), failed: i.failed }));
}

/** Product sales made on this phone that the server hasn't taken yet, for a branch day. */
export function pendingSales(branchDayId: string | undefined): number {
    if (!branchDayId) return 0;
    return pendingTo("POST", /^\/api\/product-sales$/).filter((x) => x.body.branchDayId === branchDayId).length;
}

/** Each session's latest mark still waiting to send, by session id, so lists can show it straight away. */
export function heldMarks(): Map<string, string> {
    const marks = new Map<string, string>();
    for (const x of waitingBodies("PATCH", /^\/api\/sessions\/[^/]+\/status$/)) marks.set(x.item.path.split("/")[3]!, String(x.body.status));
    return marks;
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

/** May this save wait on the phone at all? */
export function mayWait(method: string, path: string): boolean {
    return WRITES.has(method) && !ONLINE_ONLY.some((r) => r.test(path));
}

function rule(method: string, path: string): Rule | undefined {
    const own = RULES.find((r) => r.method === method && r.path.test(path));
    if (own || !mayWait(method, path)) return own;
    const named = LABELS.find(([m, p]) => m === method && p.test(path))?.[2] ?? "Change";
    return { method, path: /./, what: () => named };
}

/** Does this save always go through the queue (the page shows it before the server answers)? */
export function eager(method: string, path: string): boolean {
    return canQueue(method, path) && !!rule(method, path)?.eager;
}

/** Only saves that go out now are ahead in the queue: a save lined up behind them isn't waiting for signal. */
export const onlyEagerAhead = () => waiting().every((i) => i.eager);

/** May this request wait for signal? (Not while viewing as someone else.) */
export function canQueue(method: string, path: string): boolean {
    return !!rule(method, path) && !sessionStorage.getItem("viewAs") && !!tokenUser();
}

/** Saves already waiting must go first, so later ones queue behind them (a session after its new client). */
export function mustQueue(method: string, path: string): boolean {
    return canQueue(method, path) && waiting().length > 0;
}

/**
 * Store a save for later; answer the page as if it went through (the record's own fields).
 * `held` is false for an eager save made with good signal: it goes out now, so the page says "added", not "saved on this phone".
 */
/** A save's own id and the time it was made, given once when the person acts. */
export type Op = { id: string; at: string };
export const newOp = (): Op => ({ id: crypto.getRandomValues(new Uint32Array(2)).join("-"), at: nowIso() });

export function enqueue(method: string, path: string, body: string, shown?: string, query?: string, held = true, op: Op = newOp()): Response {
    const r = rule(method, path)!;
    const b = parseBody(body);
    let from: string | undefined;
    let drop = false;
    if (isMark(method, path)) {
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
            id: op.id,
            at: op.at,
            user: tokenUser(),
            method,
            path,
            body,
            what: r.what(b),
            ...(from !== undefined ? { from } : {}),
            ...(query ? { query } : {}),
            ...(r.eager && !held ? { eager: true as const } : {}),
        });
    save();
    if (held) queuedCount++;
    deferredCount++;
    setTimeout(() => void flush(), 0);
    return new Response(JSON.stringify(r.echo ? r.echo(b) : b), { status: 202, headers: { "Content-Type": "application/json", [QUEUED]: "1" } });
}

function bodyStatus(i: Item): string | undefined {
    const s = parseBody(i.body).status;
    return typeof s === "string" ? s : undefined;
}

let queuedCount = 0;
/** Bumped on every save that was held; act()/forms compare it to say "saved on this phone". */
export const heldSoFar = () => queuedCount;
let deferredCount = 0;
/** Bumped on every save the queue took, held or eager: the page's answer is the echo, and Today updates when it lands. */
export const deferredSoFar = () => deferredCount;

export const HELD = "Saved on this phone. It will send by itself when the signal is back.";

/** The toast for a finished save: the usual one, or "saved on this phone" if it had to wait. */
export function doneText(before: number, done?: string): string | undefined {
    return heldSoFar() > before ? HELD : done;
}

/** Send a refused save again, as it was (the person fixed what the server objected to, or it was passing). */
export function retry(id: string) {
    const i = outbox.items.find((x) => x.id === id);
    if (!i) return;
    delete i.failed;
    i.tried = false;
    if (rule(i.method, i.path)?.eager) i.eager = true;
    save();
    void flush();
}

export function discard(id: string) {
    outbox.items = discarded(outbox.items, id);
    save();
}

async function send(i: Item): Promise<Response> {
    return fetch(i.query ? `${i.path}?${i.query}` : i.path, {
        method: i.method,
        body: replayBody(i) || undefined,
        headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${localStorage.getItem("token") ?? ""}`,
            traceparent: traceparent(),
            // The server counts the save at the time it was made, and answers a resend of the same id as it did the first time.
            [OCCURRED_AT]: i.at,
            [OP_ID]: i.id,
        },
        // A hung send ends here; the server answers a resend of the same op id without doing it twice.
        signal: AbortSignal.timeout(15_000),
    });
}

const MARK = /^\/api\/sessions\/[^/]+\/status$/;
const isMark = (method: string, path: string) => method === "PATCH" && MARK.test(path);
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
        // A phone back after a day or more off signal renews its sign-in before sending.
        if (tokenExpired() && !(await refreshToken())) return;
        for (const i of waiting()) {
            // The person replaced this one while earlier saves were going out: don't send the old one.
            const live = outbox.items.find((x) => x.id === i.id);
            if (!live || live.failed) continue;
            // A save on a record behind one the server refused waits for the person (lib/outbox-versions.ts).
            if (heldBehindRefused(outbox.items, live)) continue;
            live.tried = true;
            save();
            let res: Response;
            try {
                res = await send(live);
            } catch {
                reportNetFailure();
                return;
            }
            // It went out past the client's middleware: report what it changed here, before Landed takes the answer in.
            answered(live.method, res.headers);
            const body = await readBody(res);
            const verdict = classify(res.status, body);
            if (res.status === 401 && (await refreshToken())) {
                setTimeout(() => void flush(), 0);
                return;
            }
            if (verdict.kind === "retry") return;
            reportNetOk();
            if (verdict.kind === "applied") {
                const v = (body as { version?: unknown } | null)?.version;
                landed(outbox.items, live, typeof v === "number" ? v : undefined);
                if (keepsAfterSend(i)) live.sentAt = Date.now();
                else outbox.items = outbox.items.filter((x) => x.id !== i.id);
                save();
                window.dispatchEvent(new CustomEvent<Landed>(LANDED, { detail: { item: live, answer: body } }));
                // An eager save's own answer updates Today; only saves that waited for signal make every screen re-read.
                if (!live.eager) sent++;
                continue;
            }
            const cur = outbox.items.find((x) => x.id === i.id);
            if (cur) cur.failed = verdict.message;
            window.dispatchEvent(new CustomEvent<Refused>(REFUSED, { detail: { method: live.method, path: live.path, status: res.status } }));
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
