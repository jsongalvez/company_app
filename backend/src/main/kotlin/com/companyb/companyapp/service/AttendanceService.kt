package com.companyb.companyapp.service

import com.companyb.companyapp.repository.AttendanceRepository
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditAction
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.ConflictResponse
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

object AttendanceService {
    private val logger = KotlinLogging.logger {}

    private val manilaZone: ZoneId = ZoneId.of("Asia/Manila")

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
            throw ConflictResponse("User already has an active clock-in for this branch day")
        }

        val existingAssignment =
            UserBranchAssignmentRepository.findActiveByBranchAndUser(branchId, callerId)
        val isRelief = existingAssignment == null

        val branchDayAssignmentId = UUID.randomUUID()
        val now = OffsetDateTime.now()

        val (attendance, wasCreated) =
            AttendanceRepository.clockIn(
                attendanceId = attendanceId,
                branchDayId = branchDay.id,
                userId = callerId,
                markedBy = callerId,
                clockIn = now,
                branchDayAssignmentId = branchDayAssignmentId,
                isRelief = isRelief,
            )

        val auditNewValue =
            AuditLogRepository.jsonFields(
                "attendanceId" to attendanceId.toString(),
                "branchDayId" to branchDay.id.toString(),
                "branchId" to branchId.toString(),
                "isRelief" to isRelief.toString(),
            )
        AuditLogRepository.record(
            tableName = AttendanceTable.tableName,
            recordId = attendanceId,
            action = AuditAction.INSERT,
            changedBy = callerId,
            newValue = auditNewValue,
        )

        logger.info {
            "[CLOCK-IN] User $callerId clocked in at branch $branchId (relief=$isRelief, attendance=$attendanceId)"
        }

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
