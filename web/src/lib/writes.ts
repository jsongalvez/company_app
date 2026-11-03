/**
 * What each change makes out of date in the cache. A save calls `wrote(name, ...)` from cache.svelte.ts;
 * nothing else drops cache keys. Add a row here when a new save changes what a loader in data.ts reads.
 *
 * stale: the next read asks again, what's on screen keeps showing meanwhile (and stays with no signal).
 * reread: screens showing these keys ask again now, in place.
 * forget: drop the keys, on the phone too, and reload screens showing them: for data no longer allowed.
 */
import { EVERY_KEY, all, key, type Key } from "./keys";

export type Mode = "stale" | "reread" | "forget";
type Row<A extends unknown[]> = { mode: Mode; keys: (...a: A) => Key[] };
const stale = <A extends unknown[]>(keys: (...a: A) => Key[]): Row<A> => ({ mode: "stale", keys });

export const writes = {
    /** Rights or branches changed: any answer may differ. */
    rights: stale(() => [EVERY_KEY]),
    /** The clock change's answer carried the new Today: refresh the on-duty screens in place. */
    clockApplied: { mode: "reread", keys: () => [all("dash"), all("roster")] } as Row<[]>,
    /** A clock-out held on the phone: Today is no longer this person's to see. */
    clockOutHeld: { mode: "forget", keys: () => [all("dash"), all("roster")] } as Row<[]>,

    sessionAdded: stale((clientId: string, sessionId: string) => [
        all("dash"),
        all("dsess"),
        all("dsum"),
        all("clients"),
        key.client(clientId),
        key.session(sessionId),
    ]),
    sessionChanged: stale((sessionId: string) => [key.session(sessionId), all("dash"), all("dsess"), key.audit("session", sessionId)]),
    /** Today's own lists after a save or a mark. */
    todayLists: stale((branchId: string) => [key.dashboard(branchId), key.roster(branchId), key.bookings(branchId)]),
    /** An earlier day's lists after a save or a mark; the first add to a day makes its row. */
    pastDayLists: stale((branchId: string, date: string) => [
        key.branchDay(branchId, date),
        key.daySessions(branchId, date),
        key.dailySummary(branchId, date),
        key.earlierWaiting(branchId),
    ]),
    dayRow: stale((branchId: string, date: string) => [key.branchDay(branchId, date)]),

    clientAdded: stale(() => [all("clients")]),
    clientEdited: stale((clientId: string) => [key.client(clientId), all("clients"), key.audit("client", clientId)]),
    clientAnonymized: stale((clientId: string) => [key.client(clientId), all("clients")]),
    concerns: stale(() => [key.concerns()]),
    bookings: stale(() => [all("bookings")]),

    /** branchDayId left out: every day's sales. */
    productSale: stale((branchId: string, branchDayId?: string) => [
        key.productSales(branchDayId),
        key.inventory(branchId),
        key.dashboard(branchId),
        all("dsess"),
        all("dsum"),
        all("moves"),
        key.audit("product_sale", ""),
    ]),
    stock: stale((branchId: string) => [key.inventory(branchId), key.movements(branchId)]),
    catalog: stale(() => [key.products(), key.categories(), all("inv")]),

    finance: stale((branchId: string, date: string, branchDayId: string, lists: ("exp" | "split" | "summary")[]) => [
        ...(lists.includes("exp") ? [key.expenses(branchDayId)] : []),
        ...(lists.includes("split") ? [key.commissionSplits(branchDayId)] : []),
        ...(lists.includes("summary") ? [key.dailySummary(branchId, date)] : []),
        ...(lists.includes("exp") ? [key.audit("expense", "")] : []),
    ]),
    pay: stale((list: "comp" | "allow", branchDayId: string) => [list === "comp" ? key.compensations(branchDayId) : key.allowances(branchDayId)]),

    remittances: stale((branchId: string) => [key.remittances(branchId)]),
    remittance: stale((remittanceId: string) => [key.remittance(remittanceId), all("rem")]),
    /** A remittance sent changes the day's totals and every summary range. */
    remitted: stale(() => [all("dash"), all("dsum"), all("dsums")]),

    relief: stale(() => [all("relief")]),
    branches: stale(() => [key.branches()]),
    assignments: stale((branchId: string, usersToo: boolean) => [key.assignments(branchId), ...(usersToo ? [key.users()] : [])]),
    rates: stale((branchId: string) => [key.rates(branchId)]),
    delegates: stale((branchId: string) => [key.delegates(branchId)]),
    users: stale(() => [key.users(), all("assign")]),
    notifications: stale(() => [key.notifications()]),
    auditFlags: stale(() => [key.auditFlagged()]),
};

export type Write = keyof typeof writes;
export type WriteArgs<W extends Write> = Parameters<(typeof writes)[W]["keys"]>;
