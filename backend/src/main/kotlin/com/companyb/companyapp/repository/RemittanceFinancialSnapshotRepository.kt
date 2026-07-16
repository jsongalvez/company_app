package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshot
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.math.BigDecimal
import java.util.UUID

object RemittanceFinancialSnapshotRepository {
    fun insert(
        remittanceId: UUID,
        grossIncome: BigDecimal,
        totalCompensation: BigDecimal,
        totalExpenses: BigDecimal,
        netIncome: BigDecimal,
    ): RemittanceFinancialSnapshot {
        RemittanceFinancialSnapshotTable.insert {
            it[RemittanceFinancialSnapshotTable.remittanceId] = remittanceId
            it[RemittanceFinancialSnapshotTable.grossIncome] = grossIncome
            it[RemittanceFinancialSnapshotTable.totalCompensation] = totalCompensation
            it[RemittanceFinancialSnapshotTable.netIncome] = netIncome
            it[RemittanceFinancialSnapshotTable.totalExpenses] = totalExpenses
        }
        val row =
            RemittanceFinancialSnapshotTable
                .selectAll()
                .where { RemittanceFinancialSnapshotTable.remittanceId eq remittanceId }
                .single()
        return row.toSnapshot()
    }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toSnapshot(): RemittanceFinancialSnapshot =
        RemittanceFinancialSnapshot(
            remittanceId = this[RemittanceFinancialSnapshotTable.remittanceId],
            grossIncome = this[RemittanceFinancialSnapshotTable.grossIncome],
            totalCompensation = this[RemittanceFinancialSnapshotTable.totalCompensation],
            totalExpenses = this[RemittanceFinancialSnapshotTable.totalExpenses],
            netIncome = this[RemittanceFinancialSnapshotTable.netIncome],
            snapshottedAt = this[RemittanceFinancialSnapshotTable.snapshottedAt],
        )
}
