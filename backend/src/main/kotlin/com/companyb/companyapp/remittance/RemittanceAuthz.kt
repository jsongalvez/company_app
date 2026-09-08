package com.companyb.companyapp.remittance

import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import io.javalin.http.Context
import java.util.UUID

/**
 * Remittance-owned HTTP authorization composition (#605).
 *
 * Resolves the remittance's branch, then delegates to the common
 * branch-scope check on [CapabilityFilter]. Authorization keeps
 * capability-context evaluation; how a remittance resolves that scope lives
 * here, next to the routes that need it.
 */
object RemittanceAuthz {
    /**
     * Enforces [capabilityCode] on BRANCH by resolving the branch from a remittance record.
     *
     * Throws ForbiddenResponse (403) if the caller lacks the capability.
     * Throws NotFoundException (404) if the remittance does not exist.
     */
    fun requireBranchCapabilityForRemittance(
        context: Context,
        remittanceId: UUID,
        capabilityCode: String = CapabilityCodes.SUBMIT_REMITTANCE,
    ) {
        val branchId = RemittanceService.getBranchIdForRemittance(remittanceId)
        CapabilityFilter.requireBranchCapabilityForBranchId(context, branchId, capabilityCode)
    }
}
