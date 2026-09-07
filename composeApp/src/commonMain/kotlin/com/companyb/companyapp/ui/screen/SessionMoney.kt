package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.dto.DashboardSessionResponse
import com.companyb.companyapp.dto.RemittanceLineResponse
import kotlin.math.abs

private const val CENTS_PER_UNIT = 100L

/**
 * Fixed-point money helpers (commonMain has no BigDecimal): backend money strings are
 * non-negative decimal strings (parseNonNegativeBigDecimal server-side), commission at scale 4
 * ("200.0000"), prices at scale 2. Cents arithmetic keeps the gross sum exact; scale-4 inputs
 * truncate at the second decimal (display-only — the backend amount is authoritative).
 */
internal fun moneyToCents(raw: String): Long {
    val parts = raw.split('.')
    val whole = parts.firstOrNull()?.toLongOrNull() ?: return 0L
    val frac =
        parts
            .getOrNull(1)
            ?.take(2)
            ?.padEnd(2, '0')
            ?.toLongOrNull() ?: 0L
    return whole * CENTS_PER_UNIT + frac
}

internal fun centsToMoney(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val absValue = abs(cents)
    return "$sign${absValue / CENTS_PER_UNIT}.${(absValue % CENTS_PER_UNIT).toString().padStart(2, '0')}"
}

/**
 * #97 Q2 — gross income excludes voided rows AND non-completed sessions at the state layer
 * ("Today · completed, non-voided"); rendering only shows voided-ness.
 */
internal fun grossIncomeCents(sessions: List<DashboardSessionResponse>): Long =
    sessions
        .filter { it.sessionStatus == SessionStatus.COMPLETED && !it.isVoided }
        .sumOf { moneyToCents(it.finalPrice) }

/**
 * #447 — SESSION submit gross previews from the loaded lines: the server freezes
 * SESSION-type lines only (`sumGrossIncomeInTransaction`), while the line total covers
 * every line — a mixed draft's gross-to-freeze differs from its total.
 */
internal fun sessionLinesGrossCents(lines: List<RemittanceLineResponse>): Long =
    lines
        .filter { it.type == RemittanceLineType.SESSION }
        .sumOf { moneyToCents(it.amount) }

internal fun commissionLabel(productSalesCount: Int): String =
    "from $productSalesCount product sale${if (productSalesCount == 1) "" else "s"}"

// #561 — shared peso formatter (finance + remittance external consumers per #559):
// lives in ui/screen as the deliberate shared money-display edge; remittance-specific
// labels stay in remittance/RemittanceUi.
internal fun peso(raw: String): String = "₱$raw"

/**
 * #405 — a MEDICAL_MISSION visit is always free (BR §Session types): the price affordance is
 * locked client-side wherever this type shows, and the server normalizes any non-zero value
 * authoritatively.
 */
internal fun missionPriceLocked(sessionType: SessionType): Boolean = sessionType == SessionType.MEDICAL_MISSION
