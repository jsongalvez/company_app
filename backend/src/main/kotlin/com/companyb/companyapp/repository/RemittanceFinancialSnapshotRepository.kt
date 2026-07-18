package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.CreateRemittanceFinancialSnapshot
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshot
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll

object RemittanceFinancialSnapshotRepository {
    fun insert(params: CreateRemittanceFinancialSnapshot): RemittanceFinancialSnapshot {
        RemittanceFinancialSnapshotTable.insert {
            it[RemittanceFinancialSnapshotTable.remittanceId] = params.remittanceId
            it[RemittanceFinancialSnapshotTable.grossIncome] = params.grossIncome
            it[RemittanceFinancialSnapshotTable.totalCompensation] = params.totalCompensation
            it[RemittanceFinancialSnapshotTable.netIncome] = params.netIncome
            it[RemittanceFinancialSnapshotTable.totalExpenses] = params.totalExpenses
        }
        val row =
            RemittanceFinancialSnapshotTable
                .selectAll()
                .where { RemittanceFinancialSnapshotTable.remittanceId eq params.remittanceId }
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
