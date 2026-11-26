package com.companyb.companyapp.session

import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.isStatusCorrection
import io.javalin.http.NotFoundResponse
import java.util.UUID

/**
 * Session-owned HTTP authorization composition.
 *
 * Resolves the session record and any session-specific rule (status-correction
 * policy), then delegates to the common branch/day scope checks on
 * [Access]. Authorization keeps capability-context evaluation and
 * small HTTP adaptations for already-resolved scope; how a session resolves
 * that scope lives here, next to the routes that need it.
 */
object SessionAuthz {
    /** The session's day. Throws NotFoundResponse (404) when the session does not exist. */
    fun placeOf(sessionId: UUID): Place.Day {
        val session =
            SessionReads.findById(sessionId)
                ?: throw NotFoundResponse("Session not found")
        return Place.day(session.branchDayId)
    }

    /**
     * What moving the session to [newStatus] asks for on its day: [Act.WORK_DAY], then [Act.VOID] for a
     * correction. The command repeats the correction check after locking the branch day.
     * Throws NotFoundResponse (404) when the session does not exist.
     */
    fun statusNeeds(
        sessionId: UUID,
        newStatus: SessionStatus,
    ): List<Gate.Need> {
        val session =
            SessionReads.findById(sessionId)
                ?: throw NotFoundResponse("Session not found")
        val day = Place.day(session.branchDayId)
        val acts =
            if (isStatusCorrection(
                    session.sessionStatus,
                    newStatus,
                )
            ) {
                listOf(Act.WORK_DAY, Act.VOID)
            } else {
                listOf(Act.WORK_DAY)
            }
        return acts.map { Gate.Need(it, day) }
    }
}
