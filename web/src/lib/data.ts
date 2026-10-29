/** Every read the UI makes, with its cache key. Pages and hover-prefetch share these. */
import { api, ok, ApiError, type S } from "../api/client";
import { fetchCached } from "./cache.svelte";
import { operationalDate } from "./format";

export type Loader<T> = { key: string; fn: () => Promise<T> };
const L = <T>(key: string, fn: () => Promise<T>): Loader<T> => ({ key, fn });
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
        L(`dash:${branchId}:${operationalDate()}`, async () => {
            const t = await todayScreen(branchId);
            return part(t.dashboard, t.dashboardRefusal);
        }),
    /** A day's row at a branch (its id and status), or null when nothing was recorded that day. */
    branchDay: (branchId: string, date: string) =>
        L(`bday:${branchId}:${date}`, async () => {
            const r = await ok(api.GET("/api/branches/{branchId}/remittance-days", { params: { path: { branchId }, query: { from: date, to: date } } }));
            return r[0] ?? null;
        }),
    daySessions: (branchId: string, date: string) =>
        L(`dsess:${branchId}:${date}`, () => ok(api.GET("/api/branches/{branchId}/sessions", { params: { path: { branchId }, query: { date } } }))),
    roster: (branchId: string) => L(`roster:${branchId}:${operationalDate()}`, async () => part((await todayScreen(branchId)).roster)),
    members: (branchId: string) => L(`members:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/members", b(branchId)))),
    dayUsers: (branchDayId: string) =>
        L(`dayusers:${branchDayId}`, () => ok(api.GET("/api/branch-days/{branchDayId}/users", { params: { path: { branchDayId } } }))),
    concerns: () => L(`concerns`, () => ok(api.GET("/api/concerns"))),
    clients: (q: string) => L(`clients:${q}`, () => ok(api.GET("/api/clients", { params: { query: { q } } }))),
    // Key "clients:" with no term cannot clash with a search (2+ letters) and still clears with invalidate("clients:").
    recentClients: () => L("clients:", () => ok(api.GET("/api/recent-clients"))),
    client: (clientId: string) => L(`client:${clientId}`, () => ok(api.GET("/api/clients/{clientId}", { params: { path: { clientId } } }))),
    bookings: (branchId: string) => L(`bookings:${branchId}`, async () => part((await todayScreen(branchId)).bookings)),
    clientBookings: (clientId: string) =>
        L(`bookings:client:${clientId}`, () => ok(api.GET("/api/clients/{clientId}/bookings", { params: { path: { clientId } } }))),
    clientHistory: (clientId: string) =>
        L(`client:${clientId}:history`, () => ok(api.GET("/api/clients/{clientId}/history", { params: { path: { clientId } } }))),
    // GET /api/sessions/{id} only answers for sessions in the caller's notifications,
    // so read today's dashboard first and fall back to it.
    session: (sessionId: string, branchId: string) =>
        L(`session:${sessionId}`, async () => {
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
    inventory: (branchId: string) => L(`inv:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/inventory", b(branchId)))),
    movements: (branchId: string, date: string) =>
        L(`moves:${branchId}:${date}`, () =>
            ok(api.GET("/api/branches/{branchId}/inventory/movements", { params: { path: { branchId }, query: { date: date || undefined } } })),
        ),
    products: () => L(`products`, () => ok(api.GET("/api/products"))),
    categories: () => L(`categories`, () => ok(api.GET("/api/product-categories"))),
    expenses: (branchDayId: string) => L(`exp:${branchDayId}`, () => ok(api.GET("/api/expenses", { params: { query: { branchDayId } } }))),
    compensations: (branchDayId: string) => L(`comp:${branchDayId}`, () => ok(api.GET("/api/compensations", { params: { query: { branchDayId } } }))),
    allowances: (branchDayId: string) => L(`allow:${branchDayId}`, () => ok(api.GET("/api/allowances", { params: { query: { branchDayId } } }))),
    commissionSplits: (branchDayId: string) =>
        L(`split:${branchDayId}`, () => ok(api.GET("/api/commission-splits/{branchDayId}", { params: { path: { branchDayId } } }))),
    dailySummary: (branchId: string, date: string) =>
        L(`dsum:${branchId}:${date}`, () => ok(api.GET("/api/branches/{branchId}/daily-summary", { params: { path: { branchId }, query: { date } } }))),
    dailySummaries: (branchId: string, from: string, to: string) =>
        L(`dsums:${branchId}:${from}:${to}`, () =>
            ok(api.GET("/api/branches/{branchId}/daily-summaries", { params: { path: { branchId }, query: { from, to, limit: "100" } } })),
        ),
    monthly: (branchId: string, year: number, month: number) =>
        L(`msum:${branchId}:${year}-${month}`, () =>
            ok(api.GET("/api/branches/{branchId}/monthly-summary", { params: { path: { branchId }, query: { year, month } } })),
        ),
    remittances: (branchId: string) => L(`rem:${branchId}`, () => ok(api.GET("/api/remittances", { params: { query: { branchId } } }))),
    remittance: (remittanceId: string) =>
        L(`remd:${remittanceId}`, () => ok(api.GET("/api/remittances/{remittanceId}", { params: { path: { remittanceId } } }))),
    notifications: () => L(`notif`, () => ok(api.GET("/api/notifications"))),
    reliefMine: () => L(`relief:mine`, () => ok(api.GET("/api/relief-access/mine"))),
    reliefInvitesMine: () => L(`relief:invites`, () => ok(api.GET("/api/relief-invites"))),
    reliefOptions: () => L(`relief:options`, () => ok(api.GET("/api/relief-access/branch-options"))),
    reliefUpcoming: (branchId: string) => L(`relief:branch:${branchId}:upcoming`, () => ok(api.GET("/api/relief-access", { params: { query: { branchId } } }))),
    reliefForBranch: (branchId: string, date: string) =>
        L(`relief:branch:${branchId}:${date}`, () => ok(api.GET("/api/relief-access", { params: { query: { branchId, date } } }))),
    branchInvites: (branchId: string) => L(`relief:binv:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/relief-invites", b(branchId)))),
    users: () => L(`users`, () => ok(api.GET("/api/users"))),
    roles: () => L(`roles`, () => ok(api.GET("/api/roles"))),
    branches: () => L(`branches`, () => ok(api.GET("/api/branches"))),
    assignments: (branchId: string) => L(`assign:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/assignments", b(branchId)))),
    rates: (branchId: string) => L(`rates:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/rates", b(branchId)))),
    delegates: (branchId: string) => L(`deleg:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/delegates", b(branchId)))),
    myHistory: (from: string, to: string) => L(`me:history:${from}:${to}`, () => ok(api.GET("/api/me/history", { params: { query: { from, to } } }))),
    audit: (tableName: string, recordId: string) =>
        L(`audit:${tableName}:${recordId}`, () => ok(api.GET("/api/audit-log", { params: { query: { tableName, recordId } } }))),
    auditTables: () => L(`audit:tables`, () => ok(api.GET("/api/audit-log/tables"))),
    auditFlagged: () => L(`audit:flagged`, () => ok(api.GET("/api/audit-log/flagged"))),
};

export function warm<T>(l: Loader<T>) {
    void fetchCached(l.key, l.fn).catch(() => {});
}
