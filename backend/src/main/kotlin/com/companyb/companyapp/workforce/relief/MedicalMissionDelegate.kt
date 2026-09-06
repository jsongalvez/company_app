package com.companyb.companyapp.workforce.relief
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
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

internal object MedicalMissionDelegateTable : Table("medical_mission_delegate") {
    val id = javaUUID("id").autoGenerate()
    val targetUser = javaUUID("target_user").references(AppUserTable.id)
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)
    val assignedBy = javaUUID("assigned_by").references(AppUserTable.id)
    val branchId = javaUUID("branch_id").references(BranchTable.id)
    val endedAt = timestampWithTimeZone("ended_at").nullable()

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: MedicalMissionDelegate): Map<String, String?> =
        mapOf(
            "id" to entity.id.toString(),
            "targetUser" to entity.targetUser.toString(),
            "assignedAt" to entity.assignedAt.toString(),
            "assignedBy" to entity.assignedBy.toString(),
            "branchId" to entity.branchId.toString(),
            "endedAt" to entity.endedAt?.toString(),
        )
}
