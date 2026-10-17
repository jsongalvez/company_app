/** Every read the UI makes, with its cache key. Pages and hover-prefetch share these. */
import { api, ok } from "../api/client";
import { fetchCached } from "./cache.svelte";

export type Loader<T> = { key: string; fn: () => Promise<T> };
const L = <T>(key: string, fn: () => Promise<T>): Loader<T> => ({ key, fn });
const b = (branchId: string) => ({ params: { path: { branchId } } });

export const load = {
    dashboard: (branchId: string) => L(`dash:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/dashboard/today", b(branchId)))),
    roster: (branchId: string) => L(`roster:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/attendance/today", b(branchId)))),
    members: (branchId: string) => L(`members:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/members", b(branchId)))),
    dayUsers: (branchDayId: string) =>
        L(`dayusers:${branchDayId}`, () => ok(api.GET("/api/branch-days/{branchDayId}/users", { params: { path: { branchDayId } } }))),
    concerns: () => L(`concerns`, () => ok(api.GET("/api/concerns"))),
    clients: (q: string) => L(`clients:${q}`, () => ok(api.GET("/api/clients", { params: { query: { q } } }))),
    client: (clientId: string) => L(`client:${clientId}`, () => ok(api.GET("/api/clients/{clientId}", { params: { path: { clientId } } }))),
    // GET /api/sessions/{id} only answers for sessions in the caller's notifications,
    // so read today's dashboard first and fall back to it.
    session: (sessionId: string, branchId: string) =>
        L(`session:${sessionId}`, async () => {
            if (branchId) {
                const d = await ok(api.GET("/api/branches/{branchId}/dashboard/today", b(branchId))).catch(() => null);
                const row = d?.sessions.find((s) => s.id === sessionId);
                if (row) return row;
            }
            return ok(api.GET("/api/sessions/{sessionId}", { params: { path: { sessionId } } }));
        }),
    inventory: (branchId: string) => L(`inv:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/inventory", b(branchId)))),
    movements: (branchId: string, date: string) =>
        L(`moves:${branchId}:${date}`, () => ok(api.GET("/api/branches/{branchId}/inventory/movements", { params: { path: { branchId }, query: { date } } }))),
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
    reliefForBranch: (branchId: string, date: string) =>
        L(`relief:branch:${branchId}:${date}`, () => ok(api.GET("/api/relief-access", { params: { query: { branchId, date } } }))),
    branchInvites: (branchId: string) => L(`relief:binv:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/relief-invites", b(branchId)))),
    users: () => L(`users`, () => ok(api.GET("/api/users"))),
    roles: () => L(`roles`, () => ok(api.GET("/api/roles"))),
    branches: () => L(`branches`, () => ok(api.GET("/api/branches"))),
    assignments: (branchId: string) => L(`assign:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/assignments", b(branchId)))),
    rates: (branchId: string) => L(`rates:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/rates", b(branchId)))),
    delegates: (branchId: string) => L(`deleg:${branchId}`, () => ok(api.GET("/api/branches/{branchId}/delegates", b(branchId)))),
    auditTables: () => L(`audit:tables`, () => ok(api.GET("/api/audit-log/tables"))),
    auditFlagged: () => L(`audit:flagged`, () => ok(api.GET("/api/audit-log/flagged"))),
};

export function warm<T>(l: Loader<T>) {
    void fetchCached(l.key, l.fn).catch(() => {});
}
