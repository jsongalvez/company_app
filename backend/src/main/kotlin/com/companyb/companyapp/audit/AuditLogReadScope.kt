package com.companyb.companyapp.audit
import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.BranchReadScope
import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import java.util.UUID

/**
 * Audit read scoping authority — the single place the window + per-table
 * policy is declared (work item 1).
 *
 * Read window = the caller's branches with any active grant (#98 union pattern,
 * sourced from [CapabilityService.findBranchWindow]). Branchless (global)
 * tables get a declared per-table capability policy; rows on unlisted tables
 * with a NULL branch (legacy backfill rows) fall back to the
 * global-view proxy: a GLOBAL `VIEW_BRANCH_DATA` grant ("read-only
 * across all branches", role-derived for SUPERUSER, OWNER, and ACCOUNTANT).
 */
object AuditLogReadScope {
    /** Branchless tables: DB table name -> the [Act] a caller needs to read its rows. */
    private val BRANCHLESS_POLICY: Map<String, Act> =
        mapOf(
            // Per #104 D6: "client -> any EDIT_BRANCH_DATA holder" (clients are
            // global records; #99 F5 gates on GLOBAL EDIT_BRANCH_DATA).
            "client" to Act.WORK_ANYWHERE,
            // Concern catalog rows (promoteConcern writes with no branch; the
            // /api/concerns route gates GLOBAL EDIT_BRANCH_DATA, promote-concern
            // gates branch-scoped — any holder passes).
            "concern" to Act.WORK_ANYWHERE,
            // Global catalog management (ProductRoutes / ProductCategoryRoutes
            // gate GLOBAL MANAGE_CATALOG).
            "product" to Act.MANAGE_CATALOG,
            "product_category" to Act.MANAGE_CATALOG,
            // User/branch management (UserRoutes / BranchRoutes gate GLOBAL MANAGE_USERS).
            "app_user" to Act.MANAGE_USERS,
            "branch" to Act.MANAGE_USERS,
        )

    /**
     * Branch ids in the caller's read window — distinct branches where the
     * caller holds any active grant (union pattern, capability-sourced).
     * `null` = all branches: the caller holds a GLOBAL `VIEW_BRANCH_DATA` grant
     * (the read-only-across-all-branches shape for SUPERUSER, OWNER, or ACCOUNTANT).
     * Delegates to [BranchReadScope] — the shared all-branches window.
     */
    fun windowBranchIds(callerId: UUID): List<UUID>? = BranchReadScope.windowBranchIds(callerId)

    /**
     * Branchless tables whose declared policy the caller satisfies. Unlisted
     * tables (NULL-branch rows) are only readable through [canReadNullRows].
     */
    fun branchlessTablesReadableBy(callerId: UUID): Set<String> =
        BRANCHLESS_POLICY
            .filter { (_, act) -> Access.allows(callerId, act) }
            .keys

    /**
     * NULL-branch rows on unlisted tables (legacy backfill) — global-view
     * proxy D6: a GLOBAL `VIEW_BRANCH_DATA` grant.
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
