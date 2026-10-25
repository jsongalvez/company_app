package com.companyb.companyapp.remittance

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditedRow
import java.util.UUID

/**
 * Remittance audit vocabulary (ADR-0024 rule 3). The feature command calls these helpers
 * directly inside its own transaction — no callback coordination — so each audit row commits
 * with the mutation it describes. Centralizing them here keeps persistence `*Table` imports out
 * of the public command surface.
 */
internal object RemittanceAudit {
    fun draftInserted(
        changedBy: UUID,
        remittance: Remittance,
    ) = AuditLog.recordInsert(
        context = AuditContext(changedBy = changedBy, branchId = remittance.branchId),
        row = AuditedRow(RemittanceTable.tableName, remittance.id),
        fields = RemittanceTable.auditFields(remittance),
    )

    fun remittanceUpdated(
        changedBy: UUID,
        before: Remittance,
        after: Remittance,
        reason: String? = null,
    ) = AuditLog.recordUpdate(
        context = AuditContext(changedBy = changedBy, branchId = after.branchId, reason = reason),
        row = AuditedRow(RemittanceTable.tableName, after.id),
        before = before,
        after = after,
        auditFields = RemittanceTable::auditFields,
    )

    fun lineInserted(
        changedBy: UUID,
        branchId: UUID,
        line: RemittanceLine,
    ) = AuditLog.recordInsert(
        context = AuditContext(changedBy = changedBy, branchId = branchId),
        row = AuditedRow(RemittanceLineTable.tableName, line.id),
        fields = RemittanceLineTable.auditFields(line),
    )

    fun lineUpdated(
        changedBy: UUID,
        branchId: UUID,
        before: RemittanceLine,
        after: RemittanceLine,
    ) = AuditLog.recordUpdate(
        context = AuditContext(changedBy = changedBy, branchId = branchId),
        row = AuditedRow(RemittanceLineTable.tableName, after.id),
        before = before,
        after = after,
        auditFields = RemittanceLineTable::auditFields,
    )

    fun breakdownInserted(
        changedBy: UUID,
        branchId: UUID,
        breakdown: RemittanceDayBreakdown,
    ) = AuditLog.recordInsert(
        context = AuditContext(changedBy = changedBy, branchId = branchId),
        row = AuditedRow(RemittanceDayBreakdownTable.tableName, breakdown.id),
        fields = RemittanceDayBreakdownTable.auditFields(breakdown),
    )

    fun breakdownDeleted(
        changedBy: UUID,
        branchId: UUID,
        before: RemittanceDayBreakdown,
    ) = AuditLog.recordDelete(
        context = AuditContext(changedBy = changedBy, branchId = branchId),
        row = AuditedRow(RemittanceDayBreakdownTable.tableName, before.id),
        before = before,
        auditFields = RemittanceDayBreakdownTable::auditFields,
    )

    fun snapshotInserted(
        changedBy: UUID,
        branchId: UUID,
        snapshot: RemittanceFinancialSnapshot,
    ) = AuditLog.recordInsert(
        context = AuditContext(changedBy = changedBy, branchId = branchId),
        row = AuditedRow(RemittanceFinancialSnapshotTable.tableName, snapshot.remittanceId),
        fields = RemittanceFinancialSnapshotTable.auditFields(snapshot),
    )

    fun snapshotDeleted(
        changedBy: UUID,
        branchId: UUID,
        before: RemittanceFinancialSnapshot,
        reason: String? = null,
    ) = AuditLog.recordDelete(
        context = AuditContext(changedBy = changedBy, branchId = branchId, reason = reason),
        row = AuditedRow(RemittanceFinancialSnapshotTable.tableName, before.remittanceId),
        before = before,
        auditFields = RemittanceFinancialSnapshotTable::auditFields,
    )
}
