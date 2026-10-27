package com.companyb.companyapp.workforce

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/** A branch's roster and who is clocked in on its day, read together for the Today screen. */
data class DayPresence(
    val roster: List<RosterMember>,
    val clockedInUserIds: Set<UUID>,
) {
    fun isMember(userId: UUID): Boolean = roster.any { it.userId == userId }

    fun isClockedIn(userId: UUID): Boolean = userId in clockedInUserIds

    fun entries(): List<AttendanceRosterEntry> =
        roster.map {
            AttendanceRosterEntry(
                it.assignmentId,
                it.userId,
                it.displayName,
                it.slot,
                it.userId in clockedInUserIds,
            )
        }
}

object AttendancePresence {
    /**
     * The roster is the active members (the same rows the membership gate checks), so the
     * caller's membership is a lookup in it. [branchDayId] is null when today has no day yet.
     */
    fun read(
        branchId: UUID,
        branchDayId: UUID?,
    ): DayPresence =
        transaction {
            DayPresence(
                roster = AttendanceRepository.rosterRows(branchId),
                clockedInUserIds = branchDayId?.let { AttendanceRepository.activeClockInUserIds(it) } ?: emptySet(),
            )
        }
}
