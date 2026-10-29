package com.companyb.companyapp.session

import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.isStatusCorrection
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import java.util.UUID

/**
 * Session-owned HTTP authorization composition.
 *
 * Resolves the session record and any session-specific rule (status-correction
 * policy), then delegates to the common branch/day scope checks on
 * [CapabilityFilter]. Authorization keeps capability-context evaluation and
 * small HTTP adaptations for already-resolved scope; how a session resolves
 * that scope lives here, next to the routes that need it.
 */
object SessionAuthz {
    /**
     * Enforces [capabilityCode] on BRANCH by resolving the branch from a session record.
     *
     * Throws ForbiddenResponse (403) if the caller lacks the capability.
     * Throws NotFoundResponse (404) if the session does not exist.
     */
    fun requireBranchCapabilityForSession(
        context: Context,
        sessionId: UUID,
        capabilityCode: String,
    ) {
        val session =
            SessionReads.findById(sessionId)
                ?: throw NotFoundResponse("Session not found")
        CapabilityFilter.requireBranchCapability(context, session.branchDayId, capabilityCode)
    }

    /**
     * Day-scoped variant of [requireBranchCapabilityForSession]: accepts a BRANCH
     * grant at the session's branch OR a BRANCH_DAY grant for the session's branch day.
     *
     * Throws ForbiddenException (403) if the caller holds neither form.
     * Throws NotFoundResponse (404) if the session or branch day does not exist.
     */
    fun requireBranchOrBranchDayCapabilityForSession(
        context: Context,
        sessionId: UUID,
        capabilityCode: String = CapabilityCodes.EDIT_BRANCH_DATA,
    ) {
        val session =
            SessionReads.findById(sessionId)
                ?: throw NotFoundResponse("Session not found")
        CapabilityFilter.requireBranchOrBranchDayCapability(context, session.branchDayId, capabilityCode)
    }

    /**
     * Status corrections (no-show to cancelled and the like) are for branch members, as voiding is:
     * VOID_SESSION at the session's branch, which relief day grants never carry. The command
     * repeats this check after locking the branch day.
     */
    fun requireStatusCorrectionCapability(
        context: Context,
        sessionId: UUID,
        newStatus: SessionStatus,
    ) {
        val session =
            SessionReads.findById(sessionId)
                ?: throw NotFoundResponse("Session not found")
        if (isStatusCorrection(session.sessionStatus, newStatus)) {
            requireBranchCapabilityForSession(context, sessionId, CapabilityCodes.VOID_SESSION)
        }
    }
}
