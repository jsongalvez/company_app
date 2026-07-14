package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.sql.javatime.timestampWithTimeZone
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class Compensation(
    val id: UUID,
    val workBranchDayId: UUID,
    val payingBranchDayId: UUID,
    val userId: UUID,
    val amount: BigDecimal,
    val assignedBy: UUID,
    val assignedAt: OffsetDateTime,
    val note: String?,
)

object CompensationTable : Table("compensation") {
    private const val AMOUNT_PRECISION = 10
    private const val AMOUNT_SCALE = 2

    val id = uuid("id").autoGenerate()
    val workBranchDayId = uuid("work_branch_day_id")
    val payingBranchDayId = uuid("paying_branch_day_id")
    val userId = uuid("user_id")
    val amount = decimal("amount", AMOUNT_PRECISION, AMOUNT_SCALE)
    val assignedBy = uuid("assigned_by")
    val assignedAt = timestampWithTimeZone("assigned_at").defaultExpression(CurrentTimestampWithTimeZone)
    val note = text("note").nullable()

    override val primaryKey = PrimaryKey(id)
}
