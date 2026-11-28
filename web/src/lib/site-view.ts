/**
 * Pure logic for turning saves and site-hub records into the Site panel's lists and unsent flags.
 * Kept separate from site.svelte.ts so unit tests exercise every state transition without Svelte runes.
 */
import { operationalDate } from "./format";
import { parseBody } from "./outbox-classify";

export type SiteKind = "session" | "mark" | "clock-in" | "clock-out" | "sale" | "other";

export type SiteEntry = {
    opId: string;
    kind: SiteKind;
    user: string;
    name: string;
    at: string;
    what: string;
    client?: string;
    sessionId?: string;
    status?: string;
    quantity?: number;
    /** Local-only flag (not sent over the hub): true when this device sealed the record. */
    own?: boolean;
    /** All op ids that must land on the server before a combined row (session + its mark) counts as sent. */
    relatedOpIds?: string[];
};

export type OpInput = {
    id: string;
    at: string;
    user: string;
    method: string;
    path: string;
    query?: string;
    body: string;
    what: string;
    failed?: string;
    branchId?: string;
};

export type EntryContext = {
    branchId: string;
    date: string;
    branchDayId?: string;
    displayName: string;
    clientNameById: (id: string) => string | undefined;
    sessionClientById: (id: string) => string | undefined;
};

export type ServerSessionLike = {
    id: string;
    sessionStatus: string;
    isVoided: boolean;
    clientName?: string | null;
};

const MARK_PATH = /^\/api\/sessions\/([^/]+)\/status$/;
const VOID_PATH = /^\/api\/sessions\/([^/]+)\/void$/;
const UNVOID_PATH = /^\/api\/sessions\/([^/]+)\/unvoid$/;

/** The branch a save belongs to: body, query, today's branch day, or the branch active when it was made. */
export function branchOfOp(op: OpInput, body: Record<string, unknown>, ctx: Pick<EntryContext, "branchId" | "branchDayId">): string | undefined {
    if (typeof body.branchId === "string") return body.branchId;
    const q = new URLSearchParams(op.query ?? "").get("branchId");
    if (q) return q;
    if (body.branchDayId && body.branchDayId === ctx.branchDayId) return ctx.branchId;
    return op.branchId;
}

/** Converts one local save into a hub entry, or undefined when it belongs to another branch/day or failed. */
export function entryFromOp(op: OpInput, ctx: EntryContext): Omit<SiteEntry, "opId"> | undefined {
    if (op.failed || operationalDate(new Date(op.at)) !== ctx.date) return undefined;
    const b = parseBody(op.body);
    if (branchOfOp(op, b, ctx) !== ctx.branchId) return undefined;
    const base = { user: op.user, name: ctx.displayName, at: op.at, what: op.what };
    if (op.method === "POST" && op.path === "/api/sessions")
        return { ...base, kind: "session", sessionId: String(b.id), client: ctx.clientNameById(String(b.clientId)) ?? "New client" };
    const mark = op.path.match(MARK_PATH);
    if (op.method === "PATCH" && mark) {
        const sid = mark[1]!;
        const client = ctx.sessionClientById(sid);
        return { ...base, kind: "mark", sessionId: sid, status: String(b.status), ...(client ? { client } : {}) };
    }
    const voided = op.path.match(VOID_PATH);
    if (op.method === "POST" && voided) {
        const sid = voided[1]!;
        const client = ctx.sessionClientById(sid);
        return { ...base, kind: "mark", sessionId: sid, status: "VOIDED", ...(client ? { client } : {}) };
    }
    const unvoided = op.path.match(UNVOID_PATH);
    if (op.method === "POST" && unvoided) {
        const sid = unvoided[1]!;
        const client = ctx.sessionClientById(sid);
        return { ...base, kind: "mark", sessionId: sid, status: "PENDING", ...(client ? { client } : {}) };
    }
    if (op.path === "/api/attendance/clock-in") return { ...base, kind: "clock-in" };
    if (op.path === "/api/attendance/clock-out") return { ...base, kind: "clock-out" };
    if (op.method === "POST" && op.path === "/api/product-sales") return { ...base, kind: "sale", quantity: Number(b.quantity ?? 0) };
    return { ...base, kind: "other" };
}

/** Derives Waiting, Done, On duty and Sales from hub entries reconciled with any server sessions already loaded. */
export function buildSiteView(entries: SiteEntry[], serverSessions: readonly ServerSessionLike[] = []) {
    const marks = new Map<string, SiteEntry>();
    const duty = new Map<string, SiteEntry>();
    const bySession = new Map<string, SiteEntry>();
    for (const e of entries) {
        if (e.kind === "session" && e.sessionId) bySession.set(e.sessionId, e);
        if (e.kind === "mark" && e.sessionId) marks.set(e.sessionId, e);
        if (e.kind === "clock-in" || e.kind === "clock-out") duty.set(e.user, e);
    }
    const serverById = new Map(serverSessions.map((s) => [s.id, s]));
    const statusOf = (sid: string): string => {
        const m = marks.get(sid);
        if (m?.status) return m.status;
        const srv = serverById.get(sid);
        if (srv) return srv.isVoided ? "VOIDED" : srv.sessionStatus;
        return "PENDING";
    };

    const waitingNow = entries.filter((e) => e.kind === "session" && statusOf(e.sessionId ?? "") === "PENDING");
    const done: SiteEntry[] = [];
    const seenDone = new Set<string>();
    for (const e of entries) {
        if (e.kind !== "session" || !e.sessionId || seenDone.has(e.sessionId)) continue;
        if (statusOf(e.sessionId) !== "COMPLETED") continue;
        seenDone.add(e.sessionId);
        const m = marks.get(e.sessionId);
        done.push({
            ...e,
            kind: "mark",
            status: "COMPLETED",
            opId: m?.opId ?? e.opId,
            at: m?.at ?? e.at,
            name: m?.name || e.name,
            own: m ? m.own && e.own : e.own,
            relatedOpIds: m ? [e.opId, m.opId] : [e.opId],
        });
    }
    for (const [sid, m] of marks) {
        if (m.status !== "COMPLETED" || seenDone.has(sid)) continue;
        seenDone.add(sid);
        const srv = serverById.get(sid);
        done.push({
            ...m,
            client: m.client ?? bySession.get(sid)?.client ?? srv?.clientName ?? "Session",
        });
    }
    done.sort((a, b) => a.at.localeCompare(b.at));

    const sales = entries.filter((e) => e.kind === "sale");
    return {
        waiting: waitingNow,
        done,
        onDuty: [...duty.values()].filter((e) => e.kind === "clock-in"),
        sales,
        sold: sales.reduce((n, e) => n + (e.quantity ?? 0), 0),
    };
}

/**
 * True when an entry is still waiting to reach the server.
 * Own records read live outbox state (never stale hub presence); other devices' records clear once the server shows them.
 */
export function isEntryUnsent(
    entry: SiteEntry,
    ownWaitingIds: ReadonlySet<string>,
    hubUnsentIds: ReadonlySet<string>,
    serverById: ReadonlyMap<string, ServerSessionLike>,
): boolean {
    const ops = entry.relatedOpIds ?? [entry.opId];
    if (entry.own) return ops.some((id) => ownWaitingIds.has(id));
    if (entry.sessionId) {
        const srv = serverById.get(entry.sessionId);
        if (srv) {
            if (entry.kind === "session") return false;
            if (entry.kind === "mark") {
                const srvStatus = srv.isVoided ? "VOIDED" : srv.sessionStatus;
                if (srvStatus === entry.status) return false;
            }
        }
    }
    return ops.some((id) => ownWaitingIds.has(id) || hubUnsentIds.has(id));
}
