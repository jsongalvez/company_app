const TZ = "Asia/Manila";

const money = new Intl.NumberFormat("en-PH", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const int = new Intl.NumberFormat("en-PH");

/** API money is a decimal string ("1234.50"). */
export function peso(v: string | number | null | undefined): string {
    if (v === null || v === undefined || v === "") return "";
    const n = typeof v === "number" ? v : Number(v);
    return Number.isFinite(n) ? money.format(n) : String(v);
}
export function num(v: number | null | undefined): string {
    return v === null || v === undefined ? "" : int.format(v);
}
export function sum(values: (string | number | null | undefined)[]): number {
    return values.reduce<number>((a, v) => a + (Number(v) || 0), 0);
}

const dtf = new Intl.DateTimeFormat("en-PH", { timeZone: TZ, month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
const tf = new Intl.DateTimeFormat("en-PH", { timeZone: TZ, hour: "numeric", minute: "2-digit" });
const df = new Intl.DateTimeFormat("en-PH", { timeZone: TZ, weekday: "short", month: "short", day: "numeric", year: "numeric" });

export function dateTime(iso: string | null | undefined): string {
    return iso ? dtf.format(new Date(iso)) : "";
}
export function time(iso: string | null | undefined): string {
    return iso ? tf.format(new Date(iso)) : "";
}
/** A plain calendar date "2026-10-17" (no timezone shift). */
const sdf = new Intl.DateTimeFormat("en-US", { weekday: "short", month: "short", day: "numeric", timeZone: "UTC" });

/** "Fri, Oct 23": for narrow phone tables. */
export function shortDay(d: string | null | undefined): string {
    if (!d) return "";
    const [y, m, dd] = d.split("-").map(Number);
    return sdf.format(new Date(Date.UTC(y!, m! - 1, dd!, 12)));
}

export function day(d: string | null | undefined): string {
    if (!d) return "";
    const [y, m, dd] = d.split("-").map(Number);
    return df.format(new Date(Date.UTC(y!, m! - 1, dd!, 12)));
}

/** The one place the app reads the wall clock for a date or a timestamp. */
export const now = (): Date => new Date();
export const nowIso = (): string => now().toISOString();

/** Today's operational date in Manila (the day boundary is 04:00). */
export function operationalDate(at = now()): string {
    const shifted = new Date(at.getTime() - 4 * 3600_000);
    return new Intl.DateTimeFormat("en-CA", { timeZone: TZ }).format(shifted);
}
export function addDays(d: string, n: number): string {
    const [y, m, dd] = d.split("-").map(Number);
    const t = new Date(Date.UTC(y!, m! - 1, dd! + n));
    return t.toISOString().slice(0, 10);
}
export function monthStart(d: string): string {
    return d.slice(0, 8) + "01";
}
/** First and last day of the month `shift` months from year/month (month is 1-12). */
export function monthBounds(year: number, month: number, shift: number): { from: string; to: string } {
    const iso = (d: Date) => d.toISOString().slice(0, 10);
    return { from: iso(new Date(Date.UTC(year, month - 1 + shift, 1))), to: iso(new Date(Date.UTC(year, month + shift, 0))) };
}

/** App words for backend codes. Keep in sync with CONTEXT.md. */
const WORDS: Record<string, string> = {
    // Day
    OPEN: "Open",
    PAST: "Closed",
    REMITTED: "Remitted",
    // Session status
    PENDING: "Waiting",
    COMPLETED: "Done",
    NO_SHOW: "No-show",
    CANCELLED: "Cancelled",
    // Session type
    REGULAR: "First visit",
    PROVINCIAL_FIRST: "First visit (tour)",
    SECOND_SESSION: "Second visit",
    SUBSEQUENT: "Return visit",
    MEDICAL_MISSION: "Medical mission",
    // Branch type
    CLINIC: "Clinic",
    PROVINCIAL_TOUR: "Provincial tour",
    // Stock
    RESTOCK: "Restock",
    SALE: "Sale",
    TESTER: "Tester",
    SAMPLE: "Sample",
    MISSING: "Missing",
    ADJUSTMENT: "Count fix",
    // Remittance
    DRAFT: "Draft",
    SUBMITTED: "Sent",
    SESSION: "Sessions",
    PRODUCT: "Product sales",
    PRODUCT_SALE: "Product sale",
    BANK_TRANSFER: "Bank transfer",
    HANDED_TO_ACCOUNTANT: "Handed to accountant",
    // Relief
    GRANTED: "Approved",
    DENIED: "Declined",
    ACCEPTED: "Accepted",
    DECLINED: "Declined",
    RETRACTED: "Taken back",
    REVOKED: "Cancelled",
    // Users and roles
    ACTIVE: "Active",
    INACTIVE: "Deactivated",
    ONBOARDING: "New (no access yet)",
    SUPERUSER: "Superuser",
    OWNER: "Owner",
    MANAGER: "Manager",
    COORDINATOR: "Coordinator",
    PRACTITIONER: "Practitioner",
    ACCOUNTANT: "Accountant",
    // Change history
    INSERT: "Added",
    UPDATE: "Changed",
    DELETE: "Removed",
    UNKNOWN: "Unknown",
};

/** Plain word for a backend code; unknown codes become "Sentence case". */
export function label(code: string | null | undefined): string {
    if (!code) return "";
    return (
        WORDS[code] ??
        code
            .toLowerCase()
            .split("_")
            .map((w, i) => (i === 0 ? w[0]!.toUpperCase() + w.slice(1) : w))
            .join(" ")
    );
}

export function clientName(c: { firstName?: string | null; lastName?: string | null; middleName?: string | null; suffix?: string | null }): string {
    if (!c.firstName && !c.lastName) return "(details erased)";
    return [c.firstName, c.middleName ? `${c.middleName[0]}.` : null, c.lastName, c.suffix].filter(Boolean).join(" ");
}

/** "sessionStatus" / "session_status" -> "Session status". */
export function fieldName(k: string): string {
    const w = k
        .replace(/_/g, " ")
        .replace(/([a-z])([A-Z])/g, "$1 $2")
        .toLowerCase()
        .replace(/\bid\b/g, "")
        .trim();
    return w.charAt(0).toUpperCase() + w.slice(1);
}

/** One recorded value, in app words. Ids become names when [names] knows them. */
export function fieldValue(v: unknown, names: Record<string, string> = {}): string {
    if (v === null || v === undefined || v === "") return "–";
    if (typeof v === "boolean") return v ? "Yes" : "No";
    if (typeof v === "string") {
        if (names[v]) return names[v];
        if (/^[0-9a-f]{8}-[0-9a-f]{4}-/.test(v)) return "(unknown)";
        if (/^\d{4}-\d\d-\d\dT\d\d:\d\d/.test(v)) return dateTime(v);
        if (v === "true" || v === "false") return v === "true" ? "Yes" : "No";
        if (/^[A-Z][A-Z_]+$/.test(v)) return label(v);
    }
    return typeof v === "object" ? JSON.stringify(v) : String(v);
}

// Bookkeeping fields that say nothing to a reader of the change history.
const NOISE = /^(id|version|updated_?at|created_?at|created_?by|branch_?day_?id|branch_?id)$/i;

type Entry = {
    action?: string;
    oldValue?: string | null;
    newValue?: string | null;
    subject?: string | null;
    names?: { id: string; name: string }[];
};

/** What a change did, in words: changed fields as "old → new", or the added/removed record's fields. */
export function changeText(e: Entry): string {
    try {
        const names = Object.fromEntries((e.names ?? []).map((n) => [n.id, n.name]));
        const o = e.oldValue ? JSON.parse(e.oldValue) : {};
        const n = e.newValue ? JSON.parse(e.newValue) : {};
        if (e.action === "UPDATE")
            return Object.keys({ ...o, ...n })
                .filter((k) => JSON.stringify(o[k]) !== JSON.stringify(n[k]) && !/version|updated_?at/i.test(k))
                .map((k) => `${fieldName(k)}: ${o[k] == null || o[k] === "" ? "" : `${fieldValue(o[k], names)} → `}${fieldValue(n[k], names)}`)
                .join("; ");
        const row = e.action === "DELETE" ? o : n;
        return (
            Object.keys(row)
                // Skip names the subject line already shows.
                .filter((k) => {
                    const shown = names[row[k]];
                    return !NOISE.test(k) && row[k] !== null && row[k] !== "" && !(shown && e.subject?.includes(shown));
                })
                .map((k) => `${fieldName(k)}: ${fieldValue(row[k], names)}`)
                .join("; ")
        );
    } catch {
        return "";
    }
}

/** Now (or a moment) as a Manila wall-clock time "2026-10-18T14:30:05". */
export function manilaTime(at = new Date()): string {
    return new Intl.DateTimeFormat("sv-SE", {
        timeZone: TZ,
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hourCycle: "h23",
    })
        .format(at)
        .replace(" ", "T");
}
/** "2026-10-18T14:30:05" (Manila) shown as "Oct 18, 2026, 2:30:05 PM"; a plain day as day(). */
export function wallTime(v: string): string {
    if (!v.includes("T")) return day(v);
    const [d, t] = v.split("T");
    const [y, m, dd] = d!.split("-").map(Number);
    const [hh, mm, ss] = t!.split(":").map(Number);
    const at = new Date(Date.UTC(y!, m! - 1, dd!, hh!, mm!, ss ?? 0));
    return new Intl.DateTimeFormat("en-PH", {
        timeZone: "UTC",
        month: "short",
        day: "numeric",
        year: "numeric",
        hour: "numeric",
        minute: "2-digit",
        ...(ss ? { second: "2-digit" } : {}),
    }).format(at);
}

/** Server messages use code words; swap them for the app's words (see CONTEXT.md). */
export function plain(msg: string): string {
    const out = msg
        .replace(/\b[A-Z][A-Z_]{2,}\b/g, (code) => (code === "PII" ? code : label(code)))
        .replace(/\bbranch[ -]days?\b/gi, (m) => (m.toLowerCase().endsWith("s") ? "days" : "day"))
        .replace(/\b(UUID|uuid)\b/g, "id")
        .replace(/\bunvoid(ed)?\b/gi, (_m, ed) => (ed ? "restored" : "restore"))
        .replace(/\banonymi[sz]ed\b/gi, "with erased details")
        .replace(/\s*\(#\d+\)/g, "");
    return out.charAt(0).toUpperCase() + out.slice(1);
}
