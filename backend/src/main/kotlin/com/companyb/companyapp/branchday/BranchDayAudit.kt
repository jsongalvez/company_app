package com.companyb.companyapp.branchday

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditedRow
import java.util.UUID

/**
 * Branch Day audit vocabulary. The collaborating transition operations in
 * [BranchDayService] call this directly inside the caller's open transaction, so each
 * day-state change commits with its audit row or not at all. No other owner constructs
 * branch-day audit payloads.
 */
internal object BranchDayAudit {
    fun dayUpdated(
        changedBy: UUID,
        before: BranchDay,
        after: BranchDay,
        reason: String? = null,
    ) = AuditLog.recordUpdate(
        context = AuditContext(changedBy = changedBy, branchId = before.branchId, reason = reason),
        row = AuditedRow(BranchDayTable.tableName, after.id),
        before = before,
        after = after,
        auditFields = BranchDayTable::auditFields,
    )
}
