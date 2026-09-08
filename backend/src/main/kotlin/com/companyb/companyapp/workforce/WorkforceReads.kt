package com.companyb.companyapp.workforce

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Narrow workforce attendance fact for the shared commission aggregation (#604): the
 * clock-in/out window covering a sale. Carries only the membership/eligibility inputs
 * the engine needs — never the full persistence row, its marker, or its day id.
 */
data class AttendanceWindow(
    val userId: UUID,
    val clockIn: OffsetDateTime,
    val clockOut: OffsetDateTime?,
)

/**
 * Named workforce-read seams for cross-owner consumers (map #615 #604): session
 * membership/slot facts and commission attendance windows. Thin delegations to the
 * internal workforce stores — not a facade over every method. Service-to-service calls
 * need no architecture allowlist entry; direct store imports from other owners stay banned.
 *
 * ACTIVE-user filtering (member check) stays distinct from merely unended assignment
 * filtering (slot lookup) — the two seams must not be conflated.
 */
object WorkforceReads {
    /** ACTIVE member check for session create validation (#366) — runs on its own transaction. */
    fun hasActiveMember(
        branchId: UUID,
        userId: UUID,
    ): Boolean = BranchMemberRepository.hasActiveMember(branchId, userId)

    /**
     * Unended-assignment slot for the session practitioner snapshot — runs on the caller's
     * open transaction. Null when no active assignment exists; the caller falls back to 999.
     */
    fun findAssignmentSlotInTransaction(
        branchId: UUID,
        userId: UUID,
    ): Short? =
        UserBranchAssignmentRepository
            .findActiveByBranchAndUserInTransaction(branchId, userId, forUpdate = false)
            ?.slot

    /**
     * #669 — the caller's open clock-in window on [date] for launch/login resume. Runs on
     * the caller's transaction; null when no window stands open (absent or clocked out).
     */
    fun findActiveShiftInTransaction(
        userId: UUID,
        date: LocalDate,
    ): ActiveShift? = AttendanceRepository.findActiveShiftInTransaction(userId, date)

    /**
     * Full-window attendance read for the shared commission aggregation (#497) — runs on the
     * caller's transaction so enclosing commands observe their own uncommitted writes.
     * One query per day; never per-sale or per-user.
     */
    fun findAttendanceWindowsInTransaction(branchDayId: UUID): List<AttendanceWindow> =
        AttendanceRepository.findByBranchDayIdInTransaction(branchDayId).map {
            AttendanceWindow(
                userId = it.userId,
                clockIn = it.clockIn,
                clockOut = it.clockOut,
            )
        }
}
