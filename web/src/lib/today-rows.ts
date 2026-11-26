import type { S } from "../api/client";
import type { Standing } from "./outbox.svelte";
import { findLocal } from "./clientdir.svelte";
import { clientName } from "./format";

type Local = { state: Exclude<Standing, "sent">; item: string; failed?: string } | null;
/** A row of Today's list. The server picks a held session's type when it lands, so a held row has none yet. */
export type Row = Omit<S["DashboardSessionResponse"], "sessionType"> & {
    sessionType?: S["DashboardSessionResponse"]["sessionType"];
    held: boolean;
    local: Local;
};

/** A session saved on this phone, as a row before the server has it. */
export function heldRow(at: string, b: Record<string, unknown>): Row {
    const cid = String(b.clientId);
    const c = findLocal(cid);
    const price = String(b.finalPrice ?? "0");
    return {
        id: String(b.id),
        clientId: cid,
        clientName: c ? clientName(c) : "New session",
        isWalkIn: !!b.isWalkIn,
        sessionStatus: "PENDING" as const,
        basePrice: price,
        finalPrice: price,
        remarks: (b.remarks as string) ?? null,
        otherConcerns: (b.otherConcerns as string) ?? null,
        bookedAt: at,
        nextAppointmentDate: (b.nextAppointmentDate as string) ?? null,
        version: 0,
        isVoided: false,
        practitioners: [],
        concerns: [],
        held: true,
        local: null,
    };
}
