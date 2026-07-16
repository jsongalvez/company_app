package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class RemittanceFinancialSnapshot(
    val remittanceId: UUID,
    val grossIncome: BigDecimal,
    val totalCompensation: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal,
    val snapshottedAt: OffsetDateTime,
)

private const val SNAPSHOT_PRECISION = 10
private const val SNAPSHOT_SCALE = 2

object RemittanceFinancialSnapshotTable : Table("remittance_financial_snapshot") {
    val remittanceId = javaUUID("remittance_id").references(RemittanceTable.id)
    val grossIncome = decimal("gross_income", SNAPSHOT_PRECISION, SNAPSHOT_SCALE)
    val totalCompensation = decimal("total_compensation", SNAPSHOT_PRECISION, SNAPSHOT_SCALE)
    val totalExpenses = decimal("total_expenses", SNAPSHOT_PRECISION, SNAPSHOT_SCALE)
    val netIncome = decimal("net_income", SNAPSHOT_PRECISION, SNAPSHOT_SCALE)
    val snapshottedAt = timestampWithTimeZone("snapshotted_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(remittanceId)
}
