package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.dto.DashboardSessionResponse
import kotlin.math.abs

private const val SESSION_STATUS_COMPLETED = "COMPLETED"

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
    return whole * 100 + frac
}

internal fun centsToMoney(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val absValue = abs(cents)
    return "$sign${absValue / 100}.${(absValue % 100).toString().padStart(2, '0')}"
}

/**
 * #97 Q2 — gross income excludes voided rows AND non-completed sessions at the state layer
 * ("Today · completed, non-voided"); rendering only shows voided-ness.
 */
internal fun grossIncomeCents(sessions: List<DashboardSessionResponse>): Long =
    sessions
        .filter { it.sessionStatus.name == SESSION_STATUS_COMPLETED && !it.isVoided }
        .sumOf { moneyToCents(it.finalPrice) }

internal fun commissionLabel(productSalesCount: Int): String =
    "from $productSalesCount product sale${if (productSalesCount == 1) "" else "s"}"
