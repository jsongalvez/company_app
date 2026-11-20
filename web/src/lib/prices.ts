/**
 * Session prices on the phone, so a new session is priced at once on bad wifi and with no signal:
 * the branch's rates as pulled for the day, and the last prices this phone saw. The server still
 * decides the visit type and price when the save arrives.
 */
import type { S } from "../api/client";
import { peek } from "./cache.svelte";
import { key as keys } from "./keys";

type P = S["SessionPreviewResponse"];
type T = NonNullable<P["sessionType"]>;
const key = (branchId: string) => `prices:${branchId}`;

function read(branchId: string): Partial<Record<T, string>> {
    try {
        return (JSON.parse(localStorage.getItem(key(branchId)) ?? "{}") as Partial<Record<T, string>>) ?? {};
    } catch {
        return {};
    }
}

export function rememberPrice(branchId: string, p: P) {
    if (!p.sessionType) return;
    localStorage.setItem(key(branchId), JSON.stringify({ ...read(branchId), [p.sessionType]: p.basePrice }));
}

type BranchType = S["BranchResponse"]["branchType"];

/** The branch's rates from the day's pull (`data.ts pullBranch`), over the prices this phone saw. */
function rates(branchId: string): Partial<Record<T, string>> {
    const pulled = peek<S["RateResponse"][]>(keys.rates(branchId)) ?? [];
    return { ...read(branchId), ...Object.fromEntries(pulled.map((r) => [r.sessionType, r.rate])) };
}

/** Same rule as the server (`SessionService.computeSessionType`), priced from the branch's rates. */
export function guessPreview(branchId: string, branchType: BranchType | undefined, c: S["ClientResponse"]): P | null {
    const visits = c.sessionCount + (c.earlierVisits ?? 0);
    const type: T =
        branchType === "MEDICAL_MISSION"
            ? "MEDICAL_MISSION"
            : visits === 0
              ? branchType === "PROVINCIAL_TOUR"
                  ? "PROVINCIAL_FIRST"
                  : "REGULAR"
              : visits === 1
                ? "SECOND_SESSION"
                : "SUBSEQUENT";
    const price = rates(branchId)[type];
    return price ? { sessionType: type, basePrice: price } : null;
}
