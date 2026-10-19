/** Booked visits: one place for how a booking's day and time read, and for saving changes. */
import { api, ok, type S } from "../api/client";
import { invalidate } from "./cache.svelte";
import { addDays, operationalDate, shortDay } from "./format";

export type Booking = S["BookingResponse"];

/** "Today", "Tomorrow" or "Wed, Oct 22", plus "2:30 PM" when a time was set. */
export function visitWhen(b: Pick<Booking, "visitDate" | "visitTime">): string {
    const today = operationalDate();
    const d = b.visitDate === today ? "Today" : b.visitDate === addDays(today, 1) ? "Tomorrow" : shortDay(b.visitDate);
    return b.visitTime ? `${d}, ${clock(b.visitTime)}` : d;
}

export function clock(t: string): string {
    const [h, m] = t.split(":").map(Number);
    return `${((h! + 11) % 12) + 1}:${String(m).padStart(2, "0")} ${h! < 12 ? "AM" : "PM"}`;
}

/** Who booked it and who last changed it, for the list. */
export function whoText(b: Booking): string {
    return b.updatedByName ? `Booked by ${b.createdByName} · changed by ${b.updatedByName}` : `Booked by ${b.createdByName}`;
}

export async function setBooking(b: Booking, change: Partial<Pick<Booking, "visitDate" | "visitTime" | "note" | "status">>): Promise<Booking> {
    const next = { ...b, ...change };
    const r = await ok(
        api.PATCH("/api/branches/{branchId}/bookings/{bookingId}", {
            params: { path: { branchId: b.branchId, bookingId: b.id } },
            body: { visitDate: next.visitDate, visitTime: next.visitTime || null, note: next.note || null, status: next.status },
        }),
    );
    invalidate("bookings:");
    return r;
}
