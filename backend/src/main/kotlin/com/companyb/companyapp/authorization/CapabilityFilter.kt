package com.companyb.companyapp.authorization
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import io.javalin.http.Context
import java.util.UUID

/**
 * Route-level capability enforcement via Javalin before filters.
 *
 * Moves capability checks from the service layer to the HTTP layer so that
 * each route declares its authorization requirements upfront. The service
 * layer retains day-state assertions (e.g.
 * [com.companyb.companyapp.branchday.BranchDayService.checkBranchDayEditable]);
 * on the UserBranchAssignment surface it also retains the capability gates
 * themselves (deviation below).
 *
 * Exception (ADR-0007 deviation, #134): the UserBranchAssignment surface
 * enforces at the service layer — `swapSlots`' participant rule (MANAGE_USERS
 * OR caller is one of the swapped users) and `updateSlot`'s self-service rule
 * (MANAGE_USERS OR caller is the target user) cannot be expressed as path
 * filters, and the 4-segment before-filters never matched the 5-segment
 * sub-paths (the #114 exact-path lesson).
 *
 * Usage in a route object's `register`:
 * ```
 * config.routes.before(ApiRoutes.EXPENSES) { context ->
 *     val branchDayId = // extract from query / body / path
 *     CapabilityFilter.requireBranchCapability(context, branchDayId)
 * }
 * ```
 */
@Suppress("TooManyFunctions") // #538 authorization owner keeps the require* filter vocabulary on one adapter
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
     * Shared catalog gate (#455): products + categories share one MANAGE_CATALOG
     * enforcement point so the two route files stop duplicating the filter body.
     * Message stays caller-supplied to preserve each route's existing 403 text.
     */
    fun requireManageCatalog(
        context: Context,
        message: String,
    ) {
        requireGlobalCapability(context, CapabilityCodes.MANAGE_CATALOG, message)
    }

    /**
     * Enforces [capabilityCode] at any context (#660): BRANCH, BRANCH_DAY, or GLOBAL.
     * For global catalog reads with no branch context (GET /api/concerns) so every
     * EDIT_BRANCH_DATA holder — branch-assigned practitioners/coordinators via the
     * V21 BRANCH leg, relief holders via BRANCH_DAY, direct GLOBAL grantees — lists
     * the catalog the session-anchored promote write inserts into. Same capability
     * as the write; the #104 D6 any-context precedent.
     *
     * Throws [com.companyb.companyapp.exception.ForbiddenException] (403) if the caller
     * holds the capability in no context.
     */
    fun requireAnyContextCapability(
        context: Context,
        capabilityCode: String,
        message: String? = null,
    ) {
        val callerId = context.callerUuid()
        if (!CapabilityService.hasCapabilityAnyContext(callerId, capabilityCode)) {
            throw com.companyb.companyapp.exception.ForbiddenException(
                message ?: "$capabilityCode capability required",
            )
        }
    }

    /**
     * Enforces [capabilityCode] on [CapabilityContextType.BRANCH] for the given [branchDayId].
     * Resolves the branch from the branch day and calls [CapabilityService.requireCapability].
     *
     * Throws [io.javalin.http.ForbiddenResponse] (403) if the caller lacks the capability.
     * Throws NotFoundResponse (404) if the branch day does not exist.
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
     * Enforces [capabilityCode] at the given [branchId] — either BRANCH-scoped
     * at that branch OR GLOBAL (the #131 all-branches window: a GLOBAL
     * `VIEW_BRANCH_DATA` holder reads every branch, e.g. Owner/Accountant/SUPERUSER).
     *
     * Throws [com.companyb.companyapp.exception.ForbiddenException] (403) if
     * the caller holds neither form.
     */
    fun requireBranchOrGlobalCapabilityForBranchId(
        context: Context,
        branchId: UUID,
        capabilityCode: String,
    ) {
        val callerId = context.callerUuid()
        val branchScoped =
            CapabilityService.hasCapability(
                callerId,
                capabilityCode,
                CapabilityContextType.BRANCH,
                branchId,
            )
        val global =
            CapabilityService.hasCapability(
                callerId,
                capabilityCode,
                CapabilityContextType.GLOBAL,
                CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!branchScoped && !global) {
            throw com.companyb.companyapp.exception.ForbiddenException(
                "$capabilityCode capability required for this branch",
            )
        }
    }

    /**
     * Today-scoped gate for branch-uuid routes (#452): the HTTP entry to
     * [BranchDayService.requireBranchOrDayForToday] — find-only today resolution plus the
     * BRANCH-or-BRANCH_DAY OR (GLOBAL excluded per #131). Returns today's branch-day id
     * (null when no day row exists) so gates sharing the resolution with their write —
     * the session create's midnight-boundary handoff — can reuse it.
     *
     * Throws [com.companyb.companyapp.exception.ForbiddenException] (403) if the caller
     * holds neither form.
     */
    fun requireBranchOrDayForBranch(
        context: Context,
        branchId: UUID,
        capabilityCode: String = CapabilityCodes.EDIT_BRANCH_DATA,
    ): UUID? =
        BranchDayService.requireBranchOrDayForToday(
            userId = context.callerUuid(),
            branchId = branchId,
            capabilityCode = capabilityCode,
        )

    /**
     * Day-scoped variant (#157): enforces [capabilityCode] for the given [branchDayId] —
     * either BRANCH-scoped at the day's branch OR BRANCH_DAY-scoped for the day itself.
     * A relief grant (`BRANCH_DAY` context, day-scoped, windowed) satisfies the branch
     * gate for its granted day only. GLOBAL grants deliberately do NOT satisfy this
     * check (the #131 strictness: the OR adds only the narrower day-scoped form).
     *
     * Throws [com.companyb.companyapp.exception.ForbiddenException] (403) if the caller
     * holds neither form. Throws NotFoundResponse (404) if the branch day does not exist.
     */
    fun requireBranchOrBranchDayCapability(
        context: Context,
        branchDayId: UUID,
        capabilityCode: String = CapabilityCodes.EDIT_BRANCH_DATA,
    ) {
        val callerId = context.callerUuid()
        val branchId = resolveBranchIdFromBranchDay(branchDayId)
        CapabilityService.requireCapabilityForBranchDay(
            userId = callerId,
            capabilityCode = capabilityCode,
            branchId = branchId,
            branchDayId = branchDayId,
        )
    }

    /**
     * Read-side day leg (#158): enforces [capabilityCode] at [branchId] — either
     * BRANCH-scoped or GLOBAL (the standard branch read window) — OR a
     * [dayCapabilityCode] BRANCH_DAY grant for [branchDayId] (the relief grant: a
     * day-scoped `EDIT_BRANCH_DATA` holder reads the day they are granted). A null
     * [branchDayId] (no day row for the date) means the day leg is impossible — the
     * branch/global leg alone governs (the #157 no-grant lesson: a grant always
     * references an existing day row).
     *
     * Throws [com.companyb.companyapp.exception.ForbiddenException] (403) if the caller
     * holds none of the three forms.
     */
    fun requireBranchOrGlobalOrBranchDayCapabilityForBranchId(
        context: Context,
        branchId: UUID,
        branchDayId: UUID?,
        capabilityCode: String = CapabilityCodes.VIEW_BRANCH_DATA,
        dayCapabilityCode: String = CapabilityCodes.EDIT_BRANCH_DATA,
    ) {
        val callerId = context.callerUuid()
        val branchScoped =
            CapabilityService.hasCapability(
                callerId,
                capabilityCode,
                CapabilityContextType.BRANCH,
                branchId,
            )
        val global =
            CapabilityService.hasCapability(
                callerId,
                capabilityCode,
                CapabilityContextType.GLOBAL,
                CapabilityService.GLOBAL_CONTEXT_ID,
            )
        val dayScoped =
            branchDayId != null &&
                CapabilityService.hasCapability(
                    callerId,
                    dayCapabilityCode,
                    CapabilityContextType.BRANCH_DAY,
                    branchDayId,
                )
        if (!branchScoped && !global && !dayScoped) {
            throw com.companyb.companyapp.exception.ForbiddenException(
                "$capabilityCode capability required for this branch or day",
            )
        }
    }

    /**
     * Resolves a [branchId] by looking up a branch day by [branchDayId].
     * Throws NotFoundResponse (404) if the branch day does not exist.
     */
    fun resolveBranchIdFromBranchDay(branchDayId: UUID): UUID {
        val branchDay =
            BranchDayService.requireBranchDayExists(branchDayId)
        return branchDay.branchId
    }
}
