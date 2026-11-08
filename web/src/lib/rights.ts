// What the server decided the caller may do (Rights.of, sent by /api/start and /api/me/capabilities).
// The web looks answers up and decides nothing: web/tests/unit/rights.test.ts runs this lookup over
// rights.vectors.json, which the server's WebRightsVectorsTest writes from Access.allows.
import type { S } from "../api/client";

export type Rights = S["RightsResponse"];
export type Act = S["ActsResponse"]["may"][number];
/** Where an action happens: a branch, one day of it, or neither (everywhere). */
export type Place = { branch?: string; day?: string };

export const NO_RIGHTS: Rights = { everywhere: { may: [], reads: [] }, branches: [], days: [] };

/** A place holds what the person holds everywhere, plus what its branch adds, plus what its day adds. */
function levels(r: Rights, at: Place): S["ActsResponse"][] {
    const branch = at.branch ? r.branches.find((b) => b.id === at.branch) : undefined;
    const day = at.branch && at.day ? r.days.find((d) => d.id === at.day) : undefined;
    return [r.everywhere, ...(branch ? [branch] : []), ...(day ? [day] : [])];
}

export function allows(r: Rights, act: Act, at: Place): boolean {
    return levels(r, at).some((l) => l.may.includes(act));
}

/** A GET of an Act's routes: the server also lets through whoever may do its reading Act. */
export function reads(r: Rights, act: Act, at: Place): boolean {
    return levels(r, at).some((l) => l.reads.includes(act));
}

/** Staff of the branch, or relief on a day: the people the server shows a branch's day to. */
export function worksAt(r: Rights, branch: string): boolean {
    return r.days.length > 0 || r.branches.some((b) => b.id === branch);
}
