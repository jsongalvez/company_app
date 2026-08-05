package com.companyb.companyapp.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * Tests for [formatNotificationTimestamp] (D2: relative under 24h, absolute past 24h).
 * Pure function — fixed `now` injected, no clock dependency.
 */
class NotificationTimestampFormatTest {
    private val now = Instant.parse("2026-08-05T06:00:00Z")

    @Test
    fun underOneMinute_is_now() {
        assertEquals("now", formatNotificationTimestamp("2026-08-05T05:59:40Z", now))
    }

    @Test
    fun minutesAgo_is_relative_minutes() {
        assertEquals("2m ago", formatNotificationTimestamp("2026-08-05T05:58:00Z", now))
    }

    @Test
    fun hoursAgo_is_relative_hours() {
        assertEquals("3h ago", formatNotificationTimestamp("2026-08-05T03:00:00Z", now))
    }

    @Test
    fun past24h_is_absolute_date_in_manila() {
        assertEquals("Aug 4", formatNotificationTimestamp("2026-08-04T10:00:00+08:00", now))
    }

    @Test
    fun malformed_timestamp_returns_empty() {
        assertEquals("", formatNotificationTimestamp("not-a-date", now))
    }
}
