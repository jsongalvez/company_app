import createClient, { type Middleware } from "openapi-fetch";
import type { components, paths } from "./schema";

export type S = components["schemas"];

const TOKEN_KEY = "token";

export function getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
}
export function setToken(token: string | null) {
    if (token) localStorage.setItem(TOKEN_KEY, token);
    else localStorage.removeItem(TOKEN_KEY);
}

/** Called when the server rejects our token; app state installs the handler. */
let onUnauthorized: () => void = () => {};
export function setUnauthorizedHandler(fn: () => void) {
    onUnauthorized = fn;
}

const auth: Middleware = {
    onRequest({ request }) {
        const t = getToken();
        if (t) request.headers.set("Authorization", `Bearer ${t}`);
        return request;
    },
    onResponse({ response, request }) {
        if (response.status === 401 && new URL(request.url).pathname.startsWith("/api/")) onUnauthorized();
        return response;
    },
};

export const api = createClient<paths>({ baseUrl: location.origin });
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
    if (body && typeof body === "object" && "error" in body && typeof body.error === "string") return body.error;
    if (status === 0) return "Cannot reach the server";
    if (status === 401) return "Signed out: please sign in again";
    if (status === 403) return "You don't have access to this";
    if (status === 404) return "Not found";
    if (status === 409) return "Someone else changed this. Reload and try again";
    if (status === 429) return "Too many attempts. Wait a minute and retry";
    return `Server error (${status})`;
}

/** Unwrap an openapi-fetch result: data, or throw ApiError with the server's message. */
export async function ok<T>(p: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
    let r;
    try {
        r = await p;
    } catch {
        throw new ApiError(0, messageFor(0, null));
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
    const res = await fetch(url, { headers: { Authorization: `Bearer ${getToken() ?? ""}` } });
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
