package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class UserBranchAssignment(
    override val id: UUID,
    val userId: UUID,
    val branchId: UUID,
    val slot: Short,
    val assignedBy: UUID,
    val assignedAt: OffsetDateTime,
    val endedAt: OffsetDateTime?,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "userId" to userId.toString(),
            "branchId" to branchId.toString(),
            "slot" to slot.toString(),
            "assignedBy" to assignedBy.toString(),
            "assignedAt" to assignedAt.toString(),
            "endedAt" to (endedAt?.toString() ?: "null"),
        )
}

data class UserBranchAssignmentCreateParams(
    val id: UUID,
    val userId: UUID,
    val branchId: UUID,
    val slot: Short,
    val assignedBy: UUID,
)

object UserBranchAssignmentTable : Table("user_branch_assignment") {
    val id = javaUUID("id").autoGenerate()
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val branchId = javaUUID("branch_id").references(BranchTable.id)
    val slot = short("slot").default(0)
    val assignedBy = javaUUID("assigned_by").references(AppUserTable.id)
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)
    val endedAt = timestampWithTimeZone("ended_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
