import { plain } from "../lib/format";
import createClient, { type Middleware } from "openapi-fetch";
import type { components, paths } from "./schema";
import { net, onlineOrTimeout, reportNetFailure, reportNetOk } from "../lib/net.svelte";
import { canQueue, enqueue, mustQueue } from "../lib/outbox.svelte";

export type S = components["schemas"];

const TOKEN_KEY = "token";

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}
export function setToken(token: string | null) {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
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
export const lastError: { value: { at: string; method: string; path: string; status: number } | null } = { value: null };

const auth: Middleware = {
    onRequest({ request }) {
        const t = getToken();
        if (t) request.headers.set("Authorization", `Bearer ${t}`);
        for (const [k, v] of Object.entries(viewAsHeaders())) request.headers.set(k, v);
        return request;
    },
    onResponse({ response, request }) {
        const path = new URL(request.url).pathname;
        if (response.status >= 400 && response.status !== 401 && path !== "/api/feedback-notes")
            lastError.value = { at: new Date().toISOString(), method: request.method, path, status: response.status };
        if (response.status === 401 && new URL(request.url).pathname.startsWith("/api/")) onUnauthorized();
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
const TIMEOUT_MS = 20_000;
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

/**
 * fetch for weak signal: every request gets a time limit, and requests that are safe to
 * resend are retried a few times (waiting for the phone to come back online), so short
 * drops go unnoticed. Anything else fails once with a clear message.
 */
async function resilientFetch(req: Request): Promise<Response> {
    const body = req.method === "GET" || req.method === "HEAD" ? null : await req.clone().text();
    const path = new URL(req.url).pathname;
    // The page says what status a mark starts from (the server never sees this header).
    const shown = req.headers.get(BASE_STATUS) ?? undefined;
    req.headers.delete(BASE_STATUS);
    // Saves already waiting for signal go first: this one lines up behind them.
    if (mustQueue(req.method, path)) return enqueue(req.method, path, body ?? "", shown);
    const queueable = canQueue(req.method, path);
    // Signal already known to be bad: hold the save now rather than make the person wait.
    if (queueable && (net.trouble || !navigator.onLine)) return enqueue(req.method, path, body ?? "", shown);
    // Retry only while the signal still looks fine: once it's known to be bad, answer at once
    // (pages show what they had and reload by themselves when the connection is back).
    const knownBad = net.trouble;
    const retry = repeatable(req.method, body) && !knownBad;
    for (let attempt = 0; ; attempt++) {
        const last = !retry || attempt >= RETRY_WAITS_MS.length;
        let res: Response;
        try {
            res = await fetch(req.clone(), { signal: AbortSignal.timeout(queueable ? SAVE_TIMEOUT_MS : TIMEOUT_MS) });
        } catch (e) {
            if (req.signal.aborted) throw e;
            reportNetFailure();
            // Saves that can wait are held at the first failure: the queue resends them itself,
            // and each carries its own id or version, so a copy that did arrive isn't doubled.
            if (queueable) return enqueue(req.method, path, body ?? "", shown);
            // The phone says it has no signal: say so now; waiting out retries only feels slow.
            if (!navigator.onLine || knownBad) throw new NetError(false);
            if (last) throw new NetError(navigator.onLine || (e instanceof DOMException && e.name === "TimeoutError"));
            await onlineOrTimeout(RETRY_WAITS_MS[attempt]!);
            continue;
        }
        if (GATEWAY.has(res.status) && !last) {
            await onlineOrTimeout(RETRY_WAITS_MS[attempt]!);
            continue;
        }
        if (GATEWAY.has(res.status) && canQueue(req.method, path)) return enqueue(req.method, path, body ?? "", shown);
        reportNetOk();
        return res;
    }
}

export const api = createClient<paths>({ baseUrl: location.origin, fetch: resilientFetch });
api.use(auth);

export class ApiError extends Error {
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
