package com.companyb.companyapp.service.finance.remittance

import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.model.BranchDay
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.Remittance
import com.companyb.companyapp.repository.model.RemittanceDayBreakdown
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshot
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLine
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceTable
import java.util.UUID

/**
 * Remittance audit vocabulary (#320, ADR-0024 rule 3). The feature command calls these helpers
 * directly inside its own transaction — no callback coordination — so each audit row commits
 * with the mutation it describes. Centralizing them here keeps persistence `*Table` imports out
 * of the public command surface.
 */
internal object RemittanceAudit {
    fun draftInserted(
        changedBy: UUID,
        remittance: Remittance,
    ) = AuditLogRepository.recordInsert(
        tableName = RemittanceTable.tableName,
        recordId = remittance.id,
        changedBy = changedBy,
        branchId = remittance.branchId,
        fields = RemittanceTable.auditFields(remittance),
    )

    fun remittanceUpdated(
        changedBy: UUID,
        before: Remittance,
        after: Remittance,
        reason: String? = null,
    ) = AuditLogRepository.recordUpdate(
        tableName = RemittanceTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = changedBy,
        branchId = after.branchId,
        reason = reason,
        auditFields = RemittanceTable::auditFields,
    )

    fun branchDayUpdated(
        changedBy: UUID,
        before: BranchDay,
        after: BranchDay,
        reason: String? = null,
    ) = AuditLogRepository.recordUpdate(
        tableName = BranchDayTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = changedBy,
        branchId = before.branchId,
        reason = reason,
        auditFields = BranchDayTable::auditFields,
    )

    fun lineInserted(
        changedBy: UUID,
        branchId: UUID,
        line: RemittanceLine,
    ) = AuditLogRepository.recordInsert(
        tableName = RemittanceLineTable.tableName,
        recordId = line.id,
        changedBy = changedBy,
        branchId = branchId,
        fields = RemittanceLineTable.auditFields(line),
    )

    fun lineUpdated(
        changedBy: UUID,
        branchId: UUID,
        before: RemittanceLine,
        after: RemittanceLine,
    ) = AuditLogRepository.recordUpdate(
        tableName = RemittanceLineTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = changedBy,
        branchId = branchId,
        auditFields = RemittanceLineTable::auditFields,
    )

    fun breakdownInserted(
        changedBy: UUID,
        branchId: UUID,
        breakdown: RemittanceDayBreakdown,
    ) = AuditLogRepository.recordInsert(
        tableName = RemittanceDayBreakdownTable.tableName,
        recordId = breakdown.id,
        changedBy = changedBy,
        branchId = branchId,
        fields = RemittanceDayBreakdownTable.auditFields(breakdown),
    )

    fun breakdownDeleted(
        changedBy: UUID,
        branchId: UUID,
        before: RemittanceDayBreakdown,
    ) = AuditLogRepository.recordDelete(
        tableName = RemittanceDayBreakdownTable.tableName,
        recordId = before.id,
        before = before,
        changedBy = changedBy,
        branchId = branchId,
        auditFields = RemittanceDayBreakdownTable::auditFields,
    )

    fun snapshotDeleted(
        changedBy: UUID,
        branchId: UUID,
        before: RemittanceFinancialSnapshot,
        reason: String? = null,
    ) = AuditLogRepository.recordDelete(
        tableName = RemittanceFinancialSnapshotTable.tableName,
        recordId = before.remittanceId,
        before = before,
        changedBy = changedBy,
        branchId = branchId,
        reason = reason,
        auditFields = RemittanceFinancialSnapshotTable::auditFields,
    )
}
