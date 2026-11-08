/**
 * Every cache key, and every prefix a save drops, is built here. A key is a family name and its parts joined by ":".
 * Called with fewer parts, a builder gives the prefix for all keys that start with them; with none, the whole family.
 * Each builder is named after its loader in data.ts. Raw strings are not keys, so a typo fails typecheck.
 */
declare const brand: unique symbol;
export type Key = string & { readonly [brand]: true };

export const FAMILIES = [
    "allow",
    "assign",
    "audit",
    "bday",
    "bookings",
    "boot",
    "branches",
    "categories",
    "client",
    "clients",
    "comp",
    "concerns",
    "dash",
    "dayusers",
    "deleg",
    "dsess",
    "dsum",
    "dsums",
    "exp",
    "inv",
    "me",
    "members",
    "moves",
    "msum",
    "notif",
    "products",
    "rates",
    "relief",
    "rem",
    "remd",
    "roles",
    "roster",
    "sales",
    "session",
    "split",
    "users",
] as const;
export type Family = (typeof FAMILIES)[number];

type Part = string | number | undefined;
/** Trailing parts left out make a prefix; a family with no parts ends in ":", so "dsum:" never matches "dsums:". */
function k(family: Family, ...parts: Part[]): Key {
    while (parts.length && parts[parts.length - 1] === undefined) parts.pop();
    return (parts.length ? [family, ...parts].join(":") : `${family}:`) as Key;
}
const exact = (family: Family) => family as Key;

/** Every key of a family. */
export const all = (family: Family): Key => k(family);
/** Every cache key: a change of rights can change any answer. */
export const EVERY_KEY = "" as Key;

type Id = string | undefined;
export const key = {
    dashboard: (branchId?: Id, date?: Id) => k("dash", branchId, date),
    earlierWaiting: (branchId?: Id, date?: Id) => k("dash", "waiting", branchId, date),
    branchDay: (branchId?: Id, date?: Id) => k("bday", branchId, date),
    daySessions: (branchId?: Id, date?: Id) => k("dsess", branchId, date),
    roster: (branchId?: Id, date?: Id) => k("roster", branchId, date),
    members: (branchId?: Id) => k("members", branchId),
    dayUsers: (branchDayId?: Id) => k("dayusers", branchDayId),
    concerns: () => exact("concerns"),
    clients: (q?: Id) => k("clients", q),
    /** "clients:" can't clash with a search (2+ letters) and still drops with the clients family. */
    recentClients: () => k("clients", ""),
    client: (clientId?: Id) => k("client", clientId),
    bookings: (branchId?: Id) => k("bookings", branchId),
    clientBookings: (clientId: string) => k("bookings", "client", clientId),
    clientHistory: (clientId: string) => k("client", clientId, "history"),
    session: (sessionId?: Id) => k("session", sessionId),
    inventory: (branchId?: Id) => k("inv", branchId),
    movements: (branchId?: Id, date?: Id) => k("moves", branchId, date),
    products: () => exact("products"),
    categories: () => exact("categories"),
    productSales: (branchDayId?: Id) => k("sales", branchDayId),
    expenses: (branchDayId?: Id) => k("exp", branchDayId),
    compensations: (branchDayId?: Id) => k("comp", branchDayId),
    allowances: (branchDayId?: Id) => k("allow", branchDayId),
    commissionSplits: (branchDayId?: Id) => k("split", branchDayId),
    dailySummary: (branchId?: Id, date?: Id) => k("dsum", branchId, date),
    dailySummaries: (branchId?: Id, from?: Id, to?: Id) => k("dsums", branchId, from, to),
    monthly: (branchId: string, year: number, month: number) => k("msum", branchId, `${year}-${month}`),
    remittances: (branchId?: Id) => k("rem", branchId),
    remittance: (remittanceId?: Id) => k("remd", remittanceId),
    notifications: () => exact("notif"),
    reliefMine: () => k("relief", "mine"),
    reliefInvitesMine: () => k("relief", "invites"),
    reliefOptions: () => k("relief", "options"),
    reliefUpcoming: (branchId: string) => k("relief", "branch", branchId, "upcoming"),
    reliefForBranch: (branchId: string, date: string) => k("relief", "branch", branchId, date),
    branchInvites: (branchId: string) => k("relief", "binv", branchId),
    users: () => exact("users"),
    roles: () => exact("roles"),
    branches: () => exact("branches"),
    assignments: (branchId?: Id) => k("assign", branchId),
    rates: (branchId?: Id) => k("rates", branchId),
    delegates: (branchId?: Id) => k("deleg", branchId),
    myHistory: (from: string, to: string) => k("me", "history", from, to),
    /** With id "", every row of that table. */
    audit: (tableName?: Id, recordId?: Id) => k("audit", tableName, recordId),
    auditTables: () => k("audit", "tables"),
    auditFlagged: () => k("audit", "flagged"),
    /** What the start bundle carried, kept so the app opens with it. */
    boot: (what: "me" | "rights" | "branches" | "accessible" | "shift") => k("boot", what),
    bootToday: (branchId: string, date: string) => k("boot", "today", branchId, date),
};
