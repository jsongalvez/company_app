package com.companyb.companyapp.remittance

import com.companyb.companyapp.contracts.remittance.RemittanceDetailResponse
import com.companyb.companyapp.contracts.remittance.RemittanceResponse
import com.companyb.companyapp.domain.OperationalDay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// #120 — shared display vocabulary for the Remittance screens. Labels live in one frontend place
// per screen family instead of being hardcoded at every call site.

/** D1/D2 — type choices (create popup + header PATCH) and display labels. */
internal enum class RemittanceTypeChoice(
    val raw: String,
    val label: String,
) {
    SESSIONS("SESSION", "Sessions income"),
    PRODUCTS("PRODUCT", "Products income"),
}

/** D1/D2 — method choices and display labels. */
internal enum class RemittanceMethodChoice(
    val raw: String,
    val label: String,
) {
    BANK_TRANSFER("BANK_TRANSFER", "Bank transfer"),
    HANDED_TO_ACCOUNTANT("HANDED_TO_ACCOUNTANT", "Handed to accountant"),
}

internal fun remittanceTypeLabel(raw: String): String =
    RemittanceTypeChoice.entries.firstOrNull { it.raw == raw }?.label ?: raw

internal fun lineTypeLabel(raw: String): String =
    when (raw) {
        "SESSION" -> "Session"
        "PRODUCT_SALE" -> "Product sale"
        else -> raw
    }

internal fun remittanceMethodLabel(raw: String): String =
    RemittanceMethodChoice.entries.firstOrNull { it.raw == raw }?.label ?: raw

// D1 — Net appears only on submitted SESSION rows (the snapshot join; PRODUCT has no snapshot).
internal fun submittedSessionNet(raw: RemittanceResponse): String? =
    if (raw.status == com.companyb.companyapp.contracts.remittance.RemittanceStatus.SUBMITTED &&
        raw.type == com.companyb.companyapp.contracts.remittance.RemittanceType.SESSION
    ) {
        raw.netIncome
    } else {
        null
    }

// #561 — peso() lives shared in ui/screen/SessionMoney (finance + remittance consumers);
// this owner keeps only remittance-specific display vocabulary.

// #677 — single Manila-zone source for the remittance date helpers below
// (the picker speaks Manila calendar dates; the Undo window is Manila-anchored).
// #875 — the zone id is owned by shared OperationalDay; this stays a thin
// platform-specific wrapper because the time API needs a TimeZone, not a String.
internal val ManilaZone: TimeZone = TimeZone.of(OperationalDay.MANILA_ZONE_ID)

// D2 — create popup defaults the range to today (Manila business day, matching the backend's
// branch-day calendar).
internal fun todayIso(): String =
    Clock.System
        .now()
        .toLocalDateTime(ManilaZone)
        .date
        .toString()

internal fun isValidIsoDate(value: String): Boolean =
    DATE_PATTERN.matches(value) &&
        runCatching { LocalDate.parse(value) }.isSuccess

// D10 — undo is offered only while the submission is still inside the server-enforced 48h window.
// The server is authoritative; this is the hide-when-expired affordance (the #119 hide key:
// submittedAt != null && status == SUBMITTED, plus the clock check).
internal fun remittanceCanUndo(detail: RemittanceDetailResponse): Boolean {
    if (detail.status != com.companyb.companyapp.contracts.remittance.RemittanceStatus.SUBMITTED) return false
    val submittedAt =
        detail.submittedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return false
    return Clock.System.now() - submittedAt <= UNDO_WINDOW_HOURS.hours
}

// #677 — the eligible-Undo deadline affordance: the exact instant the 48h server window
// closes, rendered as a Manila calendar date for the receipt-adjacent Undo action.
// Null when the detail carries no parseable submission instant (button stays hidden).
internal fun remittanceUndoDeadline(detail: RemittanceDetailResponse): String? {
    val submittedAt =
        detail.submittedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return null
    val deadline =
        submittedAt
            .plus(UNDO_WINDOW_HOURS.hours)
            .toLocalDateTime(ManilaZone)
    return "${deadline.date} ${deadline.hour.toString().padStart(2, '0')}:" +
        deadline.minute.toString().padStart(2, '0')
}

// #677 — header branch label without inventing a name the client was not given
// (the shell-context #671 precedent): the branch id prefix.
internal fun remittanceBranchLabel(branchId: String?): String =
    if (branchId.isNullOrBlank()) "Branch unknown" else "Branch ${branchId.take(BRANCH_ID_PREFIX)}"

private const val BRANCH_ID_PREFIX = 8

internal const val UNDO_WINDOW_HOURS = 48L

private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
