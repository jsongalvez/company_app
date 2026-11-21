/** The site hub ticket (room, key, members) kept on the phone per branch; apart from site.svelte.ts so the first screen's code stays small. */
import type { S } from "../api/client";
import { disk } from "./disk";

export type Member = { userId: string; name: string };
export type Ticket = NonNullable<S["TodayScreenResponse"]["siteHub"]> & { branchId: string; members: Member[] };

export const ticketKey = (branchId: string) => `site:ticket:${branchId}`;

/** The today-screen answer carries the branch's ticket and members: kept on the phone for the day without signal. */
export function keepTicket(branchId: string, t: S["TodayScreenResponse"]) {
    if (!t.siteHub) return;
    const members = (t.roster ?? []).map((m) => ({ userId: m.userId, name: m.displayName ?? "" }));
    disk.set(ticketKey(branchId), JSON.stringify({ ...t.siteHub, branchId, members } satisfies Ticket));
}
