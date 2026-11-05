import { backoff } from "./backoff";

/**
 * What this page load did (screens, taps, field changes, API calls, errors), uploaded in the background so a
 * reported problem can be traced and replayed (docs/telemetry.md). Nothing typed and no names leave the phone:
 * a tap carries a hash of its label, a text field only its length. Number and date fields keep their value
 * unless they sit inside [data-pii] (client details).
 */
export type Kind = "boot" | "nav" | "tap" | "input" | "api" | "error" | "net" | "vis";
export type Event = { seq: number; at: number; kind: Kind; data: string };
/** What the upload did: sent (or refused for good), or worth trying again later. */
export type Outcome = "done" | "retry";
export type Post = (body: string) => Promise<Outcome>;

/** Where a tap or change landed: enough to find the same element on a replay, with no readable text. */
export type Target = { tag: string; path: string; role?: string; h?: string; al?: string; name?: string; href?: string };

export const BATCH_MAX = 200;
export const BUFFER_MAX = 1000;
export const DATA_MAX = 1000;
const BATCH_CHARS_MAX = 48_000;
export const FLUSH_MS = 15_000;
const RETRY_CAP_MS = 300_000;
const UPLOAD_TIMEOUT_MS = 20_000;
export const PATH_DEPTH = 6;
export const TEXT_MAX = 200;

const hex = (bytes: number) => Array.from(crypto.getRandomValues(new Uint8Array(bytes)), (b) => b.toString(16).padStart(2, "0")).join("");

/** A fresh W3C `traceparent` (sampled): the server joins this call's trace, and answers its trace id in X-Trace-Id. */
export function traceparent(): string {
    return `00-${hex(16)}-${hex(8)}-01`;
}

/** FNV-1a, 32 bits, base 36. The replay tool computes the same over its own page. */
export function hash(text: string): string {
    let h = 0x811c9dc5;
    for (let i = 0; i < text.length; i++) {
        h ^= text.charCodeAt(i);
        h = Math.imul(h, 0x01000193);
    }
    return (h >>> 0).toString(36);
}

export function norm(text: string | null | undefined): string {
    return (text ?? "").slice(0, TEXT_MAX).replace(/\s+/g, " ").trim();
}

export const ACTIONABLE = "a,button,input,select,textarea,label,summary,[role],[tabindex]";

export function pathOf(el: Element): string {
    const parts: string[] = [];
    for (let e: Element | null = el, depth = 0; e && e.tagName !== "BODY" && depth < PATH_DEPTH; e = e.parentElement, depth++) {
        let n = 1;
        for (let s = e.previousElementSibling; s; s = s.previousElementSibling) if (s.tagName === e.tagName) n++;
        parts.unshift(e.tagName.toLowerCase() + (n > 1 ? `:${n}` : ""));
    }
    return parts.join(">");
}

export function describe(start: Element): Target {
    const el = start.closest(ACTIONABLE) ?? start;
    const t: Target = { tag: el.tagName.toLowerCase(), path: pathOf(el) };
    const role = el.getAttribute("role");
    if (role) t.role = role;
    const text = norm(el.textContent);
    if (text) t.h = hash(text);
    const label = norm(el.getAttribute("aria-label") ?? el.getAttribute("title"));
    if (label) t.al = hash(label);
    const name = el.getAttribute("name");
    if (name) t.name = name.slice(0, 40);
    const href = el.getAttribute("href");
    if (href) t.href = href.split("?")[0]!.slice(0, 200);
    return t;
}

const VALUE_TYPES = new Set(["number", "date", "time", "month", "week", "datetime-local", "range"]);

/** A changed field: which one, and its value only when it can't hold a name or a note. */
export function fieldOf(el: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement): Record<string, unknown> {
    const d: Record<string, unknown> = { el: describe(el), type: el.type };
    const label = norm(el.closest("label")?.textContent ?? el.getAttribute("placeholder"));
    if (label) d.lh = hash(label);
    if (el instanceof HTMLInputElement && (el.type === "checkbox" || el.type === "radio")) d.on = el.checked;
    else if (el.type === "password") return d;
    else if (!el.closest("[data-pii]") && (el instanceof HTMLSelectElement || VALUE_TYPES.has(el.type) || /^(decimal|numeric)$/.test(el.inputMode)))
        d.val = el.value.slice(0, 60);
    else d.len = el.value.length;
    return d;
}

const ID = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;
/** A path safe to send: no ids, no query. */
export function plainPath(path: string): string {
    return path.split("?")[0]!.replace(ID, "{id}");
}

/** The screen, with query values dropped except the few that pick what's shown. */
export function routeOf(hashPart: string): string {
    const [path, query] = (hashPart || "#/").split("?");
    if (!query) return path!.slice(0, 200);
    const kept = new URLSearchParams();
    for (const [k, v] of new URLSearchParams(query)) if (/^(date|from|to|tab|view|branch)$/.test(k)) kept.set(k, v.slice(0, 40));
    const q = kept.toString();
    return (q ? `${path}?${q}` : path!).slice(0, 200);
}

type Fetch = (
    url: string,
    init: { method: "POST"; body: string; keepalive: true; headers: Record<string, string>; signal: AbortSignal },
) => Promise<{ status: number }>;

/**
 * A token the server refuses (expired, revoked) sends the batch on the open route with no user. Held back instead,
 * it would go out after the next sign-in, under whoever signed in.
 */
export async function sendBatch(body: string, token: string | null, fetchFn: Fetch, timeoutMs = UPLOAD_TIMEOUT_MS): Promise<Outcome> {
    const send = async (signedIn: string | null) => {
        const headers: Record<string, string> = { "Content-Type": "application/json", traceparent: traceparent() };
        if (signedIn) headers.Authorization = `Bearer ${signedIn}`;
        return (
            await fetchFn(signedIn ? "/api/telemetry" : "/telemetry", {
                method: "POST",
                body,
                keepalive: true,
                headers,
                signal: AbortSignal.timeout(timeoutMs),
            })
        ).status;
    };
    try {
        let status = await send(token);
        if (status === 401 && token) status = await send(null);
        return status === 429 || status >= 500 ? "retry" : "done";
    } catch {
        return "retry";
    }
}

export type Schedule = (run: () => void, ms: number) => void;

export function createRecorder(
    sessionId: string,
    post: Post,
    now: () => number = Date.now,
    schedule: Schedule = (run, ms) => void setTimeout(run, ms),
    rand: () => number = Math.random,
) {
    let seq = 0;
    let failures = 0;
    const queue: Event[] = [];
    let waiting = false;
    let sending = false;

    function later(ms = FLUSH_MS) {
        if (waiting) return;
        waiting = true;
        schedule(() => ((waiting = false), void flush()), ms);
    }

    function record(kind: Kind, data: Record<string, unknown>) {
        let text = JSON.stringify(data);
        if (text.length > DATA_MAX) text = JSON.stringify({ cut: text.length });
        queue.push({ seq: seq++, at: now(), kind, data: text });
        if (queue.length > BUFFER_MAX) queue.splice(0, queue.length - BUFFER_MAX);
        later();
    }

    function take(): Event[] {
        let chars = 0;
        let n = 0;
        while (n < queue.length && n < BATCH_MAX && chars + queue[n]!.data.length + 60 < BATCH_CHARS_MAX) chars += queue[n++]!.data.length + 60;
        return queue.splice(0, n);
    }

    /** Uploads what's waiting, one batch at a time, off the path of anything the person waits for. */
    async function flush(): Promise<void> {
        if (sending || !queue.length) return;
        sending = true;
        const batch = take();
        const outcome = await post(JSON.stringify({ sessionId, events: batch })).catch((): Outcome => "retry");
        sending = false;
        if (outcome === "retry") {
            queue.unshift(...batch);
            if (queue.length > BUFFER_MAX) queue.splice(0, queue.length - BUFFER_MAX);
            later(backoff(failures++, FLUSH_MS, RETRY_CAP_MS, rand));
            return;
        }
        failures = 0;
        if (queue.length) later(0);
    }

    return { sessionId, record, flush, pending: () => queue.length };
}
