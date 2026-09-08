package com.companyb.companyapp.branchday

import com.companyb.companyapp.audit.AuditLog
import java.util.UUID

/**
 * Branch Day audit vocabulary (#603). The collaborating transition operations in
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
        tableName = BranchDayTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = changedBy,
        branchId = before.branchId,
        reason = reason,
        auditFields = BranchDayTable::auditFields,
    )
}
