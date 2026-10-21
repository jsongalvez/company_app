import { operationalDate } from "./format";

/** Paths whose body takes a `reason`, which the server asks for on any change to an earlier day. */
const TAKES_REASON = [/^\/api\/sessions$/, /^\/api\/sessions\/[^/]+\/status$/, /^\/api\/product-sales$/];

/**
 * A save made on one day and sent on a later one (signal came back after the day turned over) hits
 * the server's earlier-day rule, which wants a reason. It was a normal save when it was made, so
 * say so; people without the right to change earlier days are still refused.
 */
export function replayBody(i: { path: string; body: string; at: string }, now = new Date()): string {
    if (!i.body || !TAKES_REASON.some((r) => r.test(i.path))) return i.body;
    const madeOn = operationalDate(new Date(i.at));
    const sentOn = operationalDate(now);
    if (madeOn >= sentOn) return i.body;
    try {
        const o = JSON.parse(i.body) as Record<string, unknown>;
        if (typeof o.reason === "string" && o.reason) return i.body;
        return JSON.stringify({ ...o, reason: `Saved offline on ${madeOn}, sent on ${sentOn}` });
    } catch {
        return i.body;
    }
}
