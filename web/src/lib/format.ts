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
export function day(d: string | null | undefined): string {
    if (!d) return "";
    const [y, m, dd] = d.split("-").map(Number);
    return df.format(new Date(Date.UTC(y!, m! - 1, dd!, 12)));
}

/** Today's operational date in Manila (the day boundary is 04:00). */
export function operationalDate(now = new Date()): string {
    const shifted = new Date(now.getTime() - 4 * 3600_000);
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

export function label(code: string | null | undefined): string {
    if (!code) return "";
    return code
        .toLowerCase()
        .split("_")
        .map((w, i) => (i === 0 ? w[0]!.toUpperCase() + w.slice(1) : w))
        .join(" ");
}

export function clientName(c: { firstName?: string | null; lastName?: string | null; middleName?: string | null; suffix?: string | null }): string {
    if (!c.firstName && !c.lastName) return "(anonymized)";
    return [c.firstName, c.middleName ? `${c.middleName[0]}.` : null, c.lastName, c.suffix].filter(Boolean).join(" ");
}
