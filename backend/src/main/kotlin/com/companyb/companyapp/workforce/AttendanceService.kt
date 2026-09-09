package com.companyb.companyapp.workforce

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Attendance feature commands (#321, ADR-0024). Each mutating command owns exactly one business
 * transaction: persistence runs on it via `AttendanceRepository.*InTransaction` store operations,
 * the before/after state is captured inside it (ADR-0019 invariant), and the audit row is
 * inserted directly into it — so domain write + audit commit atomically or not at all.
 * Operational-day resolution goes through the Branch Day boundary (#318); the commission
 * recalculation triggered by clock-in/out joins the same transaction, keeping side effects
 * atomic with the attendance mutation.
 */
object AttendanceService {
    private val logger = KotlinLogging.logger {}

    fun findUsersClockedInAt(
        branchDayId: UUID,
        at: OffsetDateTime,
    ): List<UUID> = AttendanceRepository.findUsersClockedInAt(branchDayId, at)

    fun hasActiveClockIn(
        userId: UUID,
        branchDayId: UUID,
    ): Boolean = AttendanceRepository.hasActiveClockIn(userId, branchDayId)

    fun findUsersByBranchDayId(branchDayId: UUID): List<BranchDayUser> {
        BranchDayService.requireBranchDayExists(branchDayId)
        return AttendanceRepository.findUsersByBranchDayId(branchDayId)
    }

    /**
     * #404 — a home member marks another home member present or absent at [branchId] today
     * (BR: practitioners mark other members of their home branch; coordinators mark
     * practitioners at their branch). One membership rule covers both sentences: the caller
     * and the target must each hold an active assignment at the branch — cross-branch
     * marking 403s, non-member targets 400. Relief users are neither (their attendance is
     * their own deliberate check-in).
     *
     * Present mirrors self clock-in exactly — same day resolution, #376 branch-day lock,
     * active-clock-in guard, idempotent-retry contract, atomic commission recalculation —
     * except `userId` is the target and `markedBy` is the caller. Absent closes the target's
     * open window (a clock-out on behalf): audit attributes the marker via changedBy, the
     * recalculation joins the transaction, and an already-closed window is an idempotent
     * no-op. Day state stays ungated, matching self clock-in/out (attendance is the access
     * primitive; resolveOrCreate bootstraps the day).
     */
    fun mark(
        callerId: UUID,
        branchId: UUID,
        targetUserId: UUID,
        present: Boolean,
        attendanceId: UUID?,
    ): AttendanceMarkResult {
        // #712 — 404 precedence for an unknown branch before the membership gate:
        // findActiveMembers would otherwise yield empty membership and surface 403
        // (403-masking-404; same position as #704 clockIn guard).
        BranchService.findById(branchId)
        return transaction {
            // Gate inside the command transaction, under the assignment row lock (#404
            // review): a concurrent revocation or deactivation serializes with the mark
            // instead of racing a pre-transaction check. #519 — reciprocal marks took
            // caller-first order, a wait-for cycle on the membership rows. Lock the
            // distinct participant set in stable user-ID order, then evaluate both
            // memberships from those locked facts (a self-mark locks once).
            val activeMembers =
                AttendanceRepository.findActiveMembersInTransaction(branchId, listOf(callerId, targetUserId))
            if (callerId !in activeMembers) {
                throw ForbiddenException("Home-branch membership required to mark attendance")
            }
            if (targetUserId !in activeMembers) {
                throw ForbiddenException("Only home-branch members can be marked present or absent at this branch")
            }

            if (present) {
                markPresent(callerId, branchId, targetUserId, attendanceId)
            } else {
                markAbsent(callerId, branchId, targetUserId)
            }
        }
    }

    /**
     * The present leg: mirrors self clock-in — day resolution, #376 branch-day lock,
     * active-clock-in guard, idempotent-retry contract, atomic commission recalculation —
     * with `userId` = target and `markedBy` = caller.
     */
    private fun markPresent(
        callerId: UUID,
        branchId: UUID,
        targetUserId: UUID,
        attendanceId: UUID?,
    ): AttendanceMarkResult {
        val id =
            attendanceId
                ?: throw ValidationException("attendance id is required to mark a member present")
        val today = BranchDayService.currentOperationalDate()
        val branchDay = BranchDayService.resolveOrCreate(branchId, today)

        // #376 — same serialization as self clock-in: the retraction-cutoff commands
        // hold this lock while deciding, so a duty can never start behind a mark that
        // then lands; the idempotent readback re-runs under it.
        BranchDayService.lockDayInTransaction(branchDay.id)
        retryOutcomeOrNull(id, branchId, targetUserId, callerId)?.let {
            return AttendanceMarkResult(it.attendance, it.created, it.isRelief)
        }

        ShiftGuard.ensureNoActiveClockIn(targetUserId, branchDay.id)

        val (attendance, wasCreated) =
            AttendanceRepository.clockInInTransaction(
                ClockInParams(
                    attendanceId = id,
                    branchDayId = branchDay.id,
                    userId = targetUserId,
                    markedBy = callerId,
                    branchDayAssignmentId = UUID.randomUUID(),
                    // The gate proved the target is a home member — never relief.
                    isRelief = false,
                    branchId = branchId,
                ),
            )

        if (wasCreated) {
            AttendanceAudit.inserted(
                AuditContext(changedBy = callerId, branchId = branchId),
                attendance,
            )
        }

        logger.info {
            "[ATTENDANCE-MARK] User $callerId marked user $targetUserId present at branch $branchId " +
                "(attendance=$id)"
        }

        if (wasCreated) {
            CommissionService.recalculateInTransaction(branchDay.id)
        }

        return AttendanceMarkResult(attendance, wasCreated, isRelief = false)
    }

    /**
     * The absent leg: closes the target's open window (a clock-out on behalf). Find-only
     * day resolution (#157 discipline) — an absent-mark never creates a branch day; with no
     * day row or no open window it is an idempotent no-op. The audit attributes the marker
     * via changedBy and the recalculation joins the same transaction.
     */
    private fun markAbsent(
        callerId: UUID,
        branchId: UUID,
        targetUserId: UUID,
    ): AttendanceMarkResult {
        val today =
            BranchDayService.findToday(branchId)
                ?: return AttendanceMarkResult(null, created = false, isRelief = false)

        val existing =
            AttendanceRepository.findActiveByUserAndBranchDayInTransaction(targetUserId, today.id)
                ?: return AttendanceMarkResult(null, created = false, isRelief = false)

        val (after, wasClockedOut) = AttendanceRepository.clockOutInTransaction(existing.id)

        if (wasClockedOut) {
            AttendanceAudit.updated(
                AuditContext(changedBy = callerId, branchId = branchId),
                existing,
                after,
            )
        }

        logger.info {
            "[ATTENDANCE-MARK] User $callerId marked user $targetUserId absent at branch $branchId " +
                "(attendance=${existing.id})"
        }

        if (wasClockedOut) {
            CommissionService.recalculateInTransaction(today.id)
        }

        return AttendanceMarkResult(after, created = false, getIsRelief(today.id, targetUserId))
    }

    /**
     * #404 — the home-branch roster with live presence flags at [branchId] today. Membership-gated
     * read (the same authority that marks); day resolution stays find-only — a missing day row means
     * nobody is present and no day is created by the read. Ordered by Branch Slot then display name.
     */
    fun rosterToday(
        callerId: UUID,
        branchId: UUID,
    ): List<AttendanceRosterEntry> {
        // #713 — 404 precedence for an unknown branch before the membership gate:
        // hasActiveMembership would otherwise yield no membership and surface 403
        // (403-masking-404; same position as #712 mark / #704 clockIn guard).
        BranchService.findById(branchId)
        return transaction {
            requireActiveMember(callerId, branchId, "Home-branch membership required to view this branch's attendance")

            val presentUserIds =
                BranchDayService
                    .findToday(branchId)
                    ?.let { AttendanceRepository.activeClockInUserIds(it.id) }
                    ?: emptySet()
            AttendanceRepository.rosterRows(branchId).map { member ->
                AttendanceRosterEntry(
                    assignmentId = member.assignmentId,
                    userId = member.userId,
                    displayName = member.displayName,
                    slot = member.slot,
                    present = member.userId in presentUserIds,
                )
            }
        }
    }

    fun clockOut(
        attendanceId: UUID,
        callerId: UUID,
    ): AttendanceServiceResult =
        transaction {
            // Transaction-local before-state (ADR-0019): read inside the command's transaction,
            // never held across a method boundary where a concurrent write could stale it.
            val existing =
                AttendanceRepository.findByIdInTransaction(attendanceId)
                    ?: throw NotFoundException("Attendance record not found")

            if (existing.userId != callerId) {
                throw ForbiddenException("Only attendance owner can clock out")
            }

            if (existing.clockOut != null) {
                val isRelief = getIsRelief(existing.branchDayId, existing.userId)
                return@transaction AttendanceServiceResult(existing, false, isRelief)
            }

            val branchId = BranchDayService.requireBranchDayExists(existing.branchDayId).branchId

            val (attendance, wasClockedOut) = AttendanceRepository.clockOutInTransaction(attendanceId)

            if (wasClockedOut) {
                AttendanceAudit.updated(
                    AuditContext(changedBy = callerId, branchId = branchId),
                    existing,
                    attendance,
                )
            }

            logger.info { "[CLOCK-OUT] User $callerId clocked out (attendance=$attendanceId)" }

            if (wasClockedOut) {
                CommissionService.recalculateInTransaction(attendance.branchDayId)
            }

            val isRelief = getIsRelief(attendance.branchDayId, attendance.userId)
            AttendanceServiceResult(attendance, false, isRelief)
        }

    fun clockIn(
        attendanceId: UUID,
        branchId: UUID,
        callerId: UUID,
    ): AttendanceServiceResult {
        // #704 — 404 precedence for an unknown branch before the command transaction:
        // resolveOrCreate would otherwise hit the branch_day.branch_id FK and surface
        // 500 (same class as #692/#694/#696/#703; getToday precedent).
        BranchService.findById(branchId)
        return transaction {
            retryOutcomeOrNull(attendanceId, branchId, callerId, callerId)?.let { return@transaction it }

            val today = BranchDayService.currentOperationalDate()
            val branchDay = BranchDayService.resolveOrCreate(branchId, today)

            // #376 — take the branch-day row lock BEFORE the guard so clock-in serializes
            // against the retraction cutoff commands (they hold it while deciding); the
            // commission recalculation below re-acquires it harmlessly in this transaction.
            BranchDayService.lockDayInTransaction(branchDay.id)

            // A concurrent same-id retry may have committed while this transaction waited
            // on the lock — the idempotent readback must run again under it (#376 review).
            retryOutcomeOrNull(attendanceId, branchId, callerId, callerId)?.let { return@transaction it }

            ShiftGuard.ensureNoActiveClockIn(callerId, branchDay.id)

            val isRelief = resolveIsRelief(branchId, callerId)

            val branchDayAssignmentId = UUID.randomUUID()

            val (attendance, wasCreated) =
                AttendanceRepository.clockInInTransaction(
                    ClockInParams(
                        attendanceId = attendanceId,
                        branchDayId = branchDay.id,
                        userId = callerId,
                        markedBy = callerId,
                        branchDayAssignmentId = branchDayAssignmentId,
                        isRelief = isRelief,
                        branchId = branchId,
                    ),
                )

            if (wasCreated) {
                AttendanceAudit.inserted(
                    AuditContext(changedBy = callerId, branchId = branchId),
                    attendance,
                )
            }

            logger.info {
                "[CLOCK-IN] User $callerId clocked in at branch $branchId (relief=$isRelief, attendance=$attendanceId)"
            }

            if (wasCreated) {
                CommissionService.recalculateInTransaction(branchDay.id)
            }

            AttendanceServiceResult(attendance, wasCreated, isRelief)
        }
    }

    /**
     * The idempotent-retry outcome for an attendance id that already exists, or null when
     * the id is free. A mismatch (another target, marker, branch, or day) still 409s — the
     * retry contract covers replaying your own request only.
     */
    private fun retryOutcomeOrNull(
        attendanceId: UUID,
        branchId: UUID,
        targetUserId: UUID,
        markerId: UUID,
    ): AttendanceServiceResult? {
        val existing = AttendanceRepository.findByIdInTransaction(attendanceId) ?: return null
        val sameMark = existing.userId == targetUserId && existing.markedBy == markerId
        val sameBranch =
            BranchDayService.requireBranchDayExists(existing.branchDayId).branchId == branchId
        val sameDay = BranchDayService.findToday(branchId)?.id == existing.branchDayId
        if (!sameMark || !sameBranch || !sameDay) {
            throw ConflictException("Attendance id already belongs to another clock-in request")
        }
        val isRelief = getIsRelief(existing.branchDayId, existing.userId)
        return AttendanceServiceResult(existing, false, isRelief)
    }
}

/**
 * #404 — the mark command's outcome. [attendance] is null only for an absent-mark that found
 * no open window (the idempotent no-op — nothing was written, no audit row exists).
 */
data class AttendanceMarkResult(
    val attendance: Attendance?,
    val created: Boolean,
    val isRelief: Boolean,
)

/** #404 — one roster row for the membership-gated attendance read. */
data class AttendanceRosterEntry(
    val assignmentId: UUID,
    val userId: UUID,
    val displayName: String,
    val slot: Short,
    val present: Boolean,
)

/**
 * The mark/roster gate (#404): an ACTIVE user holding an active home assignment at the
 * branch — membership + liveness, never a capability check. Runs on the caller's open
 * transaction (the assignment row is read FOR UPDATE, so a concurrent revocation
 * serializes with the command). File-level helper so AttendanceService stays inside its
 * function-count pin.
 */
private fun requireActiveMember(
    userId: UUID,
    branchId: UUID,
    message: String,
) {
    if (!AttendanceRepository.hasActiveMembershipInTransaction(branchId, userId)) {
        throw ForbiddenException(message)
    }
}

/**
 * Folded from AssignmentResolver (#539): two pass-through relief lookups with no
 * independent responsibility. File-level helpers so AttendanceService stays inside
 * its function-count pin.
 */
private fun resolveIsRelief(
    branchId: UUID,
    userId: UUID,
): Boolean = UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, userId) == null

private fun getIsRelief(
    branchDayId: UUID,
    userId: UUID,
): Boolean = AttendanceRepository.branchDayAssignmentIsRelief(branchDayId, userId) ?: false

data class AttendanceServiceResult(
    val attendance: Attendance,
    val created: Boolean,
    val isRelief: Boolean,
) {
    val id: UUID get() = attendance.id
    val branchDayId: UUID get() = attendance.branchDayId
    val userId: UUID get() = attendance.userId
    val markedBy: UUID get() = attendance.markedBy
    val clockIn: OffsetDateTime get() = attendance.clockIn
    val clockOut: OffsetDateTime? get() = attendance.clockOut
}

internal object AttendanceAudit {
    fun inserted(
        context: AuditContext,
        attendance: Attendance,
    ) = AuditLog.recordInsert(
        tableName = AttendanceTable.tableName,
        recordId = attendance.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = AttendanceTable.auditFields(attendance),
    )

    fun updated(
        context: AuditContext,
        before: Attendance,
        after: Attendance,
    ) = AuditLog.recordUpdate(
        tableName = AttendanceTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        auditFields = AttendanceTable::auditFields,
    )
}
