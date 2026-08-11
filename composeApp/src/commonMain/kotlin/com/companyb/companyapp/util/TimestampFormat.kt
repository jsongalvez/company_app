package com.companyb.companyapp.util

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeFormat
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// #123 — extracted from NotificationsScreen (#112 D2) so the Audit Log screen shares one
// timestamp formatter: relative under 24h ("2m ago", "3h ago"), absolute date past 24h ("Aug 4").
// Absolute format renders in Asia/Manila — the backend writes all domain timestamps in this
// zone (see backend/AGENTS.md), so a device outside Manila still sees the business date.
private val displayZone = TimeZone.of("Asia/Manila")

private val absoluteFormat: DateTimeFormat<LocalDateTime> =
    LocalDateTime.Format {
        monthName(MonthNames.ENGLISH_ABBREVIATED)
        char(' ')
        dayOfMonth(Padding.NONE)
    }

internal fun formatRelativeTimestamp(
    createdAtIso: String,
    now: Instant = Clock.System.now(),
    logTag: String = "TimestampFormat",
): String {
    val createdAt = runCatching { Instant.parse(createdAtIso) }.getOrNull()
    if (createdAt == null) {
        // malformed server timestamp = handled data error; blank rather than crash the row
        logWarn(logTag, "unparseable timestamp: $createdAtIso")
        return ""
    }
    val elapsed = now - createdAt
    return when {
        elapsed < 1.minutes -> "now"
        elapsed < 1.hours -> "${elapsed.inWholeMinutes}m ago"
        elapsed < 24.hours -> "${elapsed.inWholeHours}h ago"
        else -> createdAt.toLocalDateTime(displayZone).format(absoluteFormat)
    }
}

private val timeOfDayFormat: DateTimeFormat<LocalDateTime> =
    LocalDateTime.Format {
        hour(Padding.ZERO)
        char(':')
        minute(Padding.ZERO)
    }

// #147 — dashboard "last updated" clock time (Asia/Manila, matching the backend's zone).
internal fun formatTimeOfDay(instant: Instant): String = instant.toLocalDateTime(displayZone).format(timeOfDayFormat)
