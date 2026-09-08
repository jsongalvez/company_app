package com.companyb.companyapp.session.detail

import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.session.dashboard.statusOptionsFor

/**
 * #675 — the shared session-detail action model: one pure policy supplies the desktop
 * inline pane and the compact pushed detail, both driven by current server data and the
 * existing DashboardStatusPolicy/capability predicates. The desktop status cell consumes
 * the same predicates directly (its row editor needs the string form); this model is the
 * detail hosts' share of that source, not a second business policy.
 *
 * - PENDING: "Add myself" is primary when the caller holds edit authority, the day is
 *   known, and the caller is not yet on the roster. Once joined, "Mark completed" is
 *   primary only when the current policy permits it. An authorized caller who never joins
 *   still reaches completion (and every other legal transition) through the Session
 *   actions menu — no forced join.
 * - COMPLETED: the same action region shows the completion state instead of swapping the
 *   just-clicked primary for a destructive action at that pointer location. Void/unvoid
 *   live in the menu (capability-gated at the call site), never in the primary slot.
 * - Unknown day state fails closed (no options, no primary) exactly like the dashboard
 *   cell; a null roster (still loading) offers no primary yet — the menu stays the path.
 */
internal enum class SessionDetailPrimary { ADD_SELF, COMPLETE }

internal data class SessionDetailActionModel(
    val primary: SessionDetailPrimary?,
    val showCompletedState: Boolean,
    val statusMenuOptions: List<SessionStatus>,
)

internal fun sessionDetailActionModel(
    session: DashboardSessionResponse,
    rosterIds: Set<String>?,
    currentUserId: String?,
    canEdit: Boolean,
    canCorrectStatus: Boolean,
    dayStatus: DayStatus?,
): SessionDetailActionModel {
    val options = detailStatusOptions(session, canEdit, canCorrectStatus, dayStatus)
    val primary = detailPrimary(session, rosterIds, currentUserId, canEdit, options, dayStatus)
    return SessionDetailActionModel(
        primary = primary,
        // Editors of a completed session keep a quiet state line in the action region;
        // read-only callers already have the status badge, so their rendering is untouched.
        showCompletedState = session.sessionStatus == SessionStatus.COMPLETED && primary == null && canEdit,
        // The primary is a shortcut: the menu carries the other legal targets, plus
        // completion itself while joining is still the primary (no forced join).
        statusMenuOptions =
            if (primary ==
                SessionDetailPrimary.COMPLETE
            ) {
                options - SessionStatus.COMPLETED
            } else {
                options
            },
    )
}

/** Legal non-current targets under the shared status policy; empty without edit authority. */
private fun detailStatusOptions(
    session: DashboardSessionResponse,
    canEdit: Boolean,
    canCorrectStatus: Boolean,
    dayStatus: DayStatus?,
): List<SessionStatus> {
    if (!canEdit) return emptyList()
    return statusOptionsFor(
        isWalkIn = session.isWalkIn,
        currentStatus = session.sessionStatus,
        hasCorrectionAuthority = canCorrectStatus,
        dayStatus = dayStatus,
    ).mapNotNull {
        try {
            SessionStatus.valueOf(it)
        } catch (_: IllegalArgumentException) {
            null
        }
    }.filter { it != session.sessionStatus }
}

/**
 * The primary shortcut, or null while the roster or day is unknown, or no shortcut
 * applies. Joining without day knowledge would offer against a PAST/REMITTED day the
 * server would 403 — the shortcut waits for the day read like the menu does.
 */
private fun detailPrimary(
    session: DashboardSessionResponse,
    rosterIds: Set<String>?,
    currentUserId: String?,
    canEdit: Boolean,
    options: List<SessionStatus>,
    dayStatus: DayStatus?,
): SessionDetailPrimary? {
    if (!canEdit || session.sessionStatus != SessionStatus.PENDING || currentUserId == null) return null
    // Unknown roster (still loading) or unknown day offers no shortcut — the menu already
    // carries every legal target, and a provisional Add-myself would flash wrong for
    // joined members or against a day the server would reject.
    if (rosterIds == null || dayStatus == null) return null
    if (currentUserId !in rosterIds) return SessionDetailPrimary.ADD_SELF
    return if (SessionStatus.COMPLETED in options) SessionDetailPrimary.COMPLETE else null
}

/** Textual menu names for the Session actions list (status codes never render raw). */
internal fun SessionStatus.detailActionLabel(): String =
    when (this) {
        SessionStatus.COMPLETED -> "Mark completed"
        SessionStatus.NO_SHOW -> "Mark no-show"
        SessionStatus.CANCELLED -> "Cancel session"
        SessionStatus.PENDING -> "Reopen as pending"
    }

/**
 * #675 — the detail header adopts the #673 missing-value rule for its plain-name string:
 * a blank name is unknown, never an empty line.
 */
internal const val DETAIL_UNKNOWN_CLIENT = "Unknown client"

internal fun detailClientName(clientName: String?): String =
    clientName?.takeIf { it.isNotBlank() } ?: DETAIL_UNKNOWN_CLIENT
