/** Mirrors shared/.../contracts/session/SessionContracts.kt (the server enforces the same). */
export type Status = "PENDING" | "COMPLETED" | "NO_SHOW" | "CANCELLED" | "UNKNOWN";

export function isRoutineMark(from: Status, to: Status) {
    return from === "PENDING" && to !== "PENDING" && to !== "UNKNOWN";
}
/** Put a marked session back to Waiting: allowed for anyone who may mark. */
export function isUndo(from: Status, to: Status) {
    return to === "PENDING" && from !== "PENDING" && from !== "UNKNOWN";
}
export function isCorrection(from: Status, to: Status) {
    return from !== "UNKNOWN" && to !== "UNKNOWN" && from !== to && from !== "COMPLETED" && to !== "COMPLETED" && !isRoutineMark(from, to) && !isUndo(from, to);
}
export function allowedTargets(from: Status, isWalkIn: boolean, canCorrect: boolean): Status[] {
    return (["PENDING", "COMPLETED", "NO_SHOW", "CANCELLED"] as Status[]).filter(
        (to) =>
            !(isWalkIn && (to === "NO_SHOW" || to === "CANCELLED")) && (isRoutineMark(from, to) || isUndo(from, to) || (canCorrect && isCorrection(from, to))),
    );
}

export function statusClass(s: string | undefined): string {
    return s === "COMPLETED" ? "ok" : s === "PENDING" ? "info" : s === "NO_SHOW" ? "warn" : s === "CANCELLED" ? "plain" : "plain";
}

export const EXPENSE_CATEGORIES = [
    "PANTRY",
    "COMMUNICATION",
    "WATER",
    "TRANSPORTATION",
    "ELECTRICITY",
    "RENTAL",
    "OFFICE_SUPPLIES",
    "FURNITURE_FIXTURES",
    "MISCELLANEOUS",
] as const;
export const SESSION_TYPES = ["REGULAR", "SECOND_SESSION", "SUBSEQUENT", "PROVINCIAL_FIRST", "MEDICAL_MISSION"] as const;
