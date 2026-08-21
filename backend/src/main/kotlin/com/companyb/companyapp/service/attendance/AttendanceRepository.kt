package com.companyb.companyapp.service.attendance

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.Attendance
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.Slice
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

internal data class ClockInParams(
    val attendanceId: UUID,
    val branchDayId: UUID,
    val userId: UUID,
    val markedBy: UUID,
    val branchDayAssignmentId: UUID,
    val isRelief: Boolean,
    val branchId: UUID,
)

data class BranchDayUser(
    val userId: UUID,
    val displayName: String,
)

/**
 * Attendance persistence (#321, ADR-0024). Mutating functions are **in-transaction store
 * operations**: they open no transaction of their own and take no audit callback — they execute
 * on the caller's (command-owned) transaction, which [AttendanceService] also uses to insert
 * the audit row atomically. Read helpers keep their convenient transaction wrappers.
 */
@Suppress("UnreachableCode")
internal object AttendanceRepository {
    fun hasActiveClockIn(
        userId: UUID,
        branchDayId: UUID,
    ): Boolean =
        transaction {
            hasActiveClockInInTransaction(userId, branchDayId)
        }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun hasActiveClockInInTransaction(
        userId: UUID,
        branchDayId: UUID,
    ): Boolean =
        AttendanceTable
            .selectAll()
            .where {
                (AttendanceTable.userId eq userId) and
                    (AttendanceTable.branchDayId eq branchDayId) and
                    (AttendanceTable.clockOut.isNull())
            }.empty()
            .not()

    fun clockInInTransaction(params: ClockInParams): Pair<Attendance, Boolean> {
        val insertedCount =
            AttendanceTable
                .insertIgnore {
                    it[AttendanceTable.id] = params.attendanceId
                    it[AttendanceTable.branchDayId] = params.branchDayId
                    it[AttendanceTable.userId] = params.userId
                    it[AttendanceTable.markedBy] = params.markedBy
                    it[AttendanceTable.clockIn] = CurrentTimestampWithTimeZone
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
                .singleOrNull()
                ?.toAttendance()
                ?: throw ConflictException("Attendance already exists for this user and branch day")

        val ownsExistingAttendance =
            attendance.branchDayId == params.branchDayId &&
                attendance.userId == params.userId &&
                attendance.markedBy == params.markedBy
        if (!isNew && !ownsExistingAttendance) {
            throw ConflictException("Attendance id already belongs to another clock-in request")
        }

        return attendance to isNew
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
            findByIdInTransaction(attendanceId)
        }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByIdInTransaction(attendanceId: UUID): Attendance? =
        AttendanceTable
            .selectAll()
            .where { AttendanceTable.id eq attendanceId }
            .singleOrNull()
            ?.toAttendance()

    fun clockOutInTransaction(attendanceId: UUID): Pair<Attendance, Boolean> {
        val updated =
            AttendanceTable
                .update({ (AttendanceTable.id eq attendanceId) and (AttendanceTable.clockOut.isNull()) }) {
                    it[AttendanceTable.clockOut] = CurrentTimestampWithTimeZone
                }

        val after =
            AttendanceTable
                .selectAll()
                .where { AttendanceTable.id eq attendanceId }
                .single()
                .toAttendance()

        return after to (updated > 0)
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

    fun findUsersByBranchDayId(branchDayId: UUID): List<BranchDayUser> =
        transaction {
            val join =
                AttendanceTable.innerJoin(AppUserTable, { AttendanceTable.userId }, { AppUserTable.id })
            Slice(join, listOf(AttendanceTable.userId, AppUserTable.displayName))
                .selectAll()
                .withDistinct()
                .where { AttendanceTable.branchDayId eq branchDayId }
                .orderBy(AppUserTable.displayName to SortOrder.ASC)
                .map { row ->
                    BranchDayUser(
                        userId = row[AttendanceTable.userId],
                        displayName = row[AppUserTable.displayName],
                    )
                }
        }.also { logger.info { "[FIND-BRANCH-DAY-USERS] Found ${it.size} users for branch_day $branchDayId" } }

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
