/** Every read the UI makes, with its cache key. Pages and hover-prefetch share these. */
import { api, ok, ApiError, type S } from "../api/client";
import { fetchCached } from "./cache.svelte";
import { operationalDate } from "./format";
import { key, type Key } from "./keys";

export type Loader<T> = { key: Key; fn: () => Promise<T> };
const L = <T>(key: Key, fn: () => Promise<T>): Loader<T> => ({ key, fn });
const b = (branchId: string) => ({ params: { path: { branchId } } });

/**
 * One request carries all three of the Today screen's lists. The loaders for sessions, on-duty
 * list and booked visits are called in the same tick, so they share one request; a later call
 * (after a save, say) asks again.
 */
const todayInFlight = new Map<string, Promise<S["TodayScreenResponse"]>>();
const seeded = new WeakSet<Promise<unknown>>();
function todayScreen(branchId: string) {
    let p = todayInFlight.get(branchId);
    // A seed from the start bundle is shared by the loaders asking in this tick, then dropped: a later ask is a new read.
    if (p && seeded.has(p)) setTimeout(() => todayInFlight.get(branchId) === p && todayInFlight.delete(branchId), 0);
    if (!p) {
        p = ok(api.GET("/api/branches/{branchId}/today-screen", b(branchId)));
        todayInFlight.set(branchId, p);
        setTimeout(() => todayInFlight.delete(branchId), 0);
    }
    return p;
}
/**
 * The start bundle already holds Today's lists: hand them to the first three loaders instead of asking
 * again. If the bundle was for another branch, or failed, they ask as usual.
 */
export function seedTodayScreen(branchId: string, bundle: Promise<S["TodayScreenResponse"]>) {
    const p = bundle.catch(() => ok(api.GET("/api/branches/{branchId}/today-screen", b(branchId))));
    p.catch(() => {});
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
        L(key.dashboard(branchId, operationalDate()), async () => {
            const t = await todayScreen(branchId);
            return part(t.dashboard, t.dashboardRefusal);
        }),
    /** Earlier open days still holding a Waiting session (feedback d6cc83cf); "dash:" so marks refresh it. */
    earlierWaiting: (branchId: string) =>
        L(key.earlierWaiting(branchId, operationalDate()), async () => (await todayScreen(branchId)).earlierWaitingDays ?? []),
    /** A day's row at a branch (its id and status), or null when nothing was recorded that day. */
    branchDay: (branchId: string, date: string) =>
        L(key.branchDay(branchId, date), async () => {
            const r = await ok(api.GET("/api/branches/{branchId}/remittance-days", { params: { path: { branchId }, query: { from: date, to: date } } }));
            return r[0] ?? null;
        }),
    daySessions: (branchId: string, date: string) =>
        L(key.daySessions(branchId, date), () => ok(api.GET("/api/branches/{branchId}/sessions", { params: { path: { branchId }, query: { date } } }))),
    roster: (branchId: string) => L(key.roster(branchId, operationalDate()), async () => part((await todayScreen(branchId)).roster)),
    members: (branchId: string) => L(key.members(branchId), () => ok(api.GET("/api/branches/{branchId}/members", b(branchId)))),
    dayUsers: (branchDayId: string) =>
        L(key.dayUsers(branchDayId), () => ok(api.GET("/api/branch-days/{branchDayId}/users", { params: { path: { branchDayId } } }))),
    concerns: () => L(key.concerns(), () => ok(api.GET("/api/concerns"))),
    clients: (q: string) => L(key.clients(q), () => ok(api.GET("/api/clients", { params: { query: { q } } }))),
    recentClients: () => L(key.recentClients(), () => ok(api.GET("/api/recent-clients"))),
    client: (clientId: string) => L(key.client(clientId), () => ok(api.GET("/api/clients/{clientId}", { params: { path: { clientId } } }))),
    bookings: (branchId: string) => L(key.bookings(branchId), async () => part((await todayScreen(branchId)).bookings)),
    clientBookings: (clientId: string) =>
        L(key.clientBookings(clientId), () => ok(api.GET("/api/clients/{clientId}/bookings", { params: { path: { clientId } } }))),
    clientHistory: (clientId: string) =>
        L(key.clientHistory(clientId), () => ok(api.GET("/api/clients/{clientId}/history", { params: { path: { clientId } } }))),
    // GET /api/sessions/{id} only answers for sessions in the caller's notifications,
    // so read today's dashboard first and fall back to it.
    session: (sessionId: string, branchId: string) =>
        L(key.session(sessionId), async () => {
            if (branchId) {
                const d = await load
                    .dashboard(branchId)
                    .fn()
                    .catch(() => null);
                const row = d?.sessions.find((s) => s.id === sessionId);
                if (row) return row;
            }
            return ok(api.GET("/api/sessions/{sessionId}", { params: { path: { sessionId } } }));
        }),
    inventory: (branchId: string) => L(key.inventory(branchId), () => ok(api.GET("/api/branches/{branchId}/inventory", b(branchId)))),
    movements: (branchId: string, date: string) =>
        L(key.movements(branchId, date), () =>
            ok(api.GET("/api/branches/{branchId}/inventory/movements", { params: { path: { branchId }, query: { date: date || undefined } } })),
        ),
    products: () => L(key.products(), () => ok(api.GET("/api/products"))),
    categories: () => L(key.categories(), () => ok(api.GET("/api/product-categories"))),
    productSales: (branchDayId: string) => L(key.productSales(branchDayId), () => ok(api.GET("/api/product-sales", { params: { query: { branchDayId } } }))),
    expenses: (branchDayId: string) => L(key.expenses(branchDayId), () => ok(api.GET("/api/expenses", { params: { query: { branchDayId } } }))),
    compensations: (branchDayId: string) => L(key.compensations(branchDayId), () => ok(api.GET("/api/compensations", { params: { query: { branchDayId } } }))),
    allowances: (branchDayId: string) => L(key.allowances(branchDayId), () => ok(api.GET("/api/allowances", { params: { query: { branchDayId } } }))),
    commissionSplits: (branchDayId: string) =>
        L(key.commissionSplits(branchDayId), () => ok(api.GET("/api/commission-splits/{branchDayId}", { params: { path: { branchDayId } } }))),
    dailySummary: (branchId: string, date: string) =>
        L(key.dailySummary(branchId, date), () => ok(api.GET("/api/branches/{branchId}/daily-summary", { params: { path: { branchId }, query: { date } } }))),
    dailySummaries: (branchId: string, from: string, to: string) =>
        L(key.dailySummaries(branchId, from, to), () =>
            ok(api.GET("/api/branches/{branchId}/daily-summaries", { params: { path: { branchId }, query: { from, to, limit: "100" } } })),
        ),
    monthly: (branchId: string, year: number, month: number) =>
        L(key.monthly(branchId, year, month), () =>
            ok(api.GET("/api/branches/{branchId}/monthly-summary", { params: { path: { branchId }, query: { year, month } } })),
        ),
    remittances: (branchId: string) => L(key.remittances(branchId), () => ok(api.GET("/api/remittances", { params: { query: { branchId } } }))),
    remittance: (remittanceId: string) =>
        L(key.remittance(remittanceId), () => ok(api.GET("/api/remittances/{remittanceId}", { params: { path: { remittanceId } } }))),
    notifications: () => L(key.notifications(), () => ok(api.GET("/api/notifications"))),
    reliefMine: () => L(key.reliefMine(), () => ok(api.GET("/api/relief-access/mine"))),
    reliefInvitesMine: () => L(key.reliefInvitesMine(), () => ok(api.GET("/api/relief-invites"))),
    reliefOptions: () => L(key.reliefOptions(), () => ok(api.GET("/api/relief-access/branch-options"))),
    reliefUpcoming: (branchId: string) => L(key.reliefUpcoming(branchId), () => ok(api.GET("/api/relief-access", { params: { query: { branchId } } }))),
    reliefForBranch: (branchId: string, date: string) =>
        L(key.reliefForBranch(branchId, date), () => ok(api.GET("/api/relief-access", { params: { query: { branchId, date } } }))),
    branchInvites: (branchId: string) => L(key.branchInvites(branchId), () => ok(api.GET("/api/branches/{branchId}/relief-invites", b(branchId)))),
    users: () => L(key.users(), () => ok(api.GET("/api/users"))),
    roles: () => L(key.roles(), () => ok(api.GET("/api/roles"))),
    branches: () => L(key.branches(), () => ok(api.GET("/api/branches"))),
    assignments: (branchId: string) => L(key.assignments(branchId), () => ok(api.GET("/api/branches/{branchId}/assignments", b(branchId)))),
    rates: (branchId: string) => L(key.rates(branchId), () => ok(api.GET("/api/branches/{branchId}/rates", b(branchId)))),
    delegates: (branchId: string) => L(key.delegates(branchId), () => ok(api.GET("/api/branches/{branchId}/delegates", b(branchId)))),
    myHistory: (from: string, to: string) => L(key.myHistory(from, to), () => ok(api.GET("/api/me/history", { params: { query: { from, to } } }))),
    audit: (tableName: string, recordId: string) =>
        L(key.audit(tableName, recordId), () => ok(api.GET("/api/audit-log", { params: { query: { tableName, recordId } } }))),
    auditTables: () => L(key.auditTables(), () => ok(api.GET("/api/audit-log/tables"))),
    auditFlagged: () => L(key.auditFlagged(), () => ok(api.GET("/api/audit-log/flagged"))),
};

export function warm<T>(l: Loader<T>) {
    void fetchCached(l.key, l.fn).catch(() => {});
}
