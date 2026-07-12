package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.time.OffsetDateTime
import java.util.UUID

data class MedicalMissionDelegate(
    val id: UUID,
    val targetUser: UUID,
    val assignedAt: OffsetDateTime,
    val assignedBy: UUID,
    val branchId: UUID,
    val endedAt: OffsetDateTime?,
)

object MedicalMissionDelegateTable : Table("medical_mission_delegate") {
    val id = uuid("id").autoGenerate()
    val targetUser = uuid("target_user").references(AppUserTable.id)
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)
    val assignedBy = uuid("assigned_by").references(AppUserTable.id)
    val branchId = uuid("branch_id").references(BranchTable.id)
    val endedAt = timestampWithTimeZone("ended_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
