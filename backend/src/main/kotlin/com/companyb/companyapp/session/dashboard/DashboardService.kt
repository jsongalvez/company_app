package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.notification.NotificationReads
import com.companyb.companyapp.session.Session
import com.companyb.companyapp.session.SessionReads
import com.companyb.companyapp.workforce.AttendanceService
import java.math.BigDecimal
import java.util.UUID

data class CommissionSummary(
    val amount: BigDecimal,
    val productSalesCount: Int,
)

data class DashboardData(
    val sessions: List<Session>,
    val clientNames: Map<UUID, ClientNames>,
    val voidedSessionIds: Set<UUID>,
    val practitioners: List<SessionPractitionerWithName>,
    val concerns: List<ConcernWithSessionId>,
    val commission: CommissionSummary,
    /** #366 — requested-practitioner display names, keyed by user id. */
    val requestedPractitionerNames: Map<UUID, String> = emptyMap(),
    /** #382 — owning branch per session id (one branch per dashboard read). */
    val branchIdBySession: Map<UUID, UUID> = emptyMap(),
)

/**
 * Enrichment for a single session — [DashboardData] minus the caller-scoped commission, which
 * is a dashboard-card concern, not part of `DashboardSessionResponse` (#151 Q3).
 */
data class SessionDetailData(
    val session: Session,
    val clientNames: Map<UUID, ClientNames>,
    val voidedSessionIds: Set<UUID>,
    val practitioners: List<SessionPractitionerWithName>,
    val concerns: List<ConcernWithSessionId>,
    /** #366 — requested-practitioner display names, keyed by user id. */
    val requestedPractitionerNames: Map<UUID, String> = emptyMap(),
    /** #382 — owning branch of the session's day (single-entry map). */
    val branchIdBySession: Map<UUID, UUID> = emptyMap(),
)

/**
 * Session dashboard read model for today at a branch. The dashboard is universal post-clock-in
 * (not capability-gated): the gate is the caller's own active clock-in at the branch — a
 * clocked-in practitioner may view the day, a caller clocked in elsewhere cannot read this
 * branch's data (no cross-branch window without a capability).
 *
 * Commission is the live view over [CommissionService]'s shared batched aggregation
 * (clocked-in at sale time + manual inclusions) for the caller only — the split rows are
 * authoritative while the day is OPEN but a forced recalc on a PAST day could serve stale
 * splits, so the card never reads them.
 */
object DashboardService {
    /**
     * @throws NotFoundException if the branch does not exist.
     * @throws ForbiddenException if the caller has no active clock-in at the branch today.
     */
    fun getToday(
        callerId: UUID,
        branchId: UUID,
    ): DashboardData {
        val branchDay = BranchDayService.getToday(branchId)

        if (!AttendanceService.hasActiveClockIn(callerId, branchDay.id)) {
            throw ForbiddenException("You are not clocked in at this branch")
        }

        val sessions = DashboardRepository.findSessionsByBranchDay(branchDay.id)
        val sessionIds = sessions.map { it.id }
        val clientNames = DashboardRepository.findClientNames(sessions.map { it.clientId })
        val voidedSessionIds = DashboardRepository.findVoidedSessionIds(sessionIds)
        val practitioners = DashboardRepository.findPractitioners(sessionIds)
        val concerns = DashboardRepository.findConcernsForSessionIds(sessionIds)
        val live = CommissionService.liveCommissions(branchDay.id)[callerId]
        val commission =
            if (live == null) {
                CommissionSummary(BigDecimal.ZERO, 0)
            } else {
                CommissionSummary(live.amount, live.eligibleSaleCount)
            }

        return DashboardData(
            sessions = sessions,
            clientNames = clientNames,
            voidedSessionIds = voidedSessionIds,
            practitioners = practitioners,
            concerns = concerns,
            commission = commission,
            requestedPractitionerNames =
                DashboardRepository.findUserDisplayNames(
                    sessions.mapNotNull { it.requestedPractitionerId },
                ),
            branchIdBySession = sessions.associate { it.id to branchId },
        )
    }

    /**
     * #152 single-session detail read for the notifications bearer path (#151 Q1/Q4/Q5): the
     * caller may fetch iff a notification row exists for `(sessionId, caller)` — any read state.
     * The notification IS the authorization (the #141 markRead ownership shape). 404 for both
     * non-bearer and missing sessions — one UI fallback, UUIDs are not guessable. No day-state
     * gate: the #138 `checkBranchDayReadable` rule governs capability-gated browsing surfaces;
     * notified sessions are COMPLETED with `nextAppointmentDate = today+2`, so their branch day
     * is today-or-past and a PAST/REMITTED `EDIT_PAST_DAY` requirement would 403 the primary
     * case (#151 Q2).
     *
     * @throws NotFoundException if no notification row exists for (sessionId, caller) or the
     * session does not exist. The missing-session half is defensive: the `session_id` FK keeps
     * dangling notifications out of a consistent DB, but the lookup still fails closed.
     */
    fun getSessionDetail(
        callerId: UUID,
        sessionId: UUID,
    ): SessionDetailData {
        if (!NotificationReads.existsForSessionAndUser(sessionId, callerId)) {
            throw NotFoundException("Session not found")
        }

        val session =
            SessionReads.findById(sessionId)
                ?: throw NotFoundException("Session not found")

        val clientNames = DashboardRepository.findClientNames(listOf(session.clientId))
        val voidedSessionIds = DashboardRepository.findVoidedSessionIds(listOf(sessionId))
        val practitioners = DashboardRepository.findPractitioners(listOf(sessionId))
        val concerns = DashboardRepository.findConcernsForSessionIds(listOf(sessionId))

        return SessionDetailData(
            session = session,
            clientNames = clientNames,
            voidedSessionIds = voidedSessionIds,
            practitioners = practitioners,
            concerns = concerns,
            requestedPractitionerNames =
                session.requestedPractitionerId?.let {
                    DashboardRepository.findUserDisplayNames(listOf(it))
                } ?: emptyMap(),
            // #382 — the detail read has no route branch context; resolve it from the day.
            branchIdBySession =
                BranchDayService.findById(session.branchDayId)?.let {
                    mapOf(session.id to it.branchId)
                } ?: emptyMap(),
        )
    }
}
