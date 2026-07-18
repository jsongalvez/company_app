package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.time.OffsetDateTime
import java.util.UUID

data class ReliefAccess(
    override val id: UUID,
    val branchDayId: UUID,
    val requestedBy: UUID,
    val requestStatus: ReliefStatus,
    val targetUser: UUID,
    val grantedBy: UUID?,
    val grantedAt: OffsetDateTime?,
) : Auditable {
    override fun toAuditFields(): Map<String, String> =
        mapOf(
            "id" to id.toString(),
            "branchDayId" to branchDayId.toString(),
            "requestedBy" to requestedBy.toString(),
            "requestStatus" to requestStatus.name,
            "targetUser" to targetUser.toString(),
            "grantedBy" to (grantedBy?.toString() ?: "null"),
            "grantedAt" to (grantedAt?.toString() ?: "null"),
        )
}

object GrantReliefAccessTable : Table("grant_relief_access") {
    val id = javaUUID("id").autoGenerate()
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)
    val requestedBy = javaUUID("requested_by").references(AppUserTable.id)
    val requestStatus =
        customEnumeration<ReliefStatus>(
            name = "request_status",
            sql = "relief_status",
            fromDb = { value -> ReliefStatus.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "relief_status"
                obj.value = it.name
                obj
            },
        ).default(ReliefStatus.PENDING)
    val targetUser = javaUUID("target_user").references(AppUserTable.id)
    val grantedBy = javaUUID("granted_by").references(AppUserTable.id).nullable()
    val grantedAt = timestampWithTimeZone("granted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
