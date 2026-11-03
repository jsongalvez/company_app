// Whether the server would let the caller do an Act at a place, from the grant rows it sent (/api/me/capabilities).
// This mirrors Access.contextsFor on the server; web/tests/unit/grants.test.ts checks it against REACH_CASES,
// which WebAccessTableTest renders from the server's own function.
import { ACTS, type Act } from "./access.gen";

export type Grant = { capabilityCode: string; contextType?: string; contextId: string };
/** Where an action happens: a branch, one day of it, or neither (everywhere). */
export type Place = { branch?: string; day?: string };

function counts(c: Grant, act: Act, at: Place): boolean {
    const { code, reach } = ACTS[act];
    if (c.capabilityCode !== code) return false;
    if (reach === "ANYWHERE") return true;
    const everywhere = reach === "EVERYWHERE" || reach === "HOME_OR_EVERYWHERE" || reach === "HOME_OR_EVERYWHERE_OR_DAY";
    const home = reach !== "EVERYWHERE";
    const day = reach === "HOME_OR_DAY" || reach === "HOME_OR_EVERYWHERE_OR_DAY";
    if (c.contextType === "GLOBAL") return everywhere;
    if (c.contextType === "BRANCH") return home && !!at.branch && c.contextId === at.branch;
    if (c.contextType === "BRANCH_DAY") return day && !!at.branch && !!at.day && c.contextId === at.day;
    return false;
}

export function allows(rows: readonly Grant[], act: Act, at: Place): boolean {
    return rows.some((c) => counts(c, act, at));
}

/** A GET of an Act's routes: the server also lets through whoever may do its reading Act (Act.whenReading). */
export function reads(rows: readonly Grant[], act: Act, at: Place): boolean {
    return allows(rows, act, at) || allows(rows, ACTS[act].read as Act, at);
}
