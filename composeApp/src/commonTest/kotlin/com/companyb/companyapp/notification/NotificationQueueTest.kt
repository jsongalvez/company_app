package com.companyb.companyapp.notification

import com.companyb.companyapp.contracts.notification.NotificationResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure queue-math tests for #679: position-stable merging, row labels, destinations.
 */
class NotificationQueueTest {
    private fun notification(
        id: String,
        sessionId: String? = "s-$id",
        targetDate: String? = null,
        createdAt: String = "2026-08-05T06:00:00+08:00",
        branchId: String = "b1",
        isRead: Boolean = false,
    ) = NotificationResponse(
        id = id,
        sessionId = sessionId,
        branchId = branchId,
        message = "Message $id",
        isRead = isRead,
        readAt = null,
        createdAt = createdAt,
        targetDate = targetDate,
    )

    @Test
    fun merge_keeps_server_order_and_marks_visit_reads_in_place() {
        val n1 = notification("n1", createdAt = "2026-08-05T06:00:00+08:00")
        val n2 = notification("n2", createdAt = "2026-08-05T02:00:00+08:00")

        val merged = mergeQueueRows(unread = listOf(n2), readThisSession = listOf(n1))

        // n1 was read this visit but keeps its newer-first position, marked Read.
        assertEquals(expected = listOf("n1", "n2"), actual = merged.map { it.notification.id })
        assertEquals(expected = listOf(true, false), actual = merged.map { it.readThisVisit })
    }

    @Test
    fun merge_breaks_created_ties_by_id() {
        val a = notification("a")
        val b = notification("b")

        val merged = mergeQueueRows(unread = listOf(a, b), readThisSession = emptyList())

        assertEquals(expected = listOf("b", "a"), actual = merged.map { it.notification.id })
    }

    @Test
    fun merge_orders_mixed_offsets_chronologically() {
        // Same wall-clock hour, different offsets: the +08:00 row is an older instant than
        // the Z row, even though its wall time sorts later lexicographically.
        val manila = notification("manila", createdAt = "2026-08-05T06:00:00+08:00")
        val utc = notification("utc", createdAt = "2026-08-05T05:00:00Z")

        val merged = mergeQueueRows(unread = listOf(manila, utc), readThisSession = emptyList())

        assertEquals(expected = listOf("utc", "manila"), actual = merged.map { it.notification.id })
    }

    @Test
    fun event_label_prefers_session_then_relief_day() {
        assertEquals("Session", notification("n1").eventLabel())
        assertEquals("Relief day", notification("n2", sessionId = null, targetDate = "2026-08-16").eventLabel())
        assertEquals("Notification", notification("n3", sessionId = null).eventLabel())
    }

    @Test
    fun day_label_prefers_target_date_then_creation_date() {
        assertEquals("2026-08-16", notification("n1", targetDate = "2026-08-16").dayLabel())
        assertEquals("2026-08-05", notification("n2").dayLabel())
    }

    @Test
    fun destination_requires_a_session_or_a_branch_day() {
        assertTrue(notification("n1").hasDestination())
        assertTrue(notification("n2", sessionId = null, targetDate = "2026-08-16").hasDestination())
        assertFalse(notification("n3", sessionId = null).hasDestination())
    }

    @Test
    fun branch_short_is_a_stable_prefix() {
        assertEquals("12345678", branchShort("12345678-aaaa-bbbb-cccc-dddddddddddd"))
        assertEquals("b1", branchShort("b1"))
    }
}
