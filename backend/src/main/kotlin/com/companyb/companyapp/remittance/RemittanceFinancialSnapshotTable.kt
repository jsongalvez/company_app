package com.companyb.companyapp.remittance

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

private const val SNAPSHOT_PRECISION = 10
private const val SNAPSHOT_SCALE = 2

internal object RemittanceFinancialSnapshotTable : Table("remittance_financial_snapshot") {
    val remittanceId = javaUUID("remittance_id").references(RemittanceTable.id)
    val grossIncome = decimal("gross_income", SNAPSHOT_PRECISION, SNAPSHOT_SCALE)
    val totalCompensation = decimal("total_compensation", SNAPSHOT_PRECISION, SNAPSHOT_SCALE)
    val totalExpenses = decimal("total_expenses", SNAPSHOT_PRECISION, SNAPSHOT_SCALE)
    val netIncome = decimal("net_income", SNAPSHOT_PRECISION, SNAPSHOT_SCALE)
    val snapshottedAt = timestampWithTimeZone("snapshotted_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(remittanceId)

    fun auditFields(entity: RemittanceFinancialSnapshot): Map<String, String> =
        mapOf(
            "remittanceId" to entity.remittanceId.toString(),
            "grossIncome" to entity.grossIncome.toPlainString(),
            "totalCompensation" to entity.totalCompensation.toPlainString(),
            "totalExpenses" to entity.totalExpenses.toPlainString(),
            "netIncome" to entity.netIncome.toPlainString(),
        )
}
