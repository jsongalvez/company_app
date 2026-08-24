package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.dto.MemberAttendanceResponse

/** #404 — pure roster presentation rules shared by [AttendanceRosterCard] (desktopTest-pinned). */
object AttendanceRosterLogic {
    /** Rows with an open clock-in window at the branch today. */
    fun presentCount(rows: List<MemberAttendanceResponse>): Int = rows.count { it.present }

    /**
     * Mark controls render for every row except the caller's own — self attendance stays a
     * deliberate act (the drawer Clock in / Clock out); members correct others only.
     */
    fun canToggle(
        row: MemberAttendanceResponse,
        currentUserId: String?,
    ): Boolean = currentUserId != null && row.userId != currentUserId

    /** "N of M present" summary; null when the roster is empty (nothing to summarize). */
    fun summaryLine(rows: List<MemberAttendanceResponse>): String? =
        rows.takeIf { it.isNotEmpty() }?.let { "${presentCount(it)} of ${it.size} present" }

    fun toggleLabel(row: MemberAttendanceResponse): String = if (row.present) "Mark absent" else "Mark present"

    /**
     * #416 — the self-service slot affordance renders on the caller's own row only; every
     * other member's slot stays read-only display (the own-row-static rule's mirror).
     */
    fun canEditOwnSlot(
        row: MemberAttendanceResponse,
        currentUserId: String?,
    ): Boolean = currentUserId != null && row.userId == currentUserId

    /**
     * Swap targets for the caller's own row: every other roster member. Fail-closed without
     * a caller identity; an empty result hides the Swap entry (nothing to trade with).
     */
    fun swapCandidates(
        rows: List<MemberAttendanceResponse>,
        currentUserId: String?,
    ): List<MemberAttendanceResponse> = currentUserId?.let { me -> rows.filter { it.userId != me } } ?: emptyList()

    /** Picker label for a swap candidate: the member plus the slot it would trade places with. */
    fun swapCandidateLabel(row: MemberAttendanceResponse): String = "${row.displayName} · slot ${row.slot}"
}
