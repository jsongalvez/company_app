package com.companyb.companyapp.commission

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.math.BigDecimal
import java.util.UUID

data class CommissionSplit(
    val id: UUID,
    val branchDayId: UUID,
    val userId: UUID,
    val amount: BigDecimal,
)

internal object CommissionSplitTable : Table("commission_split") {
    private const val AMOUNT_PRECISION = 15
    private const val AMOUNT_SCALE = 4

    val id = javaUUID("id").autoGenerate()
    val branchDayId = javaUUID("branch_day_id")
    val userId = javaUUID("user_id")
    val amount = decimal("amount", AMOUNT_PRECISION, AMOUNT_SCALE)

    override val primaryKey = PrimaryKey(id)
}
