package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.dto.RemittanceDetailResponse
import com.companyb.companyapp.dto.RemittanceResponse
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
    if (raw.status == com.companyb.companyapp.domain.RemittanceStatus.SUBMITTED &&
        raw.type == com.companyb.companyapp.domain.RemittanceType.SESSION
    ) {
        raw.netIncome
    } else {
        null
    }

internal fun peso(raw: String): String = "₱$raw"

// D2 — create popup defaults the range to today (Manila business day, matching the backend's
// branch-day calendar).
internal fun todayIso(): String =
    Clock.System
        .now()
        .toLocalDateTime(TimeZone.of("Asia/Manila"))
        .date
        .toString()

internal fun isValidIsoDate(value: String): Boolean =
    DATE_PATTERN.matches(value) &&
        runCatching { LocalDate.parse(value) }.isSuccess

// D10 — undo is offered only while the submission is still inside the server-enforced 48h window.
// The server is authoritative; this is the hide-when-expired affordance (the #119 hide key:
// submittedAt != null && status == SUBMITTED, plus the clock check).
internal fun remittanceCanUndo(detail: RemittanceDetailResponse): Boolean {
    if (detail.status != com.companyb.companyapp.domain.RemittanceStatus.SUBMITTED) return false
    val submittedAt =
        detail.submittedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return false
    return Clock.System.now() - submittedAt <= UNDO_WINDOW_HOURS.hours
}

internal const val UNDO_WINDOW_HOURS = 48L

private val DATE_PATTERN = Regex("""\d{4}-\d{2}-\d{2}""")
