package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.sql.Table
import java.math.BigDecimal
import java.util.UUID

data class CommissionSplit(
    val id: UUID,
    val branchDayId: UUID,
    val userId: UUID,
    val amount: BigDecimal,
)

object CommissionSplitTable : Table("commission_split") {
    private const val AMOUNT_PRECISION = 15
    private const val AMOUNT_SCALE = 4

    val id = uuid("id").autoGenerate()
    val branchDayId = uuid("branch_day_id")
    val userId = uuid("user_id")
    val amount = decimal("amount", AMOUNT_PRECISION, AMOUNT_SCALE)

    override val primaryKey = PrimaryKey(id)
}
