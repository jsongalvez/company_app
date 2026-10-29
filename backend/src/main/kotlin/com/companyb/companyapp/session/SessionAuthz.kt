package com.companyb.companyapp.session

import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
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
     * [Access.require] for [act] on the session's day.
     * Throws NotFoundResponse (404) when the session does not exist.
     */
    fun requireForSession(
        context: Context,
        sessionId: UUID,
        act: Act,
    ) {
        val session =
            SessionReads.findById(sessionId)
                ?: throw NotFoundResponse("Session not found")
        Access.require(context, act, Place.day(session.branchDayId))
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
            requireForSession(context, sessionId, Act.VOID)
        }
    }
}
