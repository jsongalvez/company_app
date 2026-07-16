package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.Attendance
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

data class ClockInParams(
    val attendanceId: UUID,
    val branchDayId: UUID,
    val userId: UUID,
    val markedBy: UUID,
    val branchDayAssignmentId: UUID,
    val isRelief: Boolean,
    val branchId: UUID,
)

private val logger = KotlinLogging.logger {}

object AttendanceRepository {
    fun hasActiveClockIn(
        userId: UUID,
        branchDayId: UUID,
    ): Boolean =
        transaction {
            AttendanceTable
                .selectAll()
                .where {
                    (AttendanceTable.userId eq userId) and
                        (AttendanceTable.branchDayId eq branchDayId) and
                        (AttendanceTable.clockOut.isNull())
                }.empty()
                .not()
        }

    fun clockIn(params: ClockInParams): Pair<Attendance, Boolean> =
        transaction {
            val insertedCount =
                AttendanceTable
                    .insertIgnore {
                        it[AttendanceTable.id] = params.attendanceId
                        it[AttendanceTable.branchDayId] = params.branchDayId
                        it[AttendanceTable.userId] = params.userId
                        it[AttendanceTable.markedBy] = params.markedBy
                        // defaultExpression suppressed by insertIgnore
                        it[AttendanceTable.clockIn] = OffsetDateTime.now(ZoneOffset.UTC)
                    }.insertedCount
            val isNew = insertedCount > 0

            if (isNew) {
                BranchDayAssignmentTable.insertIgnore {
                    it[BranchDayAssignmentTable.id] = params.branchDayAssignmentId
                    it[BranchDayAssignmentTable.branchDayId] = params.branchDayId
                    it[BranchDayAssignmentTable.userId] = params.userId
                    it[BranchDayAssignmentTable.isRelief] = params.isRelief
                }
                logger.info { "[CLOCK-IN] Inserted attendance ${params.attendanceId} (relief=${params.isRelief})" }
            } else {
                logger.info { "[CLOCK-IN] Attendance ${params.attendanceId} already exists, returning existing" }
            }

            val attendance =
                AttendanceTable
                    .selectAll()
                    .where { AttendanceTable.id eq params.attendanceId }
                    .single()
                    .toAttendance()

            if (isNew) {
                AuditLogRepository.record(
                    tableName = AttendanceTable.tableName,
                    recordId = params.attendanceId,
                    action = AuditAction.INSERT,
                    changedBy = params.markedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "attendanceId" to params.attendanceId.toString(),
                            "branchDayId" to params.branchDayId.toString(),
                            "branchId" to params.branchId.toString(),
                            "isRelief" to params.isRelief.toString(),
                        ),
                )
            }

            attendance to isNew
        }

    fun branchDayAssignmentIsRelief(
        branchDayId: UUID,
        userId: UUID,
    ): Boolean? =
        transaction {
            BranchDayAssignmentTable
                .selectAll()
                .where {
                    (BranchDayAssignmentTable.branchDayId eq branchDayId) and
                        (BranchDayAssignmentTable.userId eq userId)
                }.singleOrNull()
                ?.let { it[BranchDayAssignmentTable.isRelief] }
        }

    fun findById(attendanceId: UUID): Attendance? =
        transaction {
            AttendanceTable
                .selectAll()
                .where { AttendanceTable.id eq attendanceId }
                .singleOrNull()
                ?.toAttendance()
        }

    fun clockOut(
        attendanceId: UUID,
        callerId: UUID,
    ): Attendance =
        transaction {
            AttendanceTable
                .update({ (AttendanceTable.id eq attendanceId) and (AttendanceTable.clockOut.isNull()) }) {
                    it[AttendanceTable.clockOut] = CurrentTimestampWithTimeZone
                }

            val attendance =
                AttendanceTable
                    .selectAll()
                    .where { AttendanceTable.id eq attendanceId }
                    .single()
                    .toAttendance()

            AuditLogRepository.record(
                tableName = AttendanceTable.tableName,
                recordId = attendanceId,
                action = AuditAction.UPDATE,
                changedBy = callerId,
                newValue = AuditLogRepository.jsonField("clockOut", "now"),
            )

            attendance
        }

    fun findUsersClockedInAt(
        branchDayId: UUID,
        atTime: OffsetDateTime,
    ): List<UUID> =
        transaction {
            AttendanceTable
                .selectAll()
                .where {
                    (AttendanceTable.branchDayId eq branchDayId) and
                        (AttendanceTable.clockIn lessEq atTime) and
                        (AttendanceTable.clockOut.isNull() or (AttendanceTable.clockOut greaterEq atTime))
                }.map { it[AttendanceTable.userId] }
        }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toAttendance(): Attendance =
        Attendance(
            id = this[AttendanceTable.id],
            branchDayId = this[AttendanceTable.branchDayId],
            userId = this[AttendanceTable.userId],
            markedBy = this[AttendanceTable.markedBy],
            clockIn = this[AttendanceTable.clockIn],
            clockOut = this[AttendanceTable.clockOut],
        )
}
