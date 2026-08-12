package com.companyb.companyapp.ui.screen

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
    TYPE,
    STATUS,
    FINAL_PRICE,
}

data class DashboardEditState(
    val sessionId: String,
    val field: DashboardEditField,
    val draft: String,
    val baselineVersion: Int,
    val inFlight: Boolean = false,
    val error: String? = null,
    val conflict: Boolean = false,
    val fieldChangedRemotely: Boolean = false,
)

/** The displayed (committed) value of [field] on [row]. */
fun DashboardSessionResponse.fieldValue(field: DashboardEditField): String =
    when (field) {
        DashboardEditField.TYPE -> sessionType
        DashboardEditField.STATUS -> sessionStatus
        DashboardEditField.FINAL_PRICE -> finalPrice
    }

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
): Boolean = !fieldValuesEqual(state.draft, row.fieldValue(state.field), state.field)

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
        baselineVersion = row.version,
    )

/** Typing clears the inline error (the #142 "typing clears errors" behavior). */
fun DashboardEditState.withDraft(value: String): DashboardEditState = copy(draft = value, error = null)

/** The PATCH is dispatched: the cell dims + spinners; the draft stays (pessimistic). */
fun DashboardEditState.asInFlight(): DashboardEditState =
    copy(inFlight = true, error = null, conflict = false, fieldChangedRemotely = false)

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
