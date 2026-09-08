package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.isRoutineStatusMark
import com.companyb.companyapp.contracts.session.isStatusCorrection

/**
 * #672 — the Sessions workspace filter: one stable toolbar (All / Pending / Completed)
 * plus a Hide-voided toggle (off initially).
 *
 * Membership reuses the shared status vocabulary (`isRoutineStatusMark` /
 * `isStatusCorrection`): Pending is the actionable PENDING set; Completed is every
 * terminal outcome the lifecycle can mark or correct into — so no terminal row
 * strands outside both filters. Voidedness is an orthogonal axis (a voided COMPLETED
 * row is still COMPLETED) filtered only by the toggle.
 *
 * Pure + composition-free (the #670 contract shape): the screen derives the displayed
 * list from the polled rows; filtering never reorders (backend bookedAt/createdAt
 * order is authoritative) and never touches polling or commission arithmetic.
 */
enum class DashboardFilter {
    ALL,
    PENDING,
    COMPLETED,
}

internal fun DashboardFilter.matches(status: SessionStatus): Boolean =
    when (this) {
        DashboardFilter.ALL -> {
            true
        }

        DashboardFilter.PENDING -> {
            status == SessionStatus.PENDING
        }

        // Every terminal outcome the lifecycle reaches from PENDING — the routine
        // marks plus their corrections (the shared vocabulary, not a local copy).
        DashboardFilter.COMPLETED -> {
            isRoutineStatusMark(SessionStatus.PENDING, status) ||
                isStatusCorrection(SessionStatus.PENDING, status)
        }
    }

/**
 * Applies [filter] + [hideVoided] to [sessions], preserving backend order. The
 * actively edited row is never reordered by this function: callers pass the full
 * polled list through the poll merge first (which pins the edited row), then filter
 * for display — filtering only hides rows, it never moves them. Callers keep the
 * edited row mounted through [ensureEditedRowVisible] so its editor never strands.
 */
internal fun applyDashboardFilter(
    sessions: List<DashboardSessionResponse>,
    filter: DashboardFilter,
    hideVoided: Boolean,
): List<DashboardSessionResponse> =
    sessions.filter { row ->
        filter.matches(row.sessionStatus) && (!hideVoided || !row.isVoided)
    }

/** Which empty state the workspace shows once the poll has landed. */
internal enum class DashboardEmptyKind {
    NO_SESSIONS_FOR_DAY,
    NO_FILTER_MATCHES,
}

internal fun dashboardEmptyKind(daySessionCount: Int): DashboardEmptyKind =
    if (daySessionCount == 0) {
        DashboardEmptyKind.NO_SESSIONS_FOR_DAY
    } else {
        DashboardEmptyKind.NO_FILTER_MATCHES
    }

/**
 * Keeps the actively edited row mounted when the workspace filter would hide it:
 * filtering must never strand the pessimistic edit machine with its commit/discard
 * triggers unmounted (the ADR-0022 "no visible exit" class). The row is reinserted
 * at its backend index; position reconciles once the edit closes and the exemption
 * releases.
 */
internal fun ensureEditedRowVisible(
    full: List<DashboardSessionResponse>,
    filtered: List<DashboardSessionResponse>,
    editSessionId: String?,
): List<DashboardSessionResponse> {
    if (editSessionId == null) return filtered
    if (filtered.any { it.id == editSessionId }) return filtered
    val fullIndex = full.indexOfFirst { it.id == editSessionId }
    if (fullIndex < 0) return filtered
    // Exact backend-relative position: count the visible rows the backend orders
    // before the edited one (an index copy would drift whenever hidden rows sit
    // between visible ones).
    val predecessors =
        filtered.count { candidate ->
            val candidateIndex = full.indexOfFirst { it.id == candidate.id }
            candidateIndex in 0 until fullIndex
        }
    return filtered.toMutableList().apply {
        add(predecessors.coerceIn(0, size), full[fullIndex])
    }
}

/**
 * Restores the workspace filter from the retained section tab (the
 * NavigationContextStore leg): unknown or absent tabs fail closed to ALL, so a
 * newer client or a cleared store never strands the list behind a filter that
 * cannot render.
 */
internal fun restoredDashboardFilter(tab: String?): DashboardFilter =
    runCatching {
        if (tab == null) DashboardFilter.ALL else DashboardFilter.valueOf(tab)
    }.getOrDefault(DashboardFilter.ALL)
