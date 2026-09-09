package com.companyb.companyapp.workforce.relief

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Shared relief-planning date rules (#680): one owner for the invite-date default and
 * eligibility used by the Sessions Team Relief tab and the BranchSelect invite shortcut.
 *
 * - The picker defaults to the viewed date when that date is an eligible future day;
 *   otherwise tomorrow in Asia/Manila (future-day planning is the use case; the live
 *   dashboard views today, so it defaults to tomorrow).
 * - Typed dates stay available with validation; only today-or-later sends.
 */
fun defaultPlanningDate(
    viewedDate: String?,
    today: LocalDate,
): String {
    val viewed = parseInviteDate(viewedDate)
    return if (viewed != null && viewed > today) viewed.toString() else tomorrow(today).toString()
}

/** Today-or-later (the send gate); past days render Expired and never send. */
fun isPlanningDateEligible(
    dateText: String?,
    today: LocalDate,
): Boolean {
    val date = parseInviteDate(dateText) ?: return false
    return date >= today
}

/** The actually chosen date, always visible next to the picker (#680). */
fun planningDateLabel(dateText: String): String = "Invite date: $dateText"

/**
 * #729 — scope-explicit section labels. Each heading carries its own date authority so
 * the invite form date can never read as a master filter:
 * - Requests names the active operational branch-day (not the invite form date);
 * - Invite names the selected invitation date (candidate search only);
 * - History names the branch and establishes the all-dates scope (never filtered by
 *   the invite form date).
 * Pure so presentation tests pin the scope separation without a Compose harness.
 */
fun reliefRequestsHeading(activeDate: String?): String =
    if (activeDate.isNullOrBlank()) "Requests" else "Requests — $activeDate"

fun reliefInviteHeading(selectedDate: String): String =
    if (selectedDate.isBlank()) "Invite staff" else "Invite staff — $selectedDate"

fun reliefHistoryHeading(branchName: String?): String {
    val branch = branchName?.ifBlank { null } ?: "Branch"
    return "Invitation history — all dates for $branch"
}

fun reliefPlanningBranchLabel(branchName: String?): String = branchName?.ifBlank { null } ?: "Branch"

/** #729 — the exact-day deep-link destination names its read-only authority. */
const val RELIEF_DAY_READ_ONLY_TITLE = "Read-only relief activity"

private fun tomorrow(today: LocalDate): LocalDate = today.plus(1, DateTimeUnit.DAY)

/**
 * #680 — clear the selected candidate only when the parsed date actually moves to a
 * different valid day: mid-typing invalid intermediates and same-day retypes keep the
 * selection, while a real day change drops the now-incompatible candidate.
 */
fun shouldClearPlanningSelection(
    previousValid: LocalDate?,
    nextValid: LocalDate?,
): Boolean = previousValid != null && nextValid != null && previousValid != nextValid

// The Material3 DatePicker speaks epoch millis while planning speaks yyyy-MM-dd calendar
// dates (the remittance-picker precedent): seed and read back in UTC so confirming an
// unchanged date can never shift it a day (Manila midnight is the previous UTC date).
@OptIn(ExperimentalTime::class)
fun reliefIsoToPickerMillis(value: String): Long? {
    if (parseInviteDate(value) == null) return null
    return LocalDate.parse(value).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
}

@OptIn(ExperimentalTime::class)
fun reliefPickerMillisToIso(millis: Long?): String? {
    if (millis == null) return null
    return Instant
        .fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.UTC)
        .date
        .toString()
}
