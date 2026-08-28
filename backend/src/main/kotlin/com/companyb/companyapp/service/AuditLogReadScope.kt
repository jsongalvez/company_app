package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import java.util.UUID

/**
 * Audit read scoping authority — the single place the #104 D6 window + per-table
 * policy is declared (#122 work item 1).
 *
 * Read window = the caller's branches with any active grant (#98 union pattern,
 * sourced from [CapabilityService.findBranchWindow]). Branchless (global)
 * tables get a declared per-table capability policy; rows on unlisted tables
 * with a NULL branch (legacy backfill rows) fall back to the
 * global-view proxy: a GLOBAL `VIEW_BRANCH_DATA` grant ("read-only
 * across all branches", role-derived for SUPERUSER, OWNER, and ACCOUNTANT).
 */
object AuditLogReadScope {
    /** Branchless-table policy: DB table name -> required capability + context flavor. */
    private data class TablePolicy(
        val capabilityCode: String,
        val global: Boolean,
    )

    private val BRANCHLESS_POLICY: Map<String, TablePolicy> =
        mapOf(
            // Per #104 D6: "client -> any EDIT_BRANCH_DATA holder" (clients are
            // global records; #99 F5 gates on GLOBAL EDIT_BRANCH_DATA).
            "client" to TablePolicy(CapabilityCodes.EDIT_BRANCH_DATA, global = false),
            // Concern catalog rows (promoteConcern writes with no branch; the
            // /api/concerns route gates GLOBAL EDIT_BRANCH_DATA, promote-concern
            // gates branch-scoped — any holder passes).
            "concern" to TablePolicy(CapabilityCodes.EDIT_BRANCH_DATA, global = false),
            // Global catalog management (ProductRoutes / ProductCategoryRoutes
            // gate GLOBAL MANAGE_PRODUCTS).
            "product" to TablePolicy(CapabilityCodes.MANAGE_PRODUCTS, global = true),
            "product_category" to TablePolicy(CapabilityCodes.MANAGE_PRODUCTS, global = true),
            // User/branch management (UserRoutes / BranchRoutes gate GLOBAL MANAGE_USERS).
            "app_user" to TablePolicy(CapabilityCodes.MANAGE_USERS, global = true),
            "branch" to TablePolicy(CapabilityCodes.MANAGE_USERS, global = true),
        )

    /**
     * Branch ids in the caller's read window — distinct branches where the
     * caller holds any active grant (#98 union pattern, capability-sourced).
     * `null` = all branches: the caller holds a GLOBAL `VIEW_BRANCH_DATA` grant
     * (the read-only-across-all-branches shape for SUPERUSER, OWNER, or ACCOUNTANT).
     * Delegates to [BranchReadScope] — the shared all-branches window (#131).
     */
    fun windowBranchIds(callerId: UUID): List<UUID>? = BranchReadScope.windowBranchIds(callerId)

    /**
     * Branchless tables whose declared policy the caller satisfies. Unlisted
     * tables (NULL-branch rows) are only readable through [canReadNullRows].
     */
    fun branchlessTablesReadableBy(callerId: UUID): Set<String> =
        BRANCHLESS_POLICY
            .filter { (_, policy) ->
                when {
                    policy.global -> {
                        CapabilityService.hasCapability(
                            callerId,
                            policy.capabilityCode,
                            CapabilityContextType.GLOBAL,
                            CapabilityService.GLOBAL_CONTEXT_ID,
                        )
                    }

                    else -> {
                        CapabilityService.hasCapabilityAnyContext(callerId, policy.capabilityCode)
                    }
                }
            }.keys

    /**
     * NULL-branch rows on unlisted tables (legacy backfill) — global-view
     * proxy per #104 D6: a GLOBAL `VIEW_BRANCH_DATA` grant.
     */
    fun canReadNullRows(callerId: UUID): Boolean = BranchReadScope.hasGlobalView(callerId)

    /**
     * Single-entry read verdict — used by acknowledge and any per-row scope
     * check. Branch rows must be in the window; NULL-branch rows follow the
     * branchless-table policy or the global-view fallback.
     */
    fun canReadEntry(
        callerId: UUID,
        tableName: String,
        branchId: UUID?,
    ): Boolean =
        when {
            branchId != null -> {
                BranchReadScope.isBranchReadable(callerId, branchId)
            }

            else -> {
                tableName in branchlessTablesReadableBy(callerId) || canReadNullRows(callerId)
            }
        }
}
