package com.companyb.companyapp.workforce
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
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

internal object AttendanceTable : Table("attendance") {
    val id = javaUUID("id").autoGenerate()
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val markedBy = javaUUID("marked_by").references(AppUserTable.id)
    val clockIn = timestampWithTimeZone("clock_in").defaultExpression(CurrentTimestampWithTimeZone)
    val clockOut = timestampWithTimeZone("clock_out").nullable()

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: Attendance): Map<String, String?> =
        mapOf(
            "id" to entity.id.toString(),
            "branchDayId" to entity.branchDayId.toString(),
            "userId" to entity.userId.toString(),
            "markedBy" to entity.markedBy.toString(),
            "clockIn" to entity.clockIn.toString(),
            "clockOut" to entity.clockOut?.toString(),
        )
}

data class BranchDayAssignment(
    val id: UUID,
    val branchDayId: UUID,
    val userId: UUID,
    val isRelief: Boolean,
)

internal object BranchDayAssignmentTable : Table("branch_day_assignment") {
    val id = javaUUID("id").autoGenerate()
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val isRelief = bool("is_relief")

    override val primaryKey = PrimaryKey(id)
}
