/**
 * The last session prices this phone saw per branch and visit type, so a new session can be
 * priced with no signal. The server still decides the visit type when the save arrives.
 */
import type { S } from "../api/client";

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
    if (!p.sessionType || p.sessionType === "UNKNOWN") return;
    localStorage.setItem(key(branchId), JSON.stringify({ ...read(branchId), [p.sessionType]: p.basePrice }));
}

/** Same rule as the server: no earlier visits is a regular first visit, one is a second session. */
export function guessPreview(branchId: string, c: S["ClientResponse"]): P | null {
    const visits = c.sessionCount + (c.earlierVisits ?? 0);
    const seen = read(branchId);
    // Mission branches price every visit the same; provincial tours have their own first visit.
    const type: T = seen.MEDICAL_MISSION
        ? "MEDICAL_MISSION"
        : visits === 0
          ? seen.PROVINCIAL_FIRST
              ? "PROVINCIAL_FIRST"
              : "REGULAR"
          : visits === 1
            ? "SECOND_SESSION"
            : "SUBSEQUENT";
    const price = seen[type];
    return price ? { sessionType: type, basePrice: price } : null;
}
