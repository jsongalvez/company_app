package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.time.OffsetDateTime
import java.util.UUID

data class ReliefAccess(
    val id: UUID,
    val branchDayId: UUID,
    val requestedBy: UUID,
    val requestStatus: ReliefStatus,
    val targetUser: UUID,
    val grantedBy: UUID?,
    val grantedAt: OffsetDateTime?,
)

object GrantReliefAccessTable : Table("grant_relief_access") {
    val id = uuid("id").autoGenerate()
    val branchDayId = uuid("branch_day_id").references(BranchDayTable.id)
    val requestedBy = uuid("requested_by").references(AppUserTable.id)
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
    val targetUser = uuid("target_user").references(AppUserTable.id)
    val grantedBy = uuid("granted_by").references(AppUserTable.id).nullable()
    val grantedAt = timestampWithTimeZone("granted_at").nullable()

    override val primaryKey = PrimaryKey(id)
}
