package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class Attendance(
    override val id: UUID,
    val branchDayId: UUID,
    val userId: UUID,
    val markedBy: UUID,
    val clockIn: OffsetDateTime,
    val clockOut: OffsetDateTime?,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "branchDayId" to branchDayId.toString(),
            "userId" to userId.toString(),
            "markedBy" to markedBy.toString(),
            "clockIn" to clockIn.toString(),
            "clockOut" to (clockOut?.toString() ?: "null"),
        )
}

object AttendanceTable : Table("attendance") {
    val id = javaUUID("id").autoGenerate()
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val markedBy = javaUUID("marked_by").references(AppUserTable.id)
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
    val id = javaUUID("id").autoGenerate()
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val isRelief = bool("is_relief")

    override val primaryKey = PrimaryKey(id)
}
