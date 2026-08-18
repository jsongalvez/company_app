package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import java.util.UUID

/**
 * Branch read-scoping authority — the single place the all-branches read
 * window is declared (#131, extracted from the #122 `AuditLogReadScope`
 * pattern so reports/export routes share it).
 *
 * Read window = the caller's branches with any active grant (#98 union
 * pattern, sourced from [CapabilityService.findBranchWindow]); `null` = all
 * branches — the caller holds a GLOBAL `VIEW_BRANCH_DATA` grant (the
 * Accountant "read-only across all branches" shape, V2 seed; SUPERUSER/OWNER
 * derive it via ADR-0023 — derived and direct GLOBAL rows are
 * indistinguishable in `active_user_capabilities`).
 */
object BranchReadScope {
    /**
     * Branch ids in the caller's read window — distinct branches where the
     * caller holds any active grant. `null` = all branches: the caller holds
     * a GLOBAL `VIEW_BRANCH_DATA` grant.
     */
    fun windowBranchIds(callerId: UUID): List<UUID>? {
        if (hasGlobalView(callerId)) return null
        return CapabilityService.findBranchWindow(callerId)
    }

    /** True when [branchId] falls inside the caller's read window. */
    fun isBranchReadable(
        callerId: UUID,
        branchId: UUID,
    ): Boolean = windowBranchIds(callerId)?.let { branchId in it } ?: true

    internal fun hasGlobalView(callerId: UUID): Boolean =
        CapabilityService.hasCapability(
            callerId,
            CapabilityCodes.VIEW_BRANCH_DATA,
            CapabilityContextType.GLOBAL,
            CapabilityService.GLOBAL_CONTEXT_ID,
        )
}
