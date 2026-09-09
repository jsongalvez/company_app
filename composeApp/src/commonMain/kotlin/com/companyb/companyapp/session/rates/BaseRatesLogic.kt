package com.companyb.companyapp.session.rates

import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.session.RateResponse
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.ui.screen.missionPriceLocked
import com.companyb.companyapp.ui.screen.peso

/**
 * #418 — the base-rate admin screen's pure decision surface, desktopTest-pinned like the
 * #392 inventory predicates. Mirrors the backend's exact gate (`SessionBaseRateRoutes`
 * `requireBranchCapabilityForBranchId`): MANAGE_PRODUCTS at BRANCH context for the specific
 * branch — no GLOBAL leg, no day-state leg (#131 strictness). These must never widen it.
 *
 * #573 — moved from `ui/screen` to the `session/rates` owner with the rates screen;
 * [missionPriceLocked] stays shared in `ui/screen/SessionMoney.kt` (session create
 * consumes the same lock).
 */
internal fun canManageRates(
    capabilities: List<UserCapabilityResponse>,
    selectedBranchId: String?,
): Boolean = capabilities.hasCapability(CapabilityCodes.MANAGE_PRODUCTS, CapabilityContextType.BRANCH, selectedBranchId)

/** One canonical rate row in fixed display order with the BR-documented default as hint. */
internal data class BaseRateRowSpec(
    val sessionType: SessionType,
    val label: String,
    val defaultLabel: String,
)

/** docs/business-requirements.md "Base Rates" — the five canonical types in display order. */
internal val BASE_RATE_ROWSPECS: List<BaseRateRowSpec> =
    listOf(
        BaseRateRowSpec(SessionType.REGULAR, "Regular", "₱2,500"),
        BaseRateRowSpec(SessionType.SECOND_SESSION, "2nd Session", "₱2,000"),
        BaseRateRowSpec(SessionType.SUBSEQUENT, "Subsequent", "₱1,500"),
        BaseRateRowSpec(SessionType.PROVINCIAL_FIRST, "Provincial (first session)", "₱3,500"),
        BaseRateRowSpec(SessionType.MEDICAL_MISSION, "Medical Mission", "₱0 (always)"),
    )

/**
 * Merges the branch's active rates into the canonical five-row display order. A type with no
 * open rate row renders `rateText = null` (the field pre-fills empty with the default as
 * supporting text — saving provisions the row). The mission row is always locked at ₱0.
 */
internal data class RateDisplayRow(
    val spec: BaseRateRowSpec,
    val rateId: String?,
    val rateText: String?,
    val missionLocked: Boolean,
)

internal fun toRateDisplayRows(rates: List<RateResponse>): List<RateDisplayRow> {
    val byType = rates.associateBy { it.sessionType }
    return BASE_RATE_ROWSPECS.map { spec ->
        val existing = byType[spec.sessionType]
        RateDisplayRow(
            spec = spec,
            rateId = existing?.id,
            rateText = existing?.rate,
            missionLocked = missionPriceLocked(spec.sessionType),
        )
    }
}

/**
 * Client-side mirror of the route's `parseNonNegativeBigDecimal(request.rate, "rate")` 400s:
 * a non-parseable amount and a negative amount are rejected inline; the backend stays
 * authoritative (the SessionCreate price-field shape — `toDoubleOrNull`, non-negative).
 */
internal fun rateInputError(raw: String): String? {
    if (raw.isBlank()) return "Enter a rate amount"
    val value = raw.toDoubleOrNull() ?: return "Invalid rate amount: $raw"
    if (!value.isFinite()) return "Invalid rate amount: $raw"
    return if (value < 0) "rate must be non-negative" else null
}

/**
 * #685 — the editor's live validation: the submitted value is the trimmed draft (the screen
 * POSTs `draft.trim()`), so a whitespace-padded paste validates instead of falsely
 * rejecting; the pristine empty field stays quiet until the first keystroke.
 */
internal fun rateDraftError(draft: String): String? = rateInputError(draft.trim()).takeIf { draft.isNotEmpty() }

/** Display text for the mission row's locked zero (#685). */
private const val MISSION_LOCKED_VALUE = "0"

/**
 * #685 — rest-state value line: the authoritative currency value when the branch holds an
 * open row, the documented default when it holds none, the mission row locked at ₱0.
 * Replaces the old per-row "Default … — not yet customized" prose: an unset row reads the
 * default once as its value instead of repeating the same qualifier on every row.
 */
internal fun rateValueLabel(row: RateDisplayRow): String =
    when {
        row.missionLocked -> peso(MISSION_LOCKED_VALUE)
        row.rateText != null -> peso(row.rateText)
        else -> "Default ${row.spec.defaultLabel}"
    }

/**
 * #685 — secondary context under an open editor: the current authoritative value, or the
 * documented default when nothing is customized yet. Null for the mission row, which never
 * opens an editor.
 */
internal fun rateEditingContextLabel(row: RateDisplayRow): String? =
    when {
        row.missionLocked -> null
        row.rateText != null -> "Current ${peso(row.rateText)}"
        else -> "Default ${row.spec.defaultLabel}"
    }

/**
 * #685 — a reload that lands while a row edits must never overwrite the typed draft
 * silently. A null baseline means the editor has not adopted an authoritative value yet
 * (caller adopts without notice); any other difference is an external change the editor
 * must surface before another save.
 */
internal fun rateChangedWhileEditing(
    baseline: String?,
    current: String?,
): Boolean = baseline != null && baseline != current

/**
 * #685 — retry reuses the edit's original idempotency identifier; a timeout never mints a
 * fresh rate id just to resolve uncertainty (the backend would rotate a second row). The
 * minter runs only when the edit has no identifier yet.
 */
internal fun resolveSaveRequestId(
    pendingId: String?,
    mint: () -> String,
): String = pendingId ?: mint()
