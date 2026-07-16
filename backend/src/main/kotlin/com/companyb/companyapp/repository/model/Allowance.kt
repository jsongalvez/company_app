package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class Allowance(
    val id: UUID,
    val branchDayId: UUID,
    val userId: UUID,
    val amount: BigDecimal,
    val assignedBy: UUID,
    val assignedAt: OffsetDateTime,
)

object AllowanceTable : Table("allowance") {
    private const val AMOUNT_PRECISION = 10
    private const val AMOUNT_SCALE = 2

    val id = javaUUID("id").autoGenerate()
    val branchDayId = javaUUID("branch_day_id")
    val userId = javaUUID("user_id")
    val amount = decimal("amount", AMOUNT_PRECISION, AMOUNT_SCALE)
    val assignedBy = javaUUID("assigned_by")
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(id)
}
