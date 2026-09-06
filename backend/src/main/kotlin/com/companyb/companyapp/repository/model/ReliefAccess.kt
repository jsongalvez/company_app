package com.companyb.companyapp.repository.model
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.identity.AppUserTable
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.time.OffsetDateTime
import java.util.UUID

data class ReliefAccess(
    val id: UUID,
    val branchDayId: UUID,
    val requestedBy: UUID,
    val requestStatus: ReliefAccessStatus,
    val grantedBy: UUID?,
    val grantedAt: OffsetDateTime?,
)

object GrantReliefAccessTable : Table("grant_relief_access") {
    val id = javaUUID("id").autoGenerate()
    val branchDayId = javaUUID("branch_day_id").references(BranchDayTable.id)
    val requestedBy = javaUUID("requested_by").references(AppUserTable.id)
    val requestStatus =
        customEnumeration<ReliefAccessStatus>(
            name = "request_status",
            sql = "relief_status",
            // SAFETY: PG enum column binds as String via customEnumeration #467
            fromDb = { value -> ReliefAccessStatus.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "relief_status"
                obj.value = it.name
                obj
            },
        ).default(ReliefAccessStatus.PENDING)
    val grantedBy = javaUUID("granted_by").references(AppUserTable.id).nullable()
    val grantedAt = timestampWithTimeZone("granted_at").nullable()

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: ReliefAccess): Map<String, String?> =
        mapOf(
            "id" to entity.id.toString(),
            "branchDayId" to entity.branchDayId.toString(),
            "requestedBy" to entity.requestedBy.toString(),
            "requestStatus" to entity.requestStatus.name,
            "grantedBy" to entity.grantedBy?.toString(),
            "grantedAt" to entity.grantedAt?.toString(),
        )
}
