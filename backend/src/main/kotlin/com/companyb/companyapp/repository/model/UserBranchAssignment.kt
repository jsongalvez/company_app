package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class UserBranchAssignment(
    val id: UUID,
    val userId: UUID,
    val branchId: UUID,
    val slot: Short,
    val assignedBy: UUID,
    val assignedAt: OffsetDateTime,
    val endedAt: OffsetDateTime?,
)

object UserBranchAssignmentTable : Table("user_branch_assignment") {
    val id = uuid("id").autoGenerate()
    val userId = uuid("user_id").references(AppUserTable.id)
    val branchId = uuid("branch_id").references(BranchTable.id)
    val slot = short("slot").default(0)
    val assignedBy = uuid("assigned_by").references(AppUserTable.id)
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)
    val endedAt = timestampWithTimeZone("ended_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
