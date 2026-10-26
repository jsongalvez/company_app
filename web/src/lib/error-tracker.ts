/**
 * Error reports to Sentry (or GlitchTip), only when a DSN is given. Pure: the live instance is in track.ts.
 *
 * An event holds the error's class (and, for plain script errors, a short message), the server's trace id
 * for a failed call, the method, the path with ids replaced, the status and the app version. Never the
 * query string, request or response body, server message text, person or client data.
 */
export type Send = (url: string, body: string) => void;

export type Dsn = { url: string; key: string };

export function parseDsn(raw: string | undefined): Dsn | null {
    if (!raw) return null;
    try {
        const u = new URL(raw.trim());
        const project = u.pathname.replace(/^\/+|\/+$/g, "");
        if (!u.username || !project || project.includes("/") || (u.protocol !== "https:" && u.protocol !== "http:")) return null;
        return { url: `${u.protocol}//${u.host}/api/${project}/envelope/?sentry_version=7&sentry_key=${encodeURIComponent(u.username)}`, key: u.username };
    } catch {
        return null;
    }
}

const ID = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;
/** A path safe to send: no ids, no query. */
export function plainPath(path: string): string {
    return path.split("?")[0]!.replace(ID, "{id}");
}

const MESSAGE_MAX = 200;
const STACK_MAX = 2000;
const PER_PAGE_MAX = 20;
const REPEAT_MS = 60_000;

/** Statuses that are the app working as designed, not a bug to chase. */
const EXPECTED = new Set([401, 409, 429]);
/** Refusals the app asks for on purpose: Today reads its 403 as "clock in first". */
const EXPECTED_REFUSALS = new Set(["GET /api/branches/{id}/dashboard/today 403"]);

export type ApiFailure = { method: string; path: string; status: number; traceId: string | null };

export function createTracker(dsn: string | undefined, send: Send, release: string, now: () => number = Date.now) {
    const target = parseDsn(dsn);
    const seen = new Map<string, number>();
    let sentCount = 0;

    function emit(key: string, event: Record<string, unknown>) {
        if (!target || sentCount >= PER_PAGE_MAX) return;
        const last = seen.get(key);
        if (last !== undefined && now() - last < REPEAT_MS) return;
        seen.set(key, now());
        sentCount++;
        const id = crypto.randomUUID().replaceAll("-", "");
        const full = {
            event_id: id,
            timestamp: now() / 1000,
            platform: "javascript",
            level: "error",
            release,
            environment: "production",
            ...event,
        };
        const body = [{ event_id: id, sent_at: new Date(now()).toISOString() }, { type: "event" }, full].map((x) => JSON.stringify(x)).join("\n");
        try {
            send(target.url, body);
        } catch {
            /* the tracker must never break the app */
        }
    }

    const page = () => plainPath((typeof location !== "undefined" ? location.hash : "").replace(/^#/, "") || "/");

    return {
        enabled: target !== null,
        /** An uncaught error or unhandled rejection. */
        captureError(error: unknown, where: "error" | "unhandledrejection") {
            const isError = error instanceof Error;
            // A server answer (ApiError has a status) was already reported as its request, if at all;
            // its text can quote a person, so it never leaves the phone.
            if (isError && "status" in error) return;
            const type = isError ? error.name || "Error" : typeof error;
            const value = isError ? error.message.slice(0, MESSAGE_MAX) : undefined;
            // V8 starts the stack with the message, so take the message out of it.
            const stack = isError ? (error.message ? error.stack?.split(error.message).join("") : error.stack)?.slice(0, STACK_MAX) : undefined;
            emit(`${where}:${type}:${value ?? ""}`, {
                exception: { values: [{ type, value, mechanism: { type: where, handled: false } }] },
                tags: { page: page(), kind: where },
                extra: stack ? { stack } : undefined,
            });
        },
        /** A request the server refused or failed (not the expected 401/409/429). */
        captureApiFailure(f: ApiFailure) {
            if (f.status < 400 || EXPECTED.has(f.status)) return;
            const path = plainPath(f.path);
            if (EXPECTED_REFUSALS.has(`${f.method} ${path} ${f.status}`)) return;
            emit(`api:${f.method}:${path}:${f.status}`, {
                message: `${f.method} ${path} -> ${f.status}`,
                tags: { kind: "api", method: f.method, path, status: String(f.status), trace_id: f.traceId ?? "none", page: page() },
            });
        },
        /** A queued offline save the server refused on replay. */
        captureOutboxFailure(f: { method: string; path: string; status: number }) {
            const path = plainPath(f.path);
            emit(`outbox:${f.method}:${path}:${f.status}`, {
                message: `Offline save refused: ${f.method} ${path} -> ${f.status}`,
                tags: { kind: "outbox", method: f.method, path, status: String(f.status), page: page() },
            });
        },
    };
}
