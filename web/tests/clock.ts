import { addDays, operationalDate } from "../src/lib/format";

/** The app's business date (Manila, day turns over at 04:00), `days` from today. Seed and type dates with this, never `new Date`. */
export function businessDate(days = 0): string {
    return addDays(operationalDate(), days);
}

/** Manila wall-clock time, `offsetSec` from now, as "2026-10-18T14:30:01". Seconds never end in ":00", which a time field drops. */
export function manilaTime(offsetSec: number): string {
    return new Intl.DateTimeFormat("sv-SE", {
        timeZone: "Asia/Manila",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        hourCycle: "h23",
    })
        .format(Date.now() + offsetSec * 1000)
        .replace(" ", "T")
        .replace(/:00$/, ":01");
}
