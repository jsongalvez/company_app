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

/**
 * Immutable financial-snapshot store (#320, ADR-0024). Mutating functions are in-transaction
 * store operations on the caller's (command-owned) transaction; the read helper keeps its
 * convenience wrapper.
 */
@Suppress("UnreachableCode")
internal object RemittanceFinancialSnapshotRepository {
    fun insertInTransaction(params: RemittanceFinancialSnapshotCreateParams): RemittanceFinancialSnapshot {
        RemittanceFinancialSnapshotTable.insert {
            it[RemittanceFinancialSnapshotTable.remittanceId] = params.remittanceId
            it[RemittanceFinancialSnapshotTable.grossIncome] = params.grossIncome
            it[RemittanceFinancialSnapshotTable.totalCompensation] = params.totalCompensation
            it[RemittanceFinancialSnapshotTable.netIncome] = params.netIncome
            it[RemittanceFinancialSnapshotTable.totalExpenses] = params.totalExpenses
        }
        return findByRemittanceIdInTransaction(params.remittanceId)
            ?: error("snapshot not found after insert for ${params.remittanceId}")
    }

    fun findByRemittanceId(remittanceId: UUID): RemittanceFinancialSnapshot? =
        transaction {
            findByRemittanceIdInTransaction(remittanceId)
        }

    /** In-transaction read for command-owned flows — runs on the caller's open transaction. */
    fun findByRemittanceIdInTransaction(remittanceId: UUID): RemittanceFinancialSnapshot? =
        RemittanceFinancialSnapshotTable
            .selectAll()
            .where { RemittanceFinancialSnapshotTable.remittanceId eq remittanceId }
            .singleOrNull()
            ?.toSnapshot()

    /**
     * Deletes the snapshot row (undo carve-out: allowed when the parent remittance is DRAFT)
     * and returns the deleted row for the audit before-image. Null when no snapshot exists.
     */
    fun deleteByRemittanceIdInTransaction(remittanceId: UUID): RemittanceFinancialSnapshot? {
        val existing = findByRemittanceIdInTransaction(remittanceId) ?: return null

        RemittanceFinancialSnapshotTable.deleteWhere {
            RemittanceFinancialSnapshotTable.remittanceId eq remittanceId
        }
        return existing
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
