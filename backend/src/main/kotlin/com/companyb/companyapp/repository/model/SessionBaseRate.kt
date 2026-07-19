package com.companyb.companyapp.repository.model

import com.companyb.companyapp.domain.SessionType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.postgresql.util.PGobject
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class SessionBaseRate(
    val id: UUID,
    val setBy: UUID,
    val branchId: UUID,
    val sessionType: SessionType,
    val rate: BigDecimal,
    val effectiveFrom: OffsetDateTime,
    val effectiveUntil: OffsetDateTime,
)

data class SessionBaseRateCreateParams(
    val id: UUID,
    val setBy: UUID,
    val branchId: UUID,
    val sessionType: SessionType,
    val rate: BigDecimal,
    val effectiveFrom: OffsetDateTime,
    val effectiveUntil: OffsetDateTime,
)

object SessionBaseRateTable : Table("session_base_rate") {
    private const val RATE_PRECISION = 10
    private const val RATE_SCALE = 2

    val id = javaUUID("id").autoGenerate()
    val setBy = javaUUID("set_by").references(AppUserTable.id)
    val branchId = javaUUID("branch_id").references(BranchTable.id)
    val sessionType =
        customEnumeration<SessionType>(
            name = "session_type",
            sql = "session_type",
            fromDb = { value -> SessionType.valueOf(value as String) },
            toDb = {
                val obj = PGobject()
                obj.type = "session_type"
                obj.value = it.name
                obj
            },
        )
    val rate = decimal("rate", RATE_PRECISION, RATE_SCALE)
    val effectiveFrom = timestampWithTimeZone("effective_from").defaultExpression(CurrentTimestampWithTimeZone)
    val effectiveUntil = timestampWithTimeZone("effective_until")

    override val primaryKey = PrimaryKey(id)

    fun auditFields(entity: SessionBaseRate): Map<String, String> =
        mapOf(
            "id" to entity.id.toString(),
            "branchId" to entity.branchId.toString(),
            "sessionType" to entity.sessionType.name,
            "rate" to entity.rate.toPlainString(),
            "effectiveFrom" to entity.effectiveFrom.toString(),
            "effectiveUntil" to entity.effectiveUntil.toString(),
        )
}
