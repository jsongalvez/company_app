// Which grant rows count for a code. The server sends the rows (/api/me/capabilities); this is the one place
// the web decides whether a row applies, and it follows the server: edit, void, remit and commission are honoured
// only from a branch (or a day) grant, so an all-branch row must not switch them on. Pay is honoured from an
// all-branch grant too: the Owner pays at every branch (owed #25).
// The tests in web/tests/unit/grants.test.ts and FinanceReadBackAuthzTest.kt pin the two sides together.
export type Grant = { capabilityCode: string; contextType?: string; contextId: string };

/** Codes the server accepts from an all-branch (GLOBAL) grant. Any other code needs a branch or day grant. */
export const GLOBAL_CODES: ReadonlySet<string> = new Set(["VIEW_BRANCH_DATA", "ASSIGN_COMPENSATION", "MANAGE_USERS", "ASSIGN_DELEGATE", "MANAGE_CATALOG"]);

const BRANCH_LIKE = new Set(["BRANCH", "MEDICAL_MISSION", "PROVINCIAL_TOUR"]);

/** Does one grant row apply to `code` at the branch (and day)? */
export function applies(c: Grant, code: string, branchId: string, branchDayId: string | undefined, todayDayId: string | undefined): boolean {
    if (c.capabilityCode !== code) return false;
    if (c.contextType === "GLOBAL") return GLOBAL_CODES.has(code);
    if (BRANCH_LIKE.has(c.contextType ?? "")) return c.contextId === branchId;
    return c.contextType === "BRANCH_DAY" && (!branchDayId || c.contextId === branchDayId) && c.contextId === (branchDayId ?? todayDayId);
}
