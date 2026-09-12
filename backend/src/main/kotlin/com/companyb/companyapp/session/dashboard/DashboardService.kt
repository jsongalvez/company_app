package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
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
        BranchService.findById(branchId)
        // Find-only day resolution (#920): a 403'd attempt must not leave a day row
        // behind. A missing day means no active clock-in can exist for it.
        val branchDay =
            BranchDayService.findToday(branchId)
                ?: throw ForbiddenException("You are not clocked in at this branch")

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
     * #152 single-session detail read for the notifications bearer path (#151 Q1/Q4/Q5) with the
     * #907 lifetime bound: the caller may fetch iff a notification row exists for
     * `(sessionId, caller)` — any read state — AND the caller still holds
     * `VIEW_BRANCH_DATA` at the session's branch (BRANCH) or everywhere (GLOBAL, the #131
     * all-branches window). The bearer is a delivery event, not a durable grant: ending the
     * branch assignment or removing the role revokes the BRANCH leg via the
     * `active_user_capabilities` view, so the read fails closed. 404 for non-bearer, missing
     * sessions, missing days, and revoked readers alike — one UI fallback, UUIDs are not
     * guessable. No day-state gate: the #138 `checkBranchDayReadable` rule governs
     * capability-gated browsing surfaces; notified sessions are COMPLETED with
     * `nextAppointmentDate = today+2`, so their branch day is today-or-past and a
     * PAST/REMITTED `EDIT_PAST_DAY` requirement would 403 the primary case (#151 Q2).
     *
     * @throws NotFoundException if no notification row exists for (sessionId, caller), the
     * session does not exist, its day/branch is missing, or the caller holds no current
     * VIEW at the branch. The missing-session half is defensive: the `session_id` FK keeps
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

        // #907 — read-time re-check (mailbox rows are never deleted): the bearer alone no
        // longer authorizes. Resolve the owning branch find-only and require a current read
        // window; a revoked/transferred/demoted caller keeps the history row but loses the
        // VIEW leg, so this 404s exactly where the old bearer-only gate returned 200.
        val branchId =
            BranchDayService.findById(session.branchDayId)?.branchId
                ?: throw NotFoundException("Session not found")
        if (!hasBranchView(callerId, branchId)) {
            throw NotFoundException("Session not found")
        }

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
            // #382 — the detail read has no route branch context; reuse the resolved branch.
            branchIdBySession = mapOf(session.id to branchId),
        )
    }

    /**
     * #907 — current-read window for the bearer detail: BRANCH `VIEW_BRANCH_DATA` at
     * [branchId] OR GLOBAL `VIEW_BRANCH_DATA` (the #131 all-branches window, e.g.
     * Owner/Accountant). The `active_user_capabilities` view already excludes INACTIVE users
     * and out-of-window grants, so assignment end / role removal revokes without a cleanup
     * path and mailbox history stays intact.
     */
    private fun hasBranchView(
        callerId: UUID,
        branchId: UUID,
    ): Boolean {
        if (CapabilityService.hasCapability(
                callerId,
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityContextType.BRANCH,
                branchId,
            )
        ) {
            return true
        }
        return CapabilityService.hasCapability(
            callerId,
            CapabilityCodes.VIEW_BRANCH_DATA,
            CapabilityContextType.GLOBAL,
            CapabilityService.GLOBAL_CONTEXT_ID,
        )
    }
}
