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
}
