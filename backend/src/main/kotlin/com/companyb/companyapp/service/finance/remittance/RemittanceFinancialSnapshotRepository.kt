package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshot
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotCreateParams
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

internal object RemittanceFinancialSnapshotRepository {
    fun insert(params: RemittanceFinancialSnapshotCreateParams): RemittanceFinancialSnapshot =
        transaction {
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
            row.toSnapshot()
        }

    fun findByRemittanceId(remittanceId: UUID): RemittanceFinancialSnapshot? =
        transaction {
            RemittanceFinancialSnapshotTable
                .selectAll()
                .where { RemittanceFinancialSnapshotTable.remittanceId eq remittanceId }
                .singleOrNull()
                ?.toSnapshot()
        }

    /**
     * Deletes the snapshot row (undo carve-out: allowed when the parent remittance is DRAFT)
     * and returns the deleted row for the audit before-image. Null when no snapshot exists.
     */
    fun deleteByRemittanceId(remittanceId: UUID): RemittanceFinancialSnapshot? =
        transaction {
            val existing =
                RemittanceFinancialSnapshotTable
                    .selectAll()
                    .where { RemittanceFinancialSnapshotTable.remittanceId eq remittanceId }
                    .singleOrNull() ?: return@transaction null

            RemittanceFinancialSnapshotTable.deleteWhere {
                RemittanceFinancialSnapshotTable.remittanceId eq remittanceId
            }
            existing.toSnapshot()
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
