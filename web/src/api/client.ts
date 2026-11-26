import { nowIso, plain } from "../lib/format";
import createClient, { type Middleware } from "openapi-fetch";
import type { components, paths } from "./schema";
import { jittered } from "../lib/backoff";
import { net, onlineOrTimeout, reportNetFailure, reportNetOk } from "../lib/net.svelte";
import { canQueue, eager, enqueue as hold, mustQueue, newOp, OCCURRED_AT, OP_ID, onlyEagerAhead, waiting } from "../lib/outbox.svelte";
import { traceparent } from "../lib/telemetry";
import { answered, NOT_SENT } from "../lib/tables";

export type S = components["schemas"];

const TOKEN_KEY = "token";

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}
/** The long token from sign-in: after days without signal it buys a fresh [TOKEN_KEY] (`/auth/refresh`). */
const REPLAY_KEY = "replayToken";

export function setToken(token: string | null, replayToken?: string) {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
    if (replayToken) localStorage.setItem(REPLAY_KEY, replayToken);
    else if (!token) localStorage.removeItem(REPLAY_KEY);
}

/** Has the sign-in token run out (or will within a minute)? Then the replay token is needed before sending. */
export function tokenExpired(t = getToken()): boolean {
    if (!t) return false;
    try {
        const exp = (JSON.parse(atob(t.split(".")[1]!.replace(/-/g, "+").replace(/_/g, "/"))) as { exp?: number }).exp;
        return typeof exp === "number" && exp * 1000 < Date.now() + 60_000;
    } catch {
        return false;
    }
}

let refreshing: Promise<boolean | undefined> | null = null;
/**
 * Trade the replay token for a fresh sign-in. True: signed in again. False: the server said no
 * (sign-out, password change, over 7 days): the person must sign in. Undefined: no answer (no signal).
 */
export function refreshToken(): Promise<boolean | undefined> {
    refreshing ??= (async () => {
        const replayToken = localStorage.getItem(REPLAY_KEY);
        if (!replayToken) return false;
        try {
            const res = await fetch("/auth/refresh", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ replayToken }),
                signal: AbortSignal.timeout(15_000),
            });
            if (res.status === 401 || res.status === 400) return false;
            if (!res.ok) return undefined;
            const r = (await res.json()) as S["RefreshResponse"];
            setToken(r.token, r.replayToken);
            return true;
        } catch {
            return undefined;
        }
    })().finally(() => (refreshing = null));
    return refreshing;
}

/** Developer "View as": while set, every request runs as that person (server checks we're a superuser). */
export type ViewAs = { userId: string; name: string; roles: string[]; allowChanges: boolean; until: number; realName: string };
const VIEW_AS_KEY = "viewAs";
export function getViewAs(): ViewAs | null {
    try {
        const v = JSON.parse(sessionStorage.getItem(VIEW_AS_KEY) ?? "null") as ViewAs | null;
        return v && v.until > Date.now() ? v : null;
    } catch {
        return null;
    }
}
export function setViewAs(v: ViewAs | null) {
    if (v) sessionStorage.setItem(VIEW_AS_KEY, JSON.stringify(v));
    else sessionStorage.removeItem(VIEW_AS_KEY);
}
function viewAsHeaders(): Record<string, string> {
    const v = getViewAs();
    if (!v) return {};
    return v.allowChanges ? { "X-View-As": v.userId, "X-View-As-Changes": "1" } : { "X-View-As": v.userId };
}

/** Called when the server rejects our token; app state installs the handler. */
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(fn: () => void) {
    onUnauthorized = fn;
}

/** The last request the server refused, attached to problem reports so they're easy to trace. */
export const lastError: { value: { at: string; method: string; path: string; status: number; traceId: string | null } | null } = { value: null };

/** The last few answered requests with the server's trace id, so a report can be matched to the server log. */
export const recentRequests: { at: string; method: string; path: string; status: number; traceId: string | null }[] = [];
const RECENT_MAX = 5;
export const TRACE_HEADER = "X-Trace-Id";

const auth: Middleware = {
    onRequest({ request }) {
        const t = getToken();
        if (t) request.headers.set("Authorization", `Bearer ${t}`);
        request.headers.set("traceparent", traceparent());
        for (const [k, v] of Object.entries(viewAsHeaders())) request.headers.set(k, v);
        return request;
    },
    onResponse({ response, request }) {
        const path = new URL(request.url).pathname;
        const entry = { at: nowIso(), method: request.method, path, status: response.status, traceId: response.headers.get(TRACE_HEADER) };
        if (path !== "/api/feedback-notes") {
            recentRequests.push(entry);
            if (recentRequests.length > RECENT_MAX) recentRequests.shift();
            if (response.status >= 400 && response.status !== 401) lastError.value = entry;
        }
        if (response.status === 401 && path.startsWith("/api/")) onUnauthorized();
        // Before the caller takes the answer in, so what the answer brings counts as fresh.
        answered(request.method, response.headers);
        return response;
    },
};

/** A request that never reached the server (no signal) or got no answer in time. */
export class NetError extends Error {
    constructor(readonly timedOut: boolean) {
        super(timedOut ? "timeout" : "offline");
    }
}

/** Request header marks carry: the status the row showed, so a queued mark can tell it undoes the one before. */
export const BASE_STATUS = "X-Base-Status";

const RETRY_WAITS_MS = [700, 2000, 5000];
/** Bad wifi hangs rather than fails: a read gives up in time and the page keeps what it had. */
const TIMEOUT_MS = 12_000;
/** A save that can wait on the phone gives up sooner: holding it beats a frozen button. */
const SAVE_TIMEOUT_MS = 6_000;
const GATEWAY = new Set([502, 503, 504]);
/** Body keys holding the record's own client-made id: the server treats a resend as the same save. */
const OWN_ID_KEYS = ["id", "attendanceId", "requestId", "delegateId", "movementId"];

/** Safe to send twice? Reads, PUT/DELETE, and creates carrying their own id. */
function repeatable(method: string, body: string | null): boolean {
    if (method === "GET" || method === "HEAD" || method === "PUT" || method === "DELETE") return true;
    if (!body) return false;
    try {
        const o = JSON.parse(body) as Record<string, unknown>;
        return !!o && typeof o === "object" && OWN_ID_KEYS.some((k) => typeof o[k] === "string");
    } catch {
        return false;
    }
}

/** Saves that freeze a day's totals. */
const FREEZES = /^\/api\/remittances/;

/** A save that needs the server failed for want of signal: `detail` is { method, path }. The recorder logs it by screen. */
export const OFFLINE_REFUSED = "app:offline-refused";
export type OfflineRefused = { method: string; path: string };
function offlineRefused(method: string, path: string) {
    window.dispatchEvent(new CustomEvent<OfflineRefused>(OFFLINE_REFUSED, { detail: { method, path } }));
}

function refusedHere(method: string, path: string, status: number, error: string): Response {
    offlineRefused(method, path);
    return new Response(JSON.stringify({ error }), { status, headers: { "Content-Type": "application/json", [NOT_SENT]: "1" } });
}

/**
 * fetch for weak signal: every request gets a time limit, and requests that are safe to
 * resend are retried a few times (waiting for the phone to come back online), so short
 * drops go unnoticed. Anything else fails once with a clear message.
 */
async function resilientFetch(req: Request): Promise<Response> {
    const body = req.method === "GET" || req.method === "HEAD" ? null : await req.clone().text();
    const url = new URL(req.url);
    const path = url.pathname;
    const query = url.search.slice(1) || undefined;
    // The page says what status a mark starts from (the server never sees this header).
    const shown = req.headers.get(BASE_STATUS) ?? undefined;
    req.headers.delete(BASE_STATUS);
    // A remittance freezes the day's totals: it must not go out ahead of saves still on this phone.
    if (FREEZES.test(path) && req.method !== "GET" && waiting().length)
        return refusedHere(req.method, path, 409, `Send the ${waiting().length} waiting changes first, then try again.`);
    const queueable = canQueue(req.method, path);
    // One id and one time per save, from the tap: the live try and every resend from the queue carry the
    // same ones, so a try that reached the server but lost its answer is not done again.
    const op = queueable ? newOp() : undefined;
    if (op) {
        req.headers.set(OP_ID, op.id);
        req.headers.set(OCCURRED_AT, op.at);
    }
    const enqueue = (m: string, p: string, b: string, sh?: string, q?: string, held = true) => hold(m, p, b, sh, q, held, op);
    // Saves already waiting for signal go first: this one lines up behind them.
    if (mustQueue(req.method, path)) return enqueue(req.method, path, body ?? "", shown, query, net.trouble || !navigator.onLine || !onlyEagerAhead());
    // Signal already known to be bad: hold the save now rather than make the person wait.
    if (queueable && (net.trouble || !navigator.onLine)) return enqueue(req.method, path, body ?? "", shown, query);
    // New sessions and sales show at once; the queue sends them now and keeps them if that fails.
    if (eager(req.method, path)) return enqueue(req.method, path, body ?? "", shown, query, false);
    // Retry only while the signal still looks fine: once it's known to be bad, answer at once
    // (pages show what they had and reload by themselves when the connection is back).
    const knownBad = net.trouble;
    const retry = repeatable(req.method, body) && !knownBad;
    for (let attempt = 0; ; attempt++) {
        const last = !retry || attempt >= RETRY_WAITS_MS.length;
        let res: Response;
        try {
            const signal = AbortSignal.timeout(queueable ? SAVE_TIMEOUT_MS : TIMEOUT_MS);
            res = await fetch(req.clone(), { signal });
        } catch (e) {
            if (req.signal.aborted) throw e;
            reportNetFailure();
            // Saves that can wait are held at the first failure: the queue resends them itself,
            // and each carries its own id or version, so a copy that did arrive isn't doubled.
            if (queueable) return enqueue(req.method, path, body ?? "", shown, query);
            if (req.method !== "GET" && req.method !== "HEAD") offlineRefused(req.method, path);
            // The phone says it has no signal: say so now; waiting out retries only feels slow.
            if (!navigator.onLine || knownBad) throw new NetError(false);
            if (last) throw new NetError(navigator.onLine || (e instanceof DOMException && e.name === "TimeoutError"));
            await onlineOrTimeout(jittered(RETRY_WAITS_MS[attempt]!));
            continue;
        }
        if (GATEWAY.has(res.status) && !last) {
            await onlineOrTimeout(jittered(RETRY_WAITS_MS[attempt]!));
            continue;
        }
        if (GATEWAY.has(res.status) && canQueue(req.method, path)) return enqueue(req.method, path, body ?? "", shown, query);
        reportNetOk();
        return res;
    }
}

export const api = createClient<paths>({ baseUrl: location.origin, fetch: resilientFetch });
api.use(auth);

export class ApiError extends Error {
    override readonly name = "ApiError";

    constructor(
        readonly status: number,
        message: string,
    ) {
        super(message);
    }
}

function messageFor(status: number, body: unknown): string {
    if (body && typeof body === "object" && "error" in body && typeof body.error === "string") return plain(body.error);
    if (status === 0) return "No connection, so this didn't go through. Try again when you have signal.";
    if (status === -1) return "The connection dropped before the server answered. Reload to check, then try again if it's missing.";
    if (status === 401) return "You were signed out. Please sign in again.";
    if (status === 403) return "You don't have access to this";
    if (status === 404) return "Not found";
    if (status === 409) return "Someone else changed this just now. Reload and try again.";
    if (status === 429) return "Too many tries. Wait a minute and try again.";
    return `Something went wrong on the server (${status}). Try again.`;
}

/** Unwrap an openapi-fetch result: data, or throw ApiError with the server's message. */
export async function ok<T>(p: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
    let r;
    try {
        r = await p;
    } catch (e) {
        const code = e instanceof NetError && e.timedOut ? -1 : 0;
        throw new ApiError(code, messageFor(code, null));
    }
    if (r.response.ok) return r.data as T;
    throw new ApiError(r.response.status, messageFor(r.response.status, r.error));
}

export function errText(e: unknown): string {
    return e instanceof Error ? e.message : String(e);
}

/** Client-generated ids (the API is idempotent on them). Works without a secure context. */
export function uuid(): string {
    if (crypto.randomUUID && isSecureContext) return crypto.randomUUID();
    const b = crypto.getRandomValues(new Uint8Array(16));
    b[6] = (b[6]! & 0x0f) | 0x40;
    b[8] = (b[8]! & 0x3f) | 0x80;
    const h = [...b].map((x) => x.toString(16).padStart(2, "0")).join("");
    return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

/** Authenticated file download (exports). */
export async function download(path: string, query: Record<string, string>) {
    const url = new URL(path, location.origin);
    for (const [k, v] of Object.entries(query)) url.searchParams.set(k, v);
    const res = await fetch(url, { headers: { Authorization: `Bearer ${getToken() ?? ""}`, ...viewAsHeaders() } });
    if (!res.ok) {
        let body: unknown = null;
        try {
            body = await res.json();
        } catch {
            /* not json */
        }
        throw new ApiError(res.status, messageFor(res.status, body));
    }
    const cd = res.headers.get("Content-Disposition") ?? "";
    const name = /filename="?([^";]+)"?/.exec(cd)?.[1] ?? `export.${query.format ?? "csv"}`;
    const a = document.createElement("a");
    a.href = URL.createObjectURL(await res.blob());
    a.download = name;
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 10_000);
}
