import { plain } from "./format";

/**
 * What the queue does with the server's answer to a held save. The server makes every queued
 * save safe to send twice, so the queue never judges a save: it sends it again until the server
 * answers for good.
 *  - retry:   the server can't answer yet (signed out, busy, down); keep it and stop for now.
 *  - applied: the server has it (a repeat of an earlier send counts too).
 *  - failed:  the server refused it; show why and let the person decide.
 */
export type Verdict = { kind: "retry" } | { kind: "applied" } | { kind: "failed"; message: string };

export function classify(status: number, body: unknown): Verdict {
    if (status >= 200 && status < 300) return { kind: "applied" };
    if (status === 401 || status === 429 || status >= 500) return { kind: "retry" };
    return { kind: "failed", message: refusal(status, body) };
}

function refusal(status: number, body: unknown): string {
    if (body && typeof body === "object" && "error" in body && typeof body.error === "string") return plain(body.error);
    if (status === 409) return "Someone changed this while you were offline.";
    if (status === 403) return "You no longer have access to do this.";
    if (status === 404) return "It no longer exists.";
    return `The server refused it (${status}).`;
}

/** The JSON a save carries, read once. A body that isn't an object reads as empty. */
export function parseBody(body: string): Record<string, unknown> {
    try {
        const v: unknown = JSON.parse(body);
        return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : {};
    } catch {
        return {};
    }
}
