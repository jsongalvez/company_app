package com.companyb.companyapp.repository.model

import com.companyb.companyapp.domain.SessionType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
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

object SessionBaseRateTable : Table("session_base_rate") {
    private const val RATE_PRECISION = 10
    private const val RATE_SCALE = 2

    val id = uuid("id").autoGenerate()
    val setBy = uuid("set_by").references(AppUserTable.id)
    val branchId = uuid("branch_id").references(BranchTable.id)
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
}
