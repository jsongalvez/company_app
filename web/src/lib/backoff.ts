/**
 * Waits between tries of a call that failed. Each wait doubles up to [capMs] and is drawn from its upper half, so
 * phones that lost the server together don't all come back in the same second ("equal jitter").
 */
export function backoff(attempt: number, baseMs: number, capMs: number, rand: () => number = Math.random): number {
    return jittered(Math.min(capMs, baseMs * 2 ** attempt), rand);
}

export function jittered(ms: number, rand: () => number = Math.random): number {
    return ms / 2 + (rand() * ms) / 2;
}
