package com.companyb.companyapp.workforce
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
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

data class UserBranchAssignmentCreateParams(
    val id: UUID,
    val userId: UUID,
    val branchId: UUID,
    val slot: Short,
    val assignedBy: UUID,
)

internal object UserBranchAssignmentTable : Table("user_branch_assignment") {
    val id = javaUUID("id").autoGenerate()
    val userId = javaUUID("user_id").references(AppUserTable.id)
    val branchId = javaUUID("branch_id").references(BranchTable.id)
    val slot = short("slot").default(0)
    val assignedBy = javaUUID("assigned_by").references(AppUserTable.id)
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)
    val endedAt = timestampWithTimeZone("ended_at").nullable()

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: UserBranchAssignment): Map<String, String?> =
        mapOf(
            "id" to entity.id.toString(),
            "userId" to entity.userId.toString(),
            "branchId" to entity.branchId.toString(),
            "slot" to entity.slot.toString(),
            "assignedBy" to entity.assignedBy.toString(),
            "assignedAt" to entity.assignedAt.toString(),
            "endedAt" to entity.endedAt?.toString(),
        )
}
