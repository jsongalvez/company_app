package com.companyb.companyapp.service

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AttendanceRepository
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.ClockInParams
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditAction
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

object AttendanceService {
    private val logger = KotlinLogging.logger {}

    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

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
            val isRelief = getIsRelief(existing.branchDayId, existing.userId)
            return AttendanceServiceResult(existing, false, isRelief)
        }

        val attendance =
            AttendanceRepository.clockOut(attendanceId) { attendance ->
                AuditLogRepository.record(
                    tableName = AttendanceTable.tableName,
                    recordId = attendanceId,
                    action = AuditAction.UPDATE,
                    changedBy = callerId,
                    newValue = AuditLogRepository.jsonField("clockOut", "now"),
                )
            }

        logger.info { "[CLOCK-OUT] User $callerId clocked out (attendance=$attendanceId)" }

        CommissionEngineService.recalculate(attendance.branchDayId)

        val isRelief = getIsRelief(attendance.branchDayId, attendance.userId)
        return AttendanceServiceResult(attendance, false, isRelief)
    }

    private fun getIsRelief(
        branchDayId: UUID,
        userId: UUID,
    ): Boolean = AttendanceRepository.branchDayAssignmentIsRelief(branchDayId, userId) ?: false

    @Suppress("ThrowsCount")
    fun clockIn(
        attendanceId: UUID,
        branchId: UUID,
        callerId: UUID,
    ): AttendanceServiceResult {
        val today = LocalDate.now(manilaZone)
        val branchDay = BranchDayService.resolveOrCreate(branchId, today)

        val activeClockIn = AttendanceRepository.hasActiveClockIn(callerId, branchDay.id)
        if (activeClockIn) {
            throw ConflictException("User already has an active clock-in for this branch day")
        }

        val existingAssignment =
            UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, callerId)
        val isRelief = existingAssignment == null

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
                AuditLogRepository.record(
                    tableName = AttendanceTable.tableName,
                    recordId = attendance.id,
                    action = AuditAction.INSERT,
                    changedBy = callerId,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "attendanceId" to attendance.id.toString(),
                            "branchDayId" to attendance.branchDayId.toString(),
                            "branchId" to branchId.toString(),
                            "isRelief" to isRelief.toString(),
                        ),
                )
            }

        logger.info {
            "[CLOCK-IN] User $callerId clocked in at branch $branchId (relief=$isRelief, attendance=$attendanceId)"
        }

        CommissionEngineService.recalculate(branchDay.id)

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
