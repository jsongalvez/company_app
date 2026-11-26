/** Every read the UI makes, with its cache key. Pages and hover-prefetch share these. */
import { api, ok, ApiError, type S } from "../api/client";
import { fetchCached, peek } from "./cache.svelte";
import { operationalDate } from "./format";
import { key, type Key } from "./keys";
import { keepTicket } from "./site-ticket";
import { READS, Seen, tablesOf, type Tables } from "./tables";

export type Loader<T> = { key: Key; fn: (seen: Seen) => Promise<T> };
const L = <T>(key: Key, fn: (seen: Seen) => Promise<T>): Loader<T> => ({ key, fn });

const b = (branchId: string) => ({ params: { path: { branchId } } });

type Answer<T> = { data?: T; error?: unknown; response: Response };
/** Unwrap a read's answer and note the tables the server says it read, so the cache knows which saves make it stale. */
export function read<T>(seen: Seen, p: Promise<Answer<T>>): Promise<T> {
    return ok(p.then((r) => (seen.add(tablesOf(r.response.headers.get(READS))), r)));
}

/**
 * One request carries all three of the Today screen's lists. The loaders for sessions, on-duty
 * list and booked visits are called in the same tick, so they share one request; a later call
 * (after a save, say) asks again. Each loader notes the shared answer's reads.
 */
type Screen = { screen: S["TodayScreenResponse"]; reads: Tables };
const todayInFlight = new Map<string, Promise<Screen>>();
const seeded = new WeakSet<Promise<unknown>>();
function askToday(branchId: string): Promise<Screen> {
    const seen = new Seen();
    return read(seen, api.GET("/api/branches/{branchId}/today-screen", b(branchId))).then((screen) => ({ screen, reads: seen.tables }));
}
async function todayScreen(branchId: string, seen: Seen) {
    let p = todayInFlight.get(branchId);
    // A seed from the start bundle is shared by the loaders asking in this tick, then dropped: a later ask is a new read.
    if (p && seeded.has(p)) setTimeout(() => todayInFlight.get(branchId) === p && todayInFlight.delete(branchId), 0);
    if (!p) {
        p = askToday(branchId);
        p.then((t) => keepTicket(branchId, t.screen)).catch(() => {});
        todayInFlight.set(branchId, p);
        setTimeout(() => todayInFlight.delete(branchId), 0);
    }
    const t = await p;
    seen.add(t.reads);
    return t.screen;
}
/**
 * The start bundle already holds Today's lists: hand them to the first three loaders instead of asking
 * again. If the bundle was for another branch, or failed, they ask as usual. A seed came with no reads of its own: `*`.
 */
export function seedTodayScreen(branchId: string, bundle: Promise<S["TodayScreenResponse"]>) {
    const p = bundle.then((screen): Screen => ({ screen, reads: "*" })).catch(() => askToday(branchId));
    p.then((t) => keepTicket(branchId, t.screen)).catch(() => {});
    seeded.add(p);
    todayInFlight.set(branchId, p);
    setTimeout(() => todayInFlight.get(branchId) === p && todayInFlight.delete(branchId), 5000);
}
const NO_ACCESS = "You don't have access to this";
const part = <T>(v: T | null | undefined, why?: string | null): T => {
    if (v == null) throw new ApiError(403, why ?? NO_ACCESS);
    return v;
};

export const load = {
    dashboard: (branchId: string) =>
        L(key.dashboard(branchId, operationalDate()), async (seen) => {
            const t = await todayScreen(branchId, seen);
            return part(t.dashboard, t.dashboardRefusal);
        }),
    earlierWaiting: (branchId: string) =>
        L(key.earlierWaiting(branchId, operationalDate()), async (seen) => (await todayScreen(branchId, seen)).earlierWaitingDays ?? []),
    /** A day's row at a branch (its id and status), or null when nothing was recorded that day. */
    branchDay: (branchId: string, date: string) =>
        L(key.branchDay(branchId, date), async (seen) => {
            const r = await read(
                seen,
                api.GET("/api/branches/{branchId}/remittance-days", { params: { path: { branchId }, query: { from: date, to: date } } }),
            );
            return r[0] ?? null;
        }),
    daySessions: (branchId: string, date: string) =>
        L(key.daySessions(branchId, date), (seen) =>
            read(seen, api.GET("/api/branches/{branchId}/sessions", { params: { path: { branchId }, query: { date } } })),
        ),
    roster: (branchId: string) => L(key.roster(branchId, operationalDate()), async (seen) => part((await todayScreen(branchId, seen)).roster)),
    members: (branchId: string) => L(key.members(branchId), (seen) => read(seen, api.GET("/api/branches/{branchId}/members", b(branchId)))),
    dayUsers: (branchDayId: string) =>
        L(key.dayUsers(branchDayId), (seen) => read(seen, api.GET("/api/branch-days/{branchDayId}/users", { params: { path: { branchDayId } } }))),
    concerns: () => L(key.concerns(), (seen) => read(seen, api.GET("/api/concerns"))),
    clients: (q: string) => L(key.clients(q), (seen) => read(seen, api.GET("/api/clients", { params: { query: { q } } }))),
    recentClients: () => L(key.recentClients(), (seen) => read(seen, api.GET("/api/recent-clients"))),
    client: (clientId: string) => L(key.client(clientId), (seen) => read(seen, api.GET("/api/clients/{clientId}", { params: { path: { clientId } } }))),
    bookings: (branchId: string) => L(key.bookings(branchId), async (seen) => part((await todayScreen(branchId, seen)).bookings)),
    clientBookings: (clientId: string) =>
        L(key.clientBookings(clientId), (seen) => read(seen, api.GET("/api/clients/{clientId}/bookings", { params: { path: { clientId } } }))),
    clientHistory: (clientId: string) =>
        L(key.clientHistory(clientId), (seen) => read(seen, api.GET("/api/clients/{clientId}/history", { params: { path: { clientId } } }))),
    // GET /api/sessions/{id} only answers for sessions in the caller's notifications,
    // so read today's dashboard first and fall back to it.
    session: (sessionId: string, branchId: string) =>
        L(key.session(sessionId), async (seen) => {
            if (branchId) {
                const d = await load
                    .dashboard(branchId)
                    .fn(seen)
                    .catch(() => null);
                const row = d?.sessions.find((s) => s.id === sessionId);
                if (row) return row;
            }
            return read(seen, api.GET("/api/sessions/{sessionId}", { params: { path: { sessionId } } }));
        }),
    inventory: (branchId: string) => L(key.inventory(branchId), (seen) => read(seen, api.GET("/api/branches/{branchId}/inventory", b(branchId)))),
    movements: (branchId: string, date: string) =>
        L(key.movements(branchId, date), (seen) =>
            read(seen, api.GET("/api/branches/{branchId}/inventory/movements", { params: { path: { branchId }, query: { date: date || undefined } } })),
        ),
    products: () => L(key.products(), (seen) => read(seen, api.GET("/api/products"))),
    categories: () => L(key.categories(), (seen) => read(seen, api.GET("/api/product-categories"))),
    productSales: (branchDayId: string) =>
        L(key.productSales(branchDayId), (seen) => read(seen, api.GET("/api/product-sales", { params: { query: { branchDayId } } }))),
    expenses: (branchDayId: string) => L(key.expenses(branchDayId), (seen) => read(seen, api.GET("/api/expenses", { params: { query: { branchDayId } } }))),
    compensations: (branchDayId: string) =>
        L(key.compensations(branchDayId), (seen) => read(seen, api.GET("/api/compensations", { params: { query: { branchDayId } } }))),
    allowances: (branchDayId: string) =>
        L(key.allowances(branchDayId), (seen) => read(seen, api.GET("/api/allowances", { params: { query: { branchDayId } } }))),
    commissionSplits: (branchDayId: string) =>
        L(key.commissionSplits(branchDayId), (seen) => read(seen, api.GET("/api/commission-splits/{branchDayId}", { params: { path: { branchDayId } } }))),
    dailySummary: (branchId: string, date: string) =>
        L(key.dailySummary(branchId, date), (seen) =>
            read(seen, api.GET("/api/branches/{branchId}/daily-summary", { params: { path: { branchId }, query: { date } } })),
        ),
    dailySummaries: (branchId: string, from: string, to: string) =>
        L(key.dailySummaries(branchId, from, to), (seen) =>
            read(seen, api.GET("/api/branches/{branchId}/daily-summaries", { params: { path: { branchId }, query: { from, to, limit: "100" } } })),
        ),
    monthly: (branchId: string, year: number, month: number) =>
        L(key.monthly(branchId, year, month), (seen) =>
            read(seen, api.GET("/api/branches/{branchId}/monthly-summary", { params: { path: { branchId }, query: { year, month } } })),
        ),
    remittances: (branchId: string) => L(key.remittances(branchId), (seen) => read(seen, api.GET("/api/remittances", { params: { query: { branchId } } }))),
    remittance: (remittanceId: string) =>
        L(key.remittance(remittanceId), (seen) => read(seen, api.GET("/api/remittances/{remittanceId}", { params: { path: { remittanceId } } }))),
    notifications: () => L(key.notifications(), (seen) => read(seen, api.GET("/api/notifications"))),
    reliefMine: () => L(key.reliefMine(), (seen) => read(seen, api.GET("/api/relief-access/mine"))),
    reliefInvitesMine: () => L(key.reliefInvitesMine(), (seen) => read(seen, api.GET("/api/relief-invites"))),
    reliefOptions: () => L(key.reliefOptions(), (seen) => read(seen, api.GET("/api/relief-access/branch-options"))),
    reliefUpcoming: (branchId: string) =>
        L(key.reliefUpcoming(branchId), (seen) => read(seen, api.GET("/api/relief-access", { params: { query: { branchId } } }))),
    reliefForBranch: (branchId: string, date: string) =>
        L(key.reliefForBranch(branchId, date), (seen) => read(seen, api.GET("/api/relief-access", { params: { query: { branchId, date } } }))),
    branchInvites: (branchId: string) => L(key.branchInvites(branchId), (seen) => read(seen, api.GET("/api/branches/{branchId}/relief-invites", b(branchId)))),
    users: () => L(key.users(), (seen) => read(seen, api.GET("/api/users"))),
    roles: () => L(key.roles(), (seen) => read(seen, api.GET("/api/roles"))),
    branches: () => L(key.branches(), (seen) => read(seen, api.GET("/api/branches"))),
    assignments: (branchId: string) => L(key.assignments(branchId), (seen) => read(seen, api.GET("/api/branches/{branchId}/assignments", b(branchId)))),
    rates: (branchId: string) => L(key.rates(branchId), (seen) => read(seen, api.GET("/api/branches/{branchId}/rates", b(branchId)))),
    delegates: (branchId: string) => L(key.delegates(branchId), (seen) => read(seen, api.GET("/api/branches/{branchId}/delegates", b(branchId)))),
    myHistory: (from: string, to: string) => L(key.myHistory(from, to), (seen) => read(seen, api.GET("/api/me/history", { params: { query: { from, to } } }))),
    audit: (tableName: string, recordId: string) =>
        L(key.audit(tableName, recordId), (seen) => read(seen, api.GET("/api/audit-log", { params: { query: { tableName, recordId } } }))),
    auditTables: () => L(key.auditTables(), (seen) => read(seen, api.GET("/api/audit-log/tables"))),
    auditFlagged: () => L(key.auditFlagged(), (seen) => read(seen, api.GET("/api/audit-log/flagged"))),
};

export function warm<T>(l: Loader<T>) {
    void fetchCached(l.key, l.fn).catch(() => {});
}

/** What the signed-in person may read at the branch, from the server's rights (`app.svelte.ts`). */
export type PullRights = { seeDay: boolean; seeBranch: boolean; pay: boolean };

/**
 * What the save dialogs read (new session, product sale, pay), pulled into the phone's copy at sign-in,
 * at open and when the signal returns, so a whole day with no signal can still record work: the day's
 * sessions, staff, stock and prices. Only reads the person may make, and only what the phone has never
 * saved: pages refresh their own lists when opened.
 */
export function pullBranch(branchId: string, branchDayId: string | undefined, may: PullRights) {
    if (!branchId || !may.seeDay) return;
    const reads: Loader<unknown>[] = [
        load.daySessions(branchId, operationalDate()),
        load.members(branchId),
        load.inventory(branchId),
        load.concerns(),
        load.recentClients(),
        ...(may.seeBranch ? [load.rates(branchId)] : []),
        ...(branchDayId && may.pay ? [load.dayUsers(branchDayId)] : []),
    ];
    for (const l of reads) if (peek(l.key) === undefined) void fetchCached(l.key, l.fn).catch(() => {});
}
