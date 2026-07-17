package com.companyb.companyapp.api.routes

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.RemittanceRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.service.CapabilityService
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import java.util.UUID

/**
 * Route-level capability enforcement via Javalin before filters.
 *
 * Moves capability checks from the service layer to the HTTP layer so that
 * each route declares its authorization requirements upfront. The service
 * layer retains day-state assertions (e.g. [com.companyb.companyapp.service.BranchDayService.assertEditable])
 * but no longer performs capability checks.
 *
 * Usage in a route object's `register`:
 * ```
 * config.routes.before("/api/expenses") { context ->
 *     val branchDayId = // extract from query / body / path
 *     CapabilityFilter.requireBranchCapability(context, branchDayId)
 * }
 * ```
 */
object CapabilityFilter {
    /**
     * Enforces [capabilityCode] on [CapabilityContextType.GLOBAL] with the nil UUID.
     *
     * Throws [io.javalin.http.ForbiddenResponse] (403) if the caller lacks the capability.
     */
    fun requireGlobalCapability(
        context: Context,
        capabilityCode: String,
        message: String? = null,
    ) {
        val callerId = context.callerUuid()
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = capabilityCode,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            message = message ?: "$capabilityCode capability required",
        )
    }

    /**
     * Enforces [capabilityCode] on [CapabilityContextType.BRANCH] for the given [branchDayId].
     * Resolves the branch from the branch day and calls [CapabilityService.requireCapability].
     *
     * Throws [io.javalin.http.ForbiddenResponse] (403) if the caller lacks the capability.
     * Throws [NotFoundResponse] (404) if the branch day does not exist.
     */
    fun requireBranchCapability(
        context: Context,
        branchDayId: UUID,
        capabilityCode: String = CapabilityCodes.EDIT_BRANCH_DATA,
    ) {
        val callerId = context.callerUuid()
        val branchId = resolveBranchIdFromBranchDay(branchDayId)
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = capabilityCode,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            message = "$capabilityCode capability required for this branch",
        )
    }

    /**
     * Enforces [capabilityCode] on [CapabilityContextType.BRANCH] by resolving the branch
     * from an expense record.
     *
     * Throws [io.javalin.http.ForbiddenResponse] (403) if the caller lacks the capability.
     * Throws [NotFoundResponse] (404) if the expense or branch day does not exist.
     */
    fun requireBranchCapabilityForExpense(
        context: Context,
        expenseId: UUID,
        capabilityCode: String = CapabilityCodes.EDIT_BRANCH_DATA,
    ) {
        val expense =
            com.companyb.companyapp.repository.ExpenseRepository
                .findById(expenseId)
                ?: throw NotFoundResponse("Expense not found")
        requireBranchCapability(context, expense.branchDayId, capabilityCode)
    }

    /**
     * Enforces [capabilityCode] on [CapabilityContextType.BRANCH] by resolving the branch
     * from a remittance record.
     *
     * Throws [io.javalin.http.ForbiddenResponse] (403) if the caller lacks the capability.
     * Throws [NotFoundResponse] (404) if the remittance does not exist.
     */
    fun requireBranchCapabilityForRemittance(
        context: Context,
        remittanceId: UUID,
        capabilityCode: String = CapabilityCodes.SUBMIT_REMITTANCE,
    ) {
        val remittance =
            RemittanceRepository.findById(remittanceId)
                ?: throw NotFoundResponse("Remittance not found")
        val callerId = context.callerUuid()
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = capabilityCode,
            contextType = CapabilityContextType.BRANCH,
            contextId = remittance.branchId,
            message = "$capabilityCode capability required for this branch",
        )
    }

    /**
     * Enforces [capabilityCode] on [CapabilityContextType.BRANCH] for the given [branchId].
     * Does not require a branch day — use when the branch UUID is directly available.
     *
     * Throws [io.javalin.http.ForbiddenResponse] (403) if the caller lacks the capability.
     */
    fun requireBranchCapabilityForBranchId(
        context: Context,
        branchId: UUID,
        capabilityCode: String,
    ) {
        val callerId = context.callerUuid()
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = capabilityCode,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            message = "$capabilityCode capability required for this branch",
        )
    }

    /**
     * Enforces [capabilityCode] on [CapabilityContextType.BRANCH] by resolving the branch
     * from a session record.
     *
     * Throws [io.javalin.http.ForbiddenResponse] (403) if the caller lacks the capability.
     * Throws [NotFoundResponse] (404) if the session does not exist.
     */
    fun requireBranchCapabilityForSession(
        context: Context,
        sessionId: UUID,
        capabilityCode: String,
    ) {
        val session =
            SessionRepository.findById(sessionId)
                ?: throw NotFoundResponse("Session not found")
        requireBranchCapability(context, session.branchDayId, capabilityCode)
    }

    /**
     * Resolves a [branchId] by looking up a branch day by [branchDayId].
     * Throws [NotFoundResponse] (404) if the branch day does not exist.
     */
    fun resolveBranchIdFromBranchDay(branchDayId: UUID): UUID {
        val branchDay =
            BranchDayRepository.findById(branchDayId)
                ?: throw NotFoundResponse("Branch day not found")
        return branchDay.branchId
    }
}
