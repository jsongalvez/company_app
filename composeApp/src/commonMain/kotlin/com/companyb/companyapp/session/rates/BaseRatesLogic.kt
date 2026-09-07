package com.companyb.companyapp.session.rates

import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.session.RateResponse
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.ui.screen.missionPriceLocked

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
