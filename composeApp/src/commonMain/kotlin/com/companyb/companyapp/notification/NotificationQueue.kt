package com.companyb.companyapp.notification

import com.companyb.companyapp.contracts.notification.NotificationResponse
import kotlin.time.Instant

/**
 * One notification-queue row (#679): the server row plus whether this visit already marked
 * it read. Rows read this visit stay at their queue position with [readThisVisit] set —
 * the list never reorders under the pointer; an explicit refresh reconciles groups.
 */
data class QueueRow(
    val notification: NotificationResponse,
    val readThisVisit: Boolean,
)

/**
 * Merges the live unread feed with the visit-local read marks into one position-stable
 * queue. Order reproduces the server feed (`createdAt` DESC, `id` DESC — the repository
 * order), so a row that flips to read keeps its index. Timestamps parse to instants so
 * mixed offsets still order chronologically; an unparseable stamp sinks below parsed ones
 * with `id` DESC breaking ties deterministically.
 */
fun mergeQueueRows(
    unread: List<NotificationResponse>,
    readThisSession: List<NotificationResponse>,
): List<QueueRow> {
    val readIds = readThisSession.map { it.id }.toSet()
    return (unread + readThisSession)
        .distinctBy { it.id }
        .map { QueueRow(it, readThisVisit = it.id in readIds) }
        .sortedWith(
            compareByDescending<QueueRow> { it.notification.sortInstant() }
                .thenByDescending { it.notification.id },
        )
}

private fun NotificationResponse.sortInstant(): Instant? = runCatching { Instant.parse(createdAt) }.getOrNull()

/** Concise event/action label for a queue row; the message keeps the domain details. */
fun NotificationResponse.eventLabel(): String =
    when {
        sessionId != null -> "Session"
        targetDate != null -> "Relief day"
        else -> "Notification"
    }

/** Relevant operational date: the relief branch day when present, else the creation date. */
fun NotificationResponse.dayLabel(): String = targetDate ?: createdAt.take(DATE_PREFIX_LEN)

/** Whether the row has an authorized destination to open (session detail or branch day). */
fun NotificationResponse.hasDestination(): Boolean = sessionId != null || targetDate != null

/** Short branch token for the row secondary line (the contract carries only the id). */
fun branchShort(branchId: String): String = branchId.take(BRANCH_SHORT_LEN)

private const val BRANCH_SHORT_LEN = 8
private const val DATE_PREFIX_LEN = 10
