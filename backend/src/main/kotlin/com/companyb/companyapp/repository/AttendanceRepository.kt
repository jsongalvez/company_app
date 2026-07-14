package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.Attendance
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

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

    @Suppress("LongParameterList")
    fun clockIn(
        attendanceId: UUID,
        branchDayId: UUID,
        userId: UUID,
        markedBy: UUID,
        clockIn: OffsetDateTime,
        branchDayAssignmentId: UUID,
        isRelief: Boolean,
    ): Pair<Attendance, Boolean> =
        transaction {
            val insertedCount =
                AttendanceTable
                    .insertIgnore {
                        it[AttendanceTable.id] = attendanceId
                        it[AttendanceTable.branchDayId] = branchDayId
                        it[AttendanceTable.userId] = userId
                        it[AttendanceTable.markedBy] = markedBy
                        it[AttendanceTable.clockIn] = clockIn
                    }.insertedCount
            val isNew = insertedCount > 0

            if (isNew) {
                BranchDayAssignmentTable.insertIgnore {
                    it[BranchDayAssignmentTable.id] = branchDayAssignmentId
                    it[BranchDayAssignmentTable.branchDayId] = branchDayId
                    it[BranchDayAssignmentTable.userId] = userId
                    it[BranchDayAssignmentTable.isRelief] = isRelief
                }
                logger.info { "[CLOCK-IN] Inserted attendance $attendanceId (relief=$isRelief)" }
            } else {
                logger.info { "[CLOCK-IN] Attendance $attendanceId already exists, returning existing" }
            }

            val attendance =
                AttendanceTable
                    .selectAll()
                    .where { AttendanceTable.id eq attendanceId }
                    .single()
                    .toAttendance()

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
        clockOut: OffsetDateTime,
    ): Attendance =
        transaction {
            AttendanceTable
                .update({ (AttendanceTable.id eq attendanceId) and (AttendanceTable.clockOut.isNull()) }) {
                    it[AttendanceTable.clockOut] = clockOut
                }

            AttendanceTable
                .selectAll()
                .where { AttendanceTable.id eq attendanceId }
                .single()
                .toAttendance()
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

    private fun org.jetbrains.exposed.sql.ResultRow.toAttendance(): Attendance =
        Attendance(
            id = this[AttendanceTable.id],
            branchDayId = this[AttendanceTable.branchDayId],
            userId = this[AttendanceTable.userId],
            markedBy = this[AttendanceTable.markedBy],
            clockIn = this[AttendanceTable.clockIn],
            clockOut = this[AttendanceTable.clockOut],
        )
}
