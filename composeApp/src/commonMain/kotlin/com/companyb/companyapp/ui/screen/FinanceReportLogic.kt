package com.companyb.companyapp.ui.screen

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Pure decision helpers for the merged Finance & Reports screen (#101 D1-D8 + #105 D1-D7).
 * Extracted so the state-machine decisions (windows, day state, form validation) are unit-tested
 * without a VM or HTTP layer (the #142/#143 convergence pattern).
 */

/** #105 D4 — feed modes. Every mode is a day feed with a different window + export. */
enum class ReportMode {
    DAILY,
    MONTHLY,
    ALL_TIME,
    DATE_RANGE,
}

/** Inclusive date window for the daily-summaries feed; null bound = unbounded. */
data class FeedWindow(
    val from: String?,
    val to: String?,
)

/** First calendar day of the month (`YearMonth.monthNumber` is internal in 0.7.x). */
internal fun monthFirstDay(m: YearMonth): LocalDate = LocalDate(m.year, m.month.ordinal + 1, 1)

/** Last calendar day of the month (month-arithmetic — kotlinx-datetime 0.7.1 has no lastDayOfMonth). */
internal fun monthLastDay(m: YearMonth): LocalDate =
    monthFirstDay(m)
        .plus(1, DateTimeUnit.MONTH)
        .minus(1, DateTimeUnit.DAY)

/**
 * #105 D4 window derivation. DAILY pins to today ("today first, scroll back" — a future-dated
 * seeded day would otherwise head the feed). MONTHLY bounds the month; ALL_TIME is unbounded
 * unless a calendar-jump month is set (jump = scoped view of that month); DATE_RANGE applies
 * the from/to window.
 */
internal fun feedWindowFor(
    mode: ReportMode,
    today: LocalDate,
    month: YearMonth?,
    rangeFrom: String?,
    rangeTo: String?,
    jumpMonth: YearMonth?,
): FeedWindow =
    when (mode) {
        ReportMode.DAILY -> {
            FeedWindow(from = null, to = today.toString())
        }

        ReportMode.MONTHLY -> {
            val m = month ?: YearMonth(today.year, today.month.ordinal + 1)
            val first = monthFirstDay(m)
            FeedWindow(from = first.toString(), to = monthLastDay(m).toString())
        }

        ReportMode.ALL_TIME -> {
            val jump = jumpMonth
            if (jump == null) {
                FeedWindow(from = null, to = null)
            } else {
                val first = monthFirstDay(jump)
                FeedWindow(from = first.toString(), to = monthLastDay(jump).toString())
            }
        }

        ReportMode.DATE_RANGE -> {
            FeedWindow(from = rangeFrom, to = rangeTo)
        }
    }

/**
 * #101 D3 — day-state banner. The lazy rule is date-based (an OPEN day whose calendar date
 * precedes today is PAST — backend/AGENTS.md); only the REMITTED flag is stored state, which
 * the feed payload does not carry. The backend remains authoritative for REMITTED writes
 * (400 reason-required) — the banner's job is the past-day warning.
 */
internal enum class DerivedDayState {
    OPEN,
    PAST,
}

internal fun derivedDayState(
    date: LocalDate,
    today: LocalDate,
): DerivedDayState = if (date >= today) DerivedDayState.OPEN else DerivedDayState.PAST

internal fun dayStateBannerText(state: DerivedDayState): String =
    when (state) {
        DerivedDayState.OPEN -> "Today's data — edits apply immediately"
        DerivedDayState.PAST -> "Warning: past day — writes require the EDIT_PAST_DAY capability"
    }

/** #123 date-input precedent — `yyyy-MM` month parse. */
internal fun parseYearMonthInput(raw: String): YearMonth? =
    runCatching {
        val parts = raw.trim().split('-')
        require(parts.size == 2)
        YearMonth(parts[0].toInt(), parts[1].toInt())
    }.getOrNull()

/** `yyyy-MM-dd` parse (ISO). */
internal fun parseDateInput(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.trim()) }.getOrNull()

/** #101 D4 — compensation amount: non-negative (zero permitted, BR:302). */
internal fun compensationAmountError(raw: String): String? {
    if (raw.isBlank()) return "Amount is required"
    val cents = moneyToCents(raw)
    if (cents < 0) return "Amount must be non-negative"
    if (raw.any { it.isLetter() }) return "Enter a valid amount"
    return null
}

/** #101 D6 — expense amount: strictly positive. */
internal fun expenseAmountError(raw: String): String? {
    if (raw.isBlank()) return "Amount is required"
    if (raw.any { it.isLetter() }) return "Enter a valid amount"
    val cents = moneyToCents(raw)
    if (cents <= 0) return "Amount must be positive"
    return null
}

internal val expenseCategoryLabels: List<String> =
    listOf(
        "Pantry",
        "Communication",
        "Water",
        "Transportation",
        "Electricity",
        "Rental",
        "Office Supplies",
        "Furniture/Fixtures",
        "Miscellaneous",
    )

internal val expenseCategoryCodes: List<String> =
    listOf(
        "PANTRY",
        "COMMUNICATION",
        "WATER",
        "TRANSPORTATION",
        "ELECTRICITY",
        "RENTAL",
        "OFFICE_SUPPLIES",
        "FURNITURE_FIXTURES",
        "MISCELLANEOUS",
    )
