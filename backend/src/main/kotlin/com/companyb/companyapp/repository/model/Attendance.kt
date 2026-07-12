package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class Attendance(
    val id: UUID,
    val branchDayId: UUID,
    val userId: UUID,
    val markedBy: UUID,
    val clockIn: OffsetDateTime,
    val clockOut: OffsetDateTime?,
)

object AttendanceTable : Table("attendance") {
    val id = uuid("id").autoGenerate()
    val branchDayId = uuid("branch_day_id").references(BranchDayTable.id)
    val userId = uuid("user_id").references(AppUserTable.id)
    val markedBy = uuid("marked_by").references(AppUserTable.id)
    val clockIn = timestampWithTimeZone("clock_in").defaultExpression(CurrentTimestampWithTimeZone)
    val clockOut = timestampWithTimeZone("clock_out").nullable()

    override val primaryKey = PrimaryKey(id)
}

data class BranchDayAssignment(
    val id: UUID,
    val branchDayId: UUID,
    val userId: UUID,
    val isRelief: Boolean,
)

object BranchDayAssignmentTable : Table("branch_day_assignment") {
    val id = uuid("id").autoGenerate()
    val branchDayId = uuid("branch_day_id").references(BranchDayTable.id)
    val userId = uuid("user_id").references(AppUserTable.id)
    val isRelief = bool("is_relief")

    override val primaryKey = PrimaryKey(id)
}
