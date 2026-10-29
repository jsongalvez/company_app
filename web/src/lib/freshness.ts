/** How current the data on screen is. `at` is when that data came from the server. */
export type Freshness = { kind: "saved" | "updating" | "fresh" | "failed"; at: number };

export type FreshEvent = { type: "saved"; at: number } | { type: "slow" } | { type: "answer"; at: number } | { type: "lost" } | { type: "none" };

/** Shown only when the answer takes longer than this; most refreshes answer sooner (prod p50 142ms). */
export const SLOW_MS = 300;

export function step(s: Freshness | null, e: FreshEvent): Freshness | null {
    switch (e.type) {
        case "saved":
            return { kind: "saved", at: e.at };
        case "slow":
            return s?.kind === "saved" ? { kind: "updating", at: s.at } : s;
        case "answer":
            return { kind: "fresh", at: e.at };
        case "lost":
            return s && s.kind !== "fresh" ? { kind: "failed", at: s.at } : s;
        case "none":
            return null;
    }
}

const WORDS: Record<Freshness["kind"], string> = { saved: "Updated", updating: "Updating…", fresh: "Updated", failed: "Offline, showing" };

/** The two halves of the top bar line: what happened, and the time of the data shown. */
export function freshLine(s: Freshness | null, time: (ms: number) => string): [string, string] {
    if (!s) return ["", ""];
    return [WORDS[s.kind], s.kind === "updating" ? "" : time(s.at)];
}
