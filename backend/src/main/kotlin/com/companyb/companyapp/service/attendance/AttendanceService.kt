package com.companyb.companyapp.service.attendance

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.commission.CommissionService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

object AttendanceService {
    private val logger = KotlinLogging.logger {}

    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

    fun findUsersClockedInAt(
        branchDayId: UUID,
        at: OffsetDateTime,
    ): List<UUID> = AttendanceRepository.findUsersClockedInAt(branchDayId, at)

    fun findUsersByBranchDayId(branchDayId: UUID): List<BranchDayUser> {
        BranchDayService.requireBranchDayExists(branchDayId)
        return AttendanceRepository.findUsersByBranchDayId(branchDayId)
    }

    @Suppress("ThrowsCount")
    fun clockOut(
        attendanceId: UUID,
        callerId: UUID,
    ): AttendanceServiceResult {
        val existing = AttendanceRepository.findById(attendanceId)
        if (existing == null) {
            throw NotFoundException("Attendance record not found")
        }

        if (existing.clockOut != null) {
            val isRelief = AssignmentResolver.getIsRelief(existing.branchDayId, existing.userId)
            return AttendanceServiceResult(existing, false, isRelief)
        }

        val branchId = BranchDayService.requireBranchDayExists(existing.branchDayId).branchId

        val attendance =
            AttendanceRepository.clockOut(attendanceId) { before, after ->
                AuditLogRepository.recordUpdate(
                    tableName = AttendanceTable.tableName,
                    recordId = after.id,
                    before = before,
                    after = after,
                    changedBy = callerId,
                    branchId = branchId,
                    auditFields = AttendanceTable::auditFields,
                )
            }

        logger.info { "[CLOCK-OUT] User $callerId clocked out (attendance=$attendanceId)" }

        CommissionService.recalculate(attendance.branchDayId)

        val isRelief = AssignmentResolver.getIsRelief(attendance.branchDayId, attendance.userId)
        return AttendanceServiceResult(attendance, false, isRelief)
    }

    @Suppress("ThrowsCount")
    fun clockIn(
        attendanceId: UUID,
        branchId: UUID,
        callerId: UUID,
    ): AttendanceServiceResult {
        val today = LocalDate.now(manilaZone)
        val branchDay = BranchDayService.resolveOrCreate(branchId, today)

        ShiftGuard.ensureNoActiveClockIn(callerId, branchDay.id)

        val isRelief = AssignmentResolver.resolveIsRelief(branchId, callerId)

        val branchDayAssignmentId = UUID.randomUUID()

        val (attendance, wasCreated) =
            AttendanceRepository.clockIn(
                ClockInParams(
                    attendanceId = attendanceId,
                    branchDayId = branchDay.id,
                    userId = callerId,
                    markedBy = callerId,
                    branchDayAssignmentId = branchDayAssignmentId,
                    isRelief = isRelief,
                    branchId = branchId,
                ),
            ) { attendance ->
                AuditLogRepository.recordInsert(
                    tableName = AttendanceTable.tableName,
                    recordId = attendance.id,
                    changedBy = callerId,
                    branchId = branchId,
                    fields = AttendanceTable.auditFields(attendance),
                )
            }

        logger.info {
            "[CLOCK-IN] User $callerId clocked in at branch $branchId (relief=$isRelief, attendance=$attendanceId)"
        }

        CommissionService.recalculate(branchDay.id)

        return AttendanceServiceResult(attendance, wasCreated, isRelief)
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
