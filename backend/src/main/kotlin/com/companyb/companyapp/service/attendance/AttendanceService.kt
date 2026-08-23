package com.companyb.companyapp.service.attendance

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.service.branchday.BranchDayRepository
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.commission.CommissionService
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

    @Suppress("ThrowsCount")
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
                val isRelief = AssignmentResolver.getIsRelief(existing.branchDayId, existing.userId)
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

            val isRelief = AssignmentResolver.getIsRelief(attendance.branchDayId, attendance.userId)
            AttendanceServiceResult(attendance, false, isRelief)
        }

    @Suppress("ThrowsCount")
    fun clockIn(
        attendanceId: UUID,
        branchId: UUID,
        callerId: UUID,
    ): AttendanceServiceResult =
        transaction {
            retryOutcomeOrNull(attendanceId, branchId, callerId)?.let { return@transaction it }

            val today = BranchDayService.currentOperationalDate()
            val branchDay = BranchDayService.resolveOrCreate(branchId, today)

            // #376 — take the branch-day row lock BEFORE the guard so clock-in serializes
            // against the retraction cutoff commands (they hold it while deciding); the
            // commission recalculation below re-acquires it harmlessly in this transaction.
            BranchDayRepository.acquireLock(branchDay.id)

            // A concurrent same-id retry may have committed while this transaction waited
            // on the lock — the idempotent readback must run again under it (#376 review).
            retryOutcomeOrNull(attendanceId, branchId, callerId)?.let { return@transaction it }

            ShiftGuard.ensureNoActiveClockIn(callerId, branchDay.id)

            val isRelief = AssignmentResolver.resolveIsRelief(branchId, callerId)

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

    /**
     * The idempotent-retry outcome for an attendance id that already exists, or null when
     * the id is free. An ownership mismatch (another caller/branch/day) still 409s — the
     * retry contract covers replaying your own request only.
     */
    @Suppress("ThrowsCount")
    private fun retryOutcomeOrNull(
        attendanceId: UUID,
        branchId: UUID,
        callerId: UUID,
    ): AttendanceServiceResult? {
        val existing = AttendanceRepository.findByIdInTransaction(attendanceId) ?: return null
        val sameCaller = existing.userId == callerId && existing.markedBy == callerId
        val sameBranch =
            BranchDayService.requireBranchDayExists(existing.branchDayId).branchId == branchId
        val sameDay = BranchDayService.findToday(branchId)?.id == existing.branchDayId
        if (!sameCaller || !sameBranch || !sameDay) {
            throw ConflictException("Attendance id already belongs to another clock-in request")
        }
        val isRelief = AssignmentResolver.getIsRelief(existing.branchDayId, existing.userId)
        return AttendanceServiceResult(existing, false, isRelief)
    }
}

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

private typealias Attendance = com.companyb.companyapp.repository.model.Attendance

internal object AttendanceAudit {
    fun inserted(
        context: AuditContext,
        attendance: Attendance,
    ) = AuditLogRepository.recordInsert(
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
    ) = AuditLogRepository.recordUpdate(
        tableName = AttendanceTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        auditFields = AttendanceTable::auditFields,
    )
}
