package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class MedicalMissionDelegate(
    override val id: UUID,
    val targetUser: UUID,
    val assignedAt: OffsetDateTime,
    val assignedBy: UUID,
    val branchId: UUID,
    val endedAt: OffsetDateTime?,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "targetUser" to targetUser.toString(),
            "assignedAt" to assignedAt.toString(),
            "assignedBy" to assignedBy.toString(),
            "branchId" to branchId.toString(),
            "endedAt" to (endedAt?.toString() ?: "null"),
        )
}

object MedicalMissionDelegateTable : Table("medical_mission_delegate") {
    val id = javaUUID("id").autoGenerate()
    val targetUser = javaUUID("target_user").references(AppUserTable.id)
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)
    val assignedBy = javaUUID("assigned_by").references(AppUserTable.id)
    val branchId = javaUUID("branch_id").references(BranchTable.id)
    val endedAt = timestampWithTimeZone("ended_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
