package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.isStatusCorrection
import com.companyb.companyapp.domain.isStatusTransitionAllowed
import com.companyb.companyapp.dto.DashboardSessionResponse

/**
 * #149 — the session dashboard's per-field edit state machine (#97 Q4 + ADR-0022:
 * pessimistic inline editing, the #142-class reservoir). Pure transitions, extracted as
 * tested functions — the #142 lesson (the per-field machine converged only after the
 * decision points became pure tested functions).
 *
 * Model-A failure semantics: on any failure the attempted draft stays in the input with
 * an inline error and the editor remains open (retry or discard). 403 terminates the
 * edit silently (the VM clears the state + drops the affordance). 409 keeps the draft
 * with a conflict error + Reload action; after reload the baseline version re-baselines
 * to the fresh row and the cell is marked when the committed value differs from the
 * attempted draft ("indicate changed fields").
 */
enum class DashboardEditField {
    STATUS,
    FINAL_PRICE,
}

data class DashboardEditState(
    val sessionId: String,
    val field: DashboardEditField,
    val draft: String,
    val baselineValue: String = draft,
    val baselineVersion: Int,
    val inFlight: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
    val fieldChangedRemotely: Boolean = false,
    // #403 — the audit reason the backend demands on any write to a REMITTED day.
    val reason: String = "",
)

/** The displayed (committed) value of [field] on [row]. */
fun DashboardSessionResponse.fieldValue(field: DashboardEditField): String =
    when (field) {
        DashboardEditField.STATUS -> sessionStatus.name
        DashboardEditField.FINAL_PRICE -> finalPrice
    }

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
 * Semantic price equality: "2750" == "2750.00" == "2750.0" (the backend normalizes to
 * plain strings of varying scale; the draft is raw input).
 */
internal fun pricesEqual(
    a: String,
    b: String,
): Boolean = normalizePrice(a) == normalizePrice(b)

internal fun normalizePrice(raw: String): String {
    var value = raw.trim()
    if ('.' in value) {
        value = value.trimEnd('0').trimEnd('.')
    }
    return value
}

/**
 * Client mirror of the backend's price validation (parseNonNegativeBigDecimal): blank /
 * negative / non-numeric drafts are rejected before dispatch (the #135 parseSlotInput
 * mirror pattern — a rejected draft gets the inline error instead of a wire 400).
 * Values with >2 decimals pass (the backend rounds them server-side, authoritative).
 */
internal fun finalPriceInputValid(raw: String): Boolean = raw.trim().matches(Regex("""\d+(\.\d+)?"""))

internal fun fieldValuesEqual(
    a: String,
    b: String,
    field: DashboardEditField,
): Boolean = if (field == DashboardEditField.FINAL_PRICE) pricesEqual(a, b) else a == b

/** A commit whose draft equals the displayed value is a no-op exit (no request). */
fun draftChanged(
    state: DashboardEditState,
    row: DashboardSessionResponse,
): Boolean {
    val baseline =
        if (row.version == state.baselineVersion) {
            row.fieldValue(state.field)
        } else {
            state.baselineValue
        }
    return !fieldValuesEqual(state.draft, baseline, state.field)
}

fun beginEdit(
    row: DashboardSessionResponse,
    field: DashboardEditField,
): DashboardEditState =
    DashboardEditState(
        sessionId = row.id,
        field = field,
        // The editor opens showing the current committed value; the draft owns the cell
        // from here (Q4: mid-edit polling must not overwrite the edited field).
        draft = row.fieldValue(field),
        baselineValue = row.fieldValue(field),
        baselineVersion = row.version,
    )

/**
 * Typing clears the inline error (the #142 "typing clears errors" behavior) — EXCEPT while
 * a 409 conflict is unresolved: the conflict error + Reload action must stay visible (a
 * conflict-state commit is blocked, so clearing the error would silently park the machine
 * with no visible exit — the pass-2 finding).
 */
fun DashboardEditState.withDraft(value: String): DashboardEditState =
    copy(draft = value, error = if (conflict) error else null)

/** The PATCH is dispatched: the cell dims + spinners; the draft stays (pessimistic). */
fun DashboardEditState.asInFlight(): DashboardEditState =
    copy(inFlight = true, error = null, conflict = false, fieldChangedRemotely = false)

/**
 * #403 — the backend demands a non-blank reason for any write on a REMITTED day
 * (BranchDayService.assertEditableState); unknown (null) day state degrades to
 * optional — the server 400 still guards.
 */
fun remittedReasonRequired(dayStatus: DayStatus?): Boolean = dayStatus == DayStatus.REMITTED

/** Wire form of the reason: trimmed; blank becomes null so non-remitted bodies stay byte-identical. */
internal fun normalizedReason(raw: String): String = raw.trim()

/** Typing a reason clears the inline error under the same rule as [withDraft]. */
fun DashboardEditState.withReason(value: String): DashboardEditState =
    copy(reason = value, error = if (conflict) error else null)

/** ADR-0022 conflict path: keep the draft, surface the conflict inline with Reload. */
fun DashboardEditState.asConflict(message: String): DashboardEditState =
    copy(inFlight = false, error = message, conflict = true)

/** Model A: keep the draft + inline error + stay in edit mode. */
fun DashboardEditState.asFailed(message: String): DashboardEditState = copy(inFlight = false, error = message)

/**
 * The 409-reload landed: re-baseline the version so a retry commits against the current
 * row, and mark the edited cell when the committed value now differs from the attempted
 * draft ("indicate changed fields").
 */
fun DashboardEditState.afterReload(row: DashboardSessionResponse): DashboardEditState =
    copy(
        baselineVersion = row.version,
        baselineValue = row.fieldValue(field),
        error = null,
        conflict = false,
        fieldChangedRemotely = !fieldValuesEqual(row.fieldValue(field), draft, field),
    )

/**
 * #149 — monotonic version merge for the poll: an in-flight poll response that started
 * before a successful commit must never regress the committed row (it carries an older
 * version). Membership is backend-authoritative (incoming list wins for absent rows);
 * per-row version wins for present rows.
 */
fun mergeDashboardRows(
    existing: List<DashboardSessionResponse>?,
    incoming: List<DashboardSessionResponse>,
): List<DashboardSessionResponse> {
    val previousById = existing?.associateBy { it.id } ?: emptyMap()
    return incoming.map { row ->
        val previous = previousById[row.id]
        if (previous != null && previous.version > row.version) previous else row
    }
}
