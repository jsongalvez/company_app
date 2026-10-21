/**
 * Offline save queue. Front-desk saves made with no signal (new sessions, clients, sales,
 * expenses, clock-ins, marking sessions) wait on this phone and are sent, in order, once the
 * server answers again. Each one carries its own id or the record's version, so sending it
 * twice can't make a duplicate, and a change someone else made in the meantime is refused by
 * the server rather than overwritten: those land in "couldn't send" for the person to check.
 */
import { plain, type S } from "../api/client";
import { label } from "./format";
import { replayBody } from "./replay";
import { RECONNECTED, reportNetFailure, reportNetOk } from "./net.svelte";

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

const RECENT_KEY = "outbox-recent";
function loadRecent(): Item[] {
    try {
        const v = JSON.parse(localStorage.getItem(RECENT_KEY) ?? "[]") as Item[];
        return Array.isArray(v) ? v : [];
    } catch {
        return [];
    }
}
export const outbox = $state({ items: load(), sending: false, recent: loadRecent() });

/**
 * Creates sent a moment ago. They stay on screen until the server's own list shows them. A new
 * session is kept across a reload and until the list has it (a weak signal can leave the list
 * stale for minutes); a client only until the saved client list has it.
 */
const RECENT_MS = 120_000;
const RECENT_SESSION_MS = 24 * 3600_000;
const keepMs = (i: Item) => (i.path === "/api/sessions" ? RECENT_SESSION_MS : RECENT_MS);
function keepRecent(i: Item) {
    if (i.method !== "POST" || (i.path !== "/api/sessions" && i.path !== "/api/clients")) return;
    const now = Date.now();
    outbox.recent = [...outbox.recent.filter((r) => (r.sentAt ?? 0) + keepMs(r) > now), { ...i, sentAt: now }];
    localStorage.setItem(RECENT_KEY, JSON.stringify(outbox.recent));
}
/** The server's list now shows these sessions: stop holding them up ourselves. */
export function sessionsSeen(ids: Set<string>) {
    const left = outbox.recent.filter((r) => {
        if (r.path !== "/api/sessions") return true;
        try {
            return !ids.has(String((JSON.parse(r.body) as { id?: unknown }).id));
        } catch {
            return true;
        }
    });
    if (left.length === outbox.recent.length) return;
    outbox.recent = left;
    localStorage.setItem(RECENT_KEY, JSON.stringify(left));
}
function recentBodies(path: string): { at: string; body: Record<string, unknown> }[] {
    const now = Date.now();
    return outbox.recent
        .filter((i) => i.path === path && (i.sentAt ?? 0) + keepMs(i) > now)
        .map((i) => {
            try {
                return { at: i.at, body: JSON.parse(i.body) as Record<string, unknown> };
            } catch {
                return { at: i.at, body: {} };
            }
        });
}
/** New sessions just sent, which the server's list may not show yet. */
export const sentSessions = () => recentBodies("/api/sessions");
/** A client just sent, before the saved client list has it. */
export function sentClient(id: string): S["ClientResponse"] | undefined {
    const b = recentBodies("/api/clients").find((x) => String(x.body.id) === id);
    return b ? heldClient(b.body) : undefined;
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
export const waiting = () => mine().filter((i) => !i.failed);
export const failed = () => mine().filter((i) => !!i.failed);

/** The body of this person's waiting saves to a path, newest last. */
function waitingBodies(method: string, path: RegExp): { path: string; body: Record<string, unknown> }[] {
    return waiting()
        .filter((i) => i.method === method && path.test(i.path))
        .map((i) => {
            try {
                return { path: i.path, body: JSON.parse(i.body) as Record<string, unknown> };
            } catch {
                return { path: i.path, body: {} };
            }
        });
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
        gender: (b.gender as S["ClientResponse"]["gender"]) ?? "UNKNOWN",
        age: Number(b.age ?? 0),
        systolicBp: null,
        diastolicBp: null,
        medicalConditions: null,
        sessionCount: 0,
    };
}

/** Clients made on this phone that haven't reached the server yet. */
export function heldClients(): S["ClientResponse"][] {
    return waitingBodies("POST", /^\/api\/clients$/).map((x) => heldClient(x.body));
}

/** New sessions waiting to send, with when they were made. */
export function heldSessions(): { at: string; body: Record<string, unknown> }[] {
    return waiting()
        .filter((i) => i.method === "POST" && i.path === "/api/sessions")
        .map((i) => {
            try {
                return { at: i.at, body: JSON.parse(i.body) as Record<string, unknown> };
            } catch {
                return { at: i.at, body: {} };
            }
        });
}

/** A session's mark still waiting to send, so lists can show it straight away. */
export function heldMark(sessionId: string): string | undefined {
    const m = waitingBodies("PATCH", /^\/api\/sessions\/[^/]+\/status$/).filter((x) => x.path === `/api/sessions/${sessionId}/status`);
    return m.length ? String(m[m.length - 1]!.body.status) : undefined;
}

/** Was the last waiting clock action a clock-in (its body), a clock-out ("out"), or none? */
export function heldClock(): { attendanceId: string; branchId: string } | "out" | undefined {
    const c = waiting().filter((i) => /^\/api\/attendance\/clock-(in|out)$/.test(i.path));
    const last = c[c.length - 1];
    if (!last) return undefined;
    if (last.path.endsWith("clock-out")) return "out";
    try {
        return JSON.parse(last.body) as { attendanceId: string; branchId: string };
    } catch {
        return undefined;
    }
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
    let b: Record<string, unknown> = {};
    try {
        b = JSON.parse(body) as Record<string, unknown>;
    } catch {
        /* no body */
    }
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
            at: new Date().toISOString(),
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
    try {
        return (JSON.parse(i.body) as { status?: string }).status;
    } catch {
        return undefined;
    }
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

function serverMessage(status: number, body: unknown): string {
    if (body && typeof body === "object" && "error" in body && typeof body.error === "string") return plain(body.error);
    if (status === 409) return "Someone changed this while you were offline.";
    if (status === 403) return "You no longer have access to do this.";
    if (status === 404) return "It no longer exists.";
    return `The server refused it (${status}).`;
}

async function send(i: Item): Promise<Response> {
    return fetch(i.path, {
        method: i.method,
        body: replayBody(i) || undefined,
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${localStorage.getItem("token") ?? ""}` },
        signal: AbortSignal.timeout(20_000),
    });
}

/** A mark refused for an old version may have landed on an earlier try: check before calling it a clash. */
async function alreadyApplied(i: Item): Promise<{ version?: number } | null> {
    const m = /^\/api\/sessions\/([^/]+)\/status$/.exec(i.path);
    if (!m) return null;
    try {
        const res = await fetch(`/api/sessions/${m[1]}`, { headers: { Authorization: `Bearer ${localStorage.getItem("token") ?? ""}` } });
        if (!res.ok) return null;
        const s = (await res.json()) as { sessionStatus?: string; status?: string; version?: number };
        return (s.sessionStatus ?? s.status) === (JSON.parse(i.body) as { status?: string }).status ? { version: s.version } : null;
    } catch {
        return null;
    }
}

/** Marks queued behind one that has just landed were made from the version it was made from: move them up. */
function chainVersions(landed: Item, newVersion: number | undefined) {
    let from: number;
    try {
        from = (JSON.parse(landed.body) as { version: number }).version;
    } catch {
        return;
    }
    for (const x of outbox.items) {
        if (x.id === landed.id || x.method !== landed.method || x.path !== landed.path || x.failed) continue;
        try {
            const o = JSON.parse(x.body) as { version?: number };
            if (o.version === from) x.body = JSON.stringify({ ...o, version: newVersion ?? from + 1 });
        } catch {
            /* leave it */
        }
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
            if (res.status === 401 || res.status === 429 || res.status >= 500) return;
            reportNetOk();
            const applied = res.ok ? null : res.status === 409 ? await alreadyApplied(i) : null;
            if (res.ok || applied) {
                if (i.method === "PATCH") {
                    let v = applied?.version;
                    if (res.ok) {
                        try {
                            v = ((await res.clone().json()) as { version?: number }).version;
                        } catch {
                            /* no version in the answer */
                        }
                    }
                    chainVersions(i, v);
                }
                keepRecent(i);
                discard(i.id);
                sent++;
                continue;
            }
            let body: unknown = null;
            try {
                body = await res.json();
            } catch {
                /* not json */
            }
            const cur = outbox.items.find((x) => x.id === i.id);
            if (cur) cur.failed = serverMessage(res.status, body);
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

window.addEventListener(RECONNECTED, () => void flush());
window.addEventListener("online", () => void flush());
setInterval(() => waiting().length && navigator.onLine && void flush(), 30_000);
setTimeout(() => void flush(), 1000);
