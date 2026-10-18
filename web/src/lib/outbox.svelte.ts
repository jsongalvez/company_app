/**
 * Offline save queue. Front-desk saves made with no signal (new sessions, clients, sales,
 * expenses, clock-ins, marking sessions) wait on this phone and are sent, in order, once the
 * server answers again. Each one carries its own id or the record's version, so sending it
 * twice can't make a duplicate, and a change someone else made in the meantime is refused by
 * the server rather than overwritten: those land in "couldn't send" for the person to check.
 */
import { plain, type S } from "../api/client";
import { label } from "./format";
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

export const outbox = $state({ items: load(), sending: false });

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
export function enqueue(method: string, path: string, body: string): Response {
    const r = rule(method, path)!;
    let b: Record<string, unknown> = {};
    try {
        b = JSON.parse(body) as Record<string, unknown>;
    } catch {
        /* no body */
    }
    // Marking the same session twice offline: only the last mark counts (same version).
    if (method === "PATCH") outbox.items = outbox.items.filter((i) => !(i.method === method && i.path === path && !i.failed));
    outbox.items.push({
        id: crypto.getRandomValues(new Uint32Array(2)).join("-"),
        at: new Date().toISOString(),
        user: tokenUser(),
        method,
        path,
        body,
        what: r.what(b),
    });
    save();
    queuedCount++;
    setTimeout(() => void flush(), 0);
    return new Response(JSON.stringify(r.echo ? r.echo(b) : b), { status: 202, headers: { "Content-Type": "application/json", "X-Queued": "1" } });
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
        body: i.body || undefined,
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${localStorage.getItem("token") ?? ""}` },
        signal: AbortSignal.timeout(20_000),
    });
}

/** A mark refused for an old version may have landed on an earlier try: check before calling it a clash. */
async function alreadyApplied(i: Item): Promise<boolean> {
    const m = /^\/api\/sessions\/([^/]+)\/status$/.exec(i.path);
    if (!m) return false;
    try {
        const res = await fetch(`/api/sessions/${m[1]}`, { headers: { Authorization: `Bearer ${localStorage.getItem("token") ?? ""}` } });
        if (!res.ok) return false;
        const s = (await res.json()) as { status?: string };
        return s.status === (JSON.parse(i.body) as { status?: string }).status;
    } catch {
        return false;
    }
}

/** Send what's waiting, oldest first. Stops at the first sign the server can't be reached. */
export async function flush(): Promise<void> {
    if (outbox.sending) return;
    outbox.sending = true;
    let sent = 0;
    try {
        for (const i of waiting()) {
            let res: Response;
            try {
                res = await send(i);
            } catch {
                reportNetFailure();
                return;
            }
            if (res.status === 401 || res.status === 429 || res.status >= 500) return;
            reportNetOk();
            if (res.ok || (res.status === 409 && (await alreadyApplied(i)))) {
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
    } finally {
        outbox.sending = false;
        if (sent) window.dispatchEvent(new Event(SENT));
    }
}

window.addEventListener(RECONNECTED, () => void flush());
window.addEventListener("online", () => void flush());
setInterval(() => waiting().length && navigator.onLine && void flush(), 30_000);
setTimeout(() => void flush(), 1000);
