package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.isStatusCorrection
import com.companyb.companyapp.contracts.session.isStatusTransitionAllowed

// #556 — the dashboard status/reason policy mirrors (#425 + #403), colocated in
// session/dashboard beside DashboardEditState as pure adjacent policy (no rendering imports).

/**
 * #425 — client mirror of backend session status transitions. The current value remains in the
 * list for display, but only legal targets are offered. Correction targets are fail-closed
 * unless caller has Coordinator authority; COMPLETED rows have no status edit affordance.
 */
internal fun statusOptionsFor(
    isWalkIn: Boolean,
    currentStatus: SessionStatus,
    hasCorrectionAuthority: Boolean,
    dayStatus: DayStatus?,
): List<String> =
    SessionStatus.entries
        .filter { target -> statusTargetAllowed(target, isWalkIn, currentStatus, hasCorrectionAuthority, dayStatus) }
        .map { it.name }

private fun statusTargetAllowed(
    target: SessionStatus,
    isWalkIn: Boolean,
    currentStatus: SessionStatus,
    hasCorrectionAuthority: Boolean,
    dayStatus: DayStatus?,
): Boolean =
    when {
        target == currentStatus -> true
        dayStatus == null -> false
        dayStatus != DayStatus.OPEN && !hasCorrectionAuthority -> false
        !isStatusTransitionAllowed(currentStatus, target, isWalkIn) -> false
        isStatusCorrection(currentStatus, target) && !hasCorrectionAuthority -> false
        else -> true
    }

internal fun statusEditAllowed(
    isWalkIn: Boolean,
    currentStatus: SessionStatus,
    hasCorrectionAuthority: Boolean,
    dayStatus: DayStatus?,
): Boolean =
    statusOptionsFor(isWalkIn, currentStatus, hasCorrectionAuthority, dayStatus)
        .any { it != currentStatus.name }

/**
 * #403 — the backend demands a non-blank reason for any write on a REMITTED day
 * (BranchDayService.assertEditableState); unknown (null) day state degrades to
 * optional — the server 400 still guards.
 */
fun remittedReasonRequired(dayStatus: DayStatus?): Boolean = dayStatus == DayStatus.REMITTED

/** Wire form of the reason: trimmed; blank becomes null so non-remitted bodies stay byte-identical. */
internal fun normalizedReason(raw: String): String = raw.trim()
