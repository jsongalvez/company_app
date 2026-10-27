/**
 * Reads asked for in the same moment travel as one request. On a weak hotspot every request costs time
 * (8 reads: 650 ms alone each, 335 ms together on the test phone), and the server answers each path exactly as it would alone.
 */
const MAX_PATHS = 16;
const BATCH_PATH = "/api/batch";
const SAME_PERSON = ["Authorization", "X-View-As", "X-View-As-Changes"];

type Waiting = { req: Request; signal: AbortSignal; resolve: (r: Response) => void; reject: (e: unknown) => void };
type Answer = { status: number; body: string; traceId?: string | null };

let queue: Waiting[] = [];
let timer: ReturnType<typeof setTimeout> | undefined;

/** A read of this app's API that may share a request with others. */
export function canBatch(req: Request): boolean {
    if (req.method !== "GET") return false;
    const path = new URL(req.url).pathname;
    return path.startsWith("/api/") && path !== BATCH_PATH;
}

/** Send the read now, or together with the reads asked for in the same moment. */
export function batchedFetch(req: Request, signal: AbortSignal): Promise<Response> {
    return new Promise((resolve, reject) => {
        signal.addEventListener("abort", () => reject(signal.reason), { once: true });
        queue.push({ req, signal, resolve, reject });
        timer ??= setTimeout(flush, 0);
    });
}

function flush() {
    timer = undefined;
    const waiting = queue;
    queue = [];
    const byPerson = new Map<string, Waiting[]>();
    for (const w of waiting) {
        const key = SAME_PERSON.map((h) => w.req.headers.get(h) ?? "").join("\n");
        byPerson.set(key, [...(byPerson.get(key) ?? []), w]);
    }
    for (const group of byPerson.values()) for (let i = 0; i < group.length; i += MAX_PATHS) void send(group.slice(i, i + MAX_PATHS));
}

const alone = (w: Waiting) => fetch(w.req.clone(), { signal: w.signal }).then(w.resolve, w.reject);

async function send(group: Waiting[]) {
    if (group.length === 1) return alone(group[0]!);
    const headers = new Headers({ "Content-Type": "application/json" });
    for (const h of SAME_PERSON) {
        const v = group[0]!.req.headers.get(h);
        if (v) headers.set(h, v);
    }
    const paths = group.map((w) => {
        const u = new URL(w.req.url);
        return u.pathname + u.search;
    });
    let res: Response;
    try {
        res = await fetch(BATCH_PATH, { method: "POST", headers, body: JSON.stringify({ paths }), signal: AbortSignal.timeout(20_000) });
    } catch (e) {
        for (const w of group) w.reject(e);
        return;
    }
    // The batch itself was refused (signed out, server trouble): each read asks alone and gets its own real answer.
    if (!res.ok) return void group.forEach(alone);
    const results = ((await res.json()) as { results: Answer[] }).results;
    group.forEach((w, i) => {
        const a = results[i];
        if (!a) return void alone(w);
        const noBody = a.status === 204 || a.status === 205 || a.body === "";
        const out = new Headers(a.body ? { "Content-Type": "application/json" } : {});
        if (a.traceId) out.set("X-Trace-Id", a.traceId);
        w.resolve(new Response(noBody ? null : a.body, { status: a.status, headers: out }));
    });
}
