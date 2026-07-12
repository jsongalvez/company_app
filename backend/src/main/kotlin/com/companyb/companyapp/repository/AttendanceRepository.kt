package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.Attendance
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

object AttendanceRepository {
    private val INSERT_ATTENDANCE_SQL =
        """
        WITH inserted AS (
            INSERT INTO attendance (id, branch_day_id, user_id, marked_by, clock_in)
            VALUES (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::timestamptz)
            ON CONFLICT (id) DO NOTHING
            RETURNING 1
        )
        SELECT EXISTS (SELECT 1 FROM inserted) AS inserted
        """.trimIndent()

    private val UPSERT_BRANCH_DAY_ASSIGNMENT_SQL =
        """
        INSERT INTO branch_day_assignment (id, branch_day_id, user_id, is_relief)
        VALUES (?::uuid, ?::uuid, ?::uuid, ?)
        ON CONFLICT (branch_day_id, user_id) DO NOTHING
        """.trimIndent()

    private val SELECT_ATTENDANCE_SQL =
        """
        SELECT id, branch_day_id, user_id, marked_by, clock_in, clock_out
        FROM attendance
        WHERE id = ?::uuid
        """.trimIndent()

    private val HAS_ACTIVE_CLOCK_IN_SQL =
        """
        SELECT 1 FROM attendance
        WHERE user_id = ?::uuid
          AND branch_day_id = ?::uuid
          AND clock_out IS NULL
        LIMIT 1
        """.trimIndent()

    fun hasActiveClockIn(
        userId: UUID,
        branchDayId: UUID,
    ): Boolean =
        transaction {
            exec(
                HAS_ACTIVE_CLOCK_IN_SQL,
                args =
                    listOf(
                        TextColumnType() to userId.toString(),
                        TextColumnType() to branchDayId.toString(),
                    ),
            ) { rs -> rs.next() }
        } ?: false

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
            val isNew =
                exec(
                    INSERT_ATTENDANCE_SQL,
                    args =
                        listOf(
                            TextColumnType() to attendanceId.toString(),
                            TextColumnType() to branchDayId.toString(),
                            TextColumnType() to userId.toString(),
                            TextColumnType() to markedBy.toString(),
                            TextColumnType() to clockIn.toString(),
                        ),
                    explicitStatementType = StatementType.SELECT,
                ) { rs ->
                    check(rs.next()) { "Expected insert status for attendance $attendanceId" }
                    rs.getBoolean("inserted")
                } ?: false

            if (isNew) {
                exec(
                    UPSERT_BRANCH_DAY_ASSIGNMENT_SQL,
                    args =
                        listOf(
                            TextColumnType() to branchDayAssignmentId.toString(),
                            TextColumnType() to branchDayId.toString(),
                            TextColumnType() to userId.toString(),
                            TextColumnType() to isRelief.toString(),
                        ),
                )
                logger.info { "[CLOCK-IN] Inserted attendance $attendanceId (relief=$isRelief)" }
            } else {
                logger.info { "[CLOCK-IN] Attendance $attendanceId already exists, returning existing" }
            }

            val attendance =
                exec(
                    SELECT_ATTENDANCE_SQL,
                    args = listOf(TextColumnType() to attendanceId.toString()),
                ) { rs ->
                    check(rs.next()) { "Expected attendance record $attendanceId" }
                    rs.toAttendance()
                } ?: error("Attendance not found after clock-in for $attendanceId")

            attendance to isNew
        }

    private fun java.sql.ResultSet.toAttendance(): Attendance =
        Attendance(
            id = UUID.fromString(getString("id")),
            branchDayId = UUID.fromString(getString("branch_day_id")),
            userId = UUID.fromString(getString("user_id")),
            markedBy = UUID.fromString(getString("marked_by")),
            clockIn = getObject("clock_in", OffsetDateTime::class.java),
            clockOut = getObject("clock_out", OffsetDateTime::class.java),
        )
}
