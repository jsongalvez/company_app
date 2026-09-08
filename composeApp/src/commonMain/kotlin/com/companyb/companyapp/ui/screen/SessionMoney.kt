package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.contracts.remittance.RemittanceLineResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.util.logWarn
import kotlin.math.abs

private const val CENTS_PER_UNIT = 100L
private const val FRAC_DIGITS = 2
private const val ROUND_THRESHOLD_DIGIT = 5
private val MONEY_PATTERN = Regex("^[+-]?\\d+(\\.\\d+)?$")

/**
 * Fixed-point money helpers (commonMain has no BigDecimal): backend money strings are
 * non-negative decimal strings (parseNonNegativeBigDecimal server-side, HALF_UP to scale 2),
 * commission at scale 4 ("200.0000"), prices at scale 2. Cents arithmetic keeps the gross
 * sum exact; scale-4 inputs round half-up at the second decimal (#654 — was truncate).
 *
 * Strict fail-closed parse (#654): trims, accepts an optional leading sign plus
 * `digits[.digits]` (single dot, digits on both sides when the dot is present).
 * Returns null on any unparseable input (empty, letters, multi-dot, missing whole/frac
 * digits like ".50"/"5.", whitespace-only) instead of the previous silent 0L — callers
 * surface the null as a validation error or skip-and-warn in sums. Backend amounts stay
 * authoritative; the sign is applied to the total so "-0.xx" keeps its sign.
 */
internal fun moneyToCents(raw: String): Long? =
    runCatching {
        val trimmed = raw.trim()
        require(MONEY_PATTERN.matches(trimmed))
        var body = trimmed
        var negative = false
        if (body[0] == '-' || body[0] == '+') {
            negative = body[0] == '-'
            body = body.substring(1)
        }
        val dot = body.indexOf('.')
        val whole = body.substring(0, if (dot < 0) body.length else dot).toLong()
        // #654 — cents total is (whole+carry)*100+frac: reserve headroom for carry+99.
        require(whole <= (Long.MAX_VALUE - CENTS_PER_UNIT) / CENTS_PER_UNIT - 1)
        val fracPart = if (dot < 0) null else body.substring(dot + 1)
        val (fracCents, carryWhole) = roundFracToCents(fracPart)
        val total = (whole + carryWhole) * CENTS_PER_UNIT + fracCents
        if (negative) -total else total
    }.getOrNull()

/** #654 — half-up frac rounding (third digit >= 5 carries); null frac means no dot. */
private fun roundFracToCents(frac: String?): Pair<Long, Long> {
    if (frac == null) return 0L to 0L
    if (frac.length <= FRAC_DIGITS) return frac.padEnd(FRAC_DIGITS, '0').toLong() to 0L
    val thirdUp = frac[FRAC_DIGITS].digitToInt() >= ROUND_THRESHOLD_DIGIT
    val rounded = frac.take(FRAC_DIGITS).toLong() + if (thirdUp) 1 else 0
    return if (rounded >= CENTS_PER_UNIT) 0L to 1L else rounded to 0L
}

internal fun centsToMoney(cents: Long): String {
    val sign = if (cents < 0) "-" else ""
    val absValue = abs(cents)
    return "$sign${absValue / CENTS_PER_UNIT}.${(absValue % CENTS_PER_UNIT).toString().padStart(2, '0')}"
}

/**
 * #97 Q2 — gross income excludes voided rows AND non-completed sessions at the state layer
 * ("Today · completed, non-voided"); rendering only shows voided-ness.
 * #654 — unparseable prices are skipped with a warn (fail-closed: never silently 0).
 */
internal fun grossIncomeCents(sessions: List<DashboardSessionResponse>): Long =
    sessions
        .filter { it.sessionStatus == SessionStatus.COMPLETED && !it.isVoided }
        .sumOf { session ->
            moneyToCents(session.finalPrice) ?: run {
                logWarn("SessionMoney", "grossIncomeCents skipping unparseable finalPrice=${session.finalPrice}")
                0L
            }
        }

/**
 * #447 — SESSION submit gross previews from the loaded lines: the server freezes
 * SESSION-type lines only (`sumGrossIncomeInTransaction`), while the line total covers
 * every line — a mixed draft's gross-to-freeze differs from its total.
 * #654 — unparseable line amounts are skipped with a warn (fail-closed).
 */
internal fun sessionLinesGrossCents(lines: List<RemittanceLineResponse>): Long =
    lines
        .filter { it.type == RemittanceLineType.SESSION }
        .sumOf { line ->
            moneyToCents(line.amount) ?: run {
                logWarn("SessionMoney", "sessionLinesGrossCents skipping unparseable amount=${line.amount}")
                0L
            }
        }

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
