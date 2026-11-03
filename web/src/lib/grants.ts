// Which grant rows count for a code. The server sends the rows (/api/me/capabilities); this is the one place
// the web decides whether a row applies, and it follows the server: edit, void, remit and commission are honoured
// only from a branch (or a day) grant, so an all-branch row must not switch them on. Pay is honoured from an
// all-branch grant too: the Owner pays at every branch (owed #25).
// Codes and reaches come from the server's Act table (access.gen.ts, rendered by WebAccessTableTest).
import { ACTS, type Code, type Reach } from "./access.gen";

export type Grant = { capabilityCode: string; contextType?: string; contextId: string };

const ALL_BRANCH: ReadonlySet<Reach> = new Set(["EVERYWHERE", "HOME_OR_EVERYWHERE", "HOME_OR_EVERYWHERE_OR_DAY"]);

/** Codes some server Act accepts from an all-branch (GLOBAL) grant. Any other code needs a branch or day grant. */
const GLOBAL_CODES: ReadonlySet<Code> = new Set(Object.values(ACTS).flatMap((a) => (ALL_BRANCH.has(a.reach) ? [a.code] : [])));

const BRANCH_LIKE = new Set(["BRANCH", "MEDICAL_MISSION", "PROVINCIAL_TOUR"]);

/** Does one grant row apply to `code` at the branch (and day)? */
export function applies(c: Grant, code: Code, branchId: string, branchDayId: string | undefined, todayDayId: string | undefined): boolean {
    if (c.capabilityCode !== code) return false;
    if (c.contextType === "GLOBAL") return GLOBAL_CODES.has(code);
    if (BRANCH_LIKE.has(c.contextType ?? "")) return c.contextId === branchId;
    return c.contextType === "BRANCH_DAY" && (!branchDayId || c.contextId === branchDayId) && c.contextId === (branchDayId ?? todayDayId);
}
