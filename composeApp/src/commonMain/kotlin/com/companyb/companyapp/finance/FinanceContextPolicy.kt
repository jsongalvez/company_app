package com.companyb.companyapp.finance

import com.companyb.companyapp.contracts.reporting.DailySalesSummaryResponse
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * #728 - pure Finance working-context rules: identity anchor resolution, applied
 * branch/day restore validation, and picker millis conversions.
 *
 * Composition-free so the decisions stay unit-testable without a Compose runtime
 * (the FinanceLayoutPolicy #678 contract shape). The session-scoped owner is
 * NavigationContextStore (memory-only, cleared with the session); this file owns
 * the pure decisions, screens own the retain/restore wiring.
 *
 * Staged applied scope passed from the screen to the ViewModel before the first load.
 */
data class FinanceRestoreRequest(
    val branchId: String?,
    val modeName: String?,
    val month: String?,
    val rangeFrom: String?,
    val rangeTo: String?,
    val jumpMonth: String?,
)

/**
 * Identity anchor resolution for Finance section roundtrips: the retained list
 * position is the visible day's branchDayId (not a raw index), resolved against
 * the current feed. A missing anchor falls back to the list start, never a blank
 * or impossible scroll state. Null/blank anchors (cold first visit, cleared
 * scope) also start at 0.
 */
fun financeAnchorIndex(
    days: List<DailySalesSummaryResponse>,
    anchorId: String?,
): Int {
    if (anchorId.isNullOrBlank()) return 0
    val index = days.indexOfFirst { it.branchDayId == anchorId }
    return if (index >= 0) index else 0
}

/**
 * Restores the applied branch: the stored branch wins when it is still in the
 * accessible list; otherwise falls back through the existing safe default rule
 * (clocked-in branch when authorized, otherwise first accessible). Null when no
 * branch is accessible (fail-closed, mirrors loadBranches).
 */
fun resolveRestoredFinanceBranch(
    storedBranchId: String?,
    accessibleIds: List<String>,
    clockedBranchId: String?,
): String? {
    if (storedBranchId != null && accessibleIds.contains(storedBranchId)) return storedBranchId
    if (clockedBranchId != null && accessibleIds.contains(clockedBranchId)) return clockedBranchId
    return accessibleIds.firstOrNull()
}

/**
 * Restores the selected day only when it still belongs to the current
 * authoritative feed. A missing day (scope changed, day aged out) falls back to
 * the report list (null) without stale detail. Blank stored ids (explicitly
 * cleared selection) also restore to null.
 */
fun resolveRestoredFinanceDay(
    storedDayId: String?,
    feed: List<DailySalesSummaryResponse>,
): DailySalesSummaryResponse? {
    if (storedDayId.isNullOrBlank()) return null
    return feed.firstOrNull { it.branchDayId == storedDayId }
}

/** Parses a stored ReportMode name; null/unknown falls back to null (caller keeps default). */
fun parseStoredFinanceMode(raw: String?): ReportMode? =
    when (raw) {
        ReportMode.DAILY.name -> ReportMode.DAILY
        ReportMode.MONTHLY.name -> ReportMode.MONTHLY
        ReportMode.ALL_TIME.name -> ReportMode.ALL_TIME
        ReportMode.DATE_RANGE.name -> ReportMode.DATE_RANGE
        else -> null
    }

/** Stored applied month (yyyy-MM); blank/invalid degrades to null (caller keeps default). */
fun parseStoredFinanceMonth(raw: String?): YearMonth? {
    if (raw.isNullOrBlank()) return null
    return parseYearMonthInput(raw)
}

/**
 * Stored ALL_TIME jump month (yyyy-MM); blank degrades to null (unbounded
 * all-time). Invalid degrades to null rather than a guessed month.
 */
fun parseStoredFinanceJumpMonth(raw: String?): YearMonth? {
    if (raw.isNullOrBlank()) return null
    return parseYearMonthInput(raw)
}

/**
 * Stored DATE_RANGE window; either side blank/invalid degrades the whole window
 * to null (the hint state) rather than a half-applied range. Ordering is
 * validated by the existing Apply rules; a reversed stored window (never written
 * by Apply) also degrades to null.
 */
fun parseStoredFinanceRange(
    fromRaw: String?,
    toRaw: String?,
): Pair<String, String>? {
    if (fromRaw.isNullOrBlank() || toRaw.isNullOrBlank()) return null
    val from = parseDateInput(fromRaw) ?: return null
    val to = parseDateInput(toRaw) ?: return null
    if (from > to) return null
    return from.toString() to to.toString()
}

// The Material3 pickers speak epoch millis while Finance speaks yyyy-MM-dd /
// yyyy-MM calendar strings (the relief/audit precedent): seed and read back in
// UTC so confirming an unchanged value can never shift it a day.

/** Seeds a date picker from yyyy-MM-dd; garbage seeds null (picker opens unselected). */
@OptIn(ExperimentalTime::class)
fun financeDateToPickerMillis(value: String): Long? {
    val date = parseDateInput(value) ?: return null
    return date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
}

/** Reads a date picker back to yyyy-MM-dd; null stays null (dismissal changes nothing). */
@OptIn(ExperimentalTime::class)
fun financePickerMillisToDate(millis: Long?): String? {
    if (millis == null) return null
    return Instant
        .fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.UTC)
        .date
        .toString()
}

/** Seeds a month picker from yyyy-MM (first of the month); garbage seeds null. */
@OptIn(ExperimentalTime::class)
fun financeMonthToPickerMillis(value: String): Long? {
    val month = parseYearMonthInput(value) ?: return null
    val first = LocalDate(month.year, month.month.ordinal + 1, 1)
    return first.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
}

/**
 * Reads a month picker back to yyyy-MM (the picked day's month - picking any day
 * in the desired month selects that month). Null stays null.
 */
@OptIn(ExperimentalTime::class)
fun financePickerMillisToMonth(millis: Long?): String? {
    if (millis == null) return null
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
    return YearMonth(date.year, date.month.ordinal + 1).toString()
}
