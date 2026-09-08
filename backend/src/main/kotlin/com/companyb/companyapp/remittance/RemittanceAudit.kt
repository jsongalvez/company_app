package com.companyb.companyapp.remittance

import com.companyb.companyapp.audit.AuditLog
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
    ) = AuditLog.recordInsert(
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
    ) = AuditLog.recordUpdate(
        tableName = RemittanceTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = changedBy,
        branchId = after.branchId,
        reason = reason,
        auditFields = RemittanceTable::auditFields,
    )

    fun lineInserted(
        changedBy: UUID,
        branchId: UUID,
        line: RemittanceLine,
    ) = AuditLog.recordInsert(
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
    ) = AuditLog.recordUpdate(
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
    ) = AuditLog.recordInsert(
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
    ) = AuditLog.recordDelete(
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
    ) = AuditLog.recordDelete(
        tableName = RemittanceFinancialSnapshotTable.tableName,
        recordId = before.remittanceId,
        before = before,
        changedBy = changedBy,
        branchId = branchId,
        reason = reason,
        auditFields = RemittanceFinancialSnapshotTable::auditFields,
    )
}
