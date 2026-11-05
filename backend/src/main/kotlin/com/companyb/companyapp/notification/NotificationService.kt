package com.companyb.companyapp.notification

import com.companyb.companyapp.contracts.notification.NotificationHistoryResponse
import com.companyb.companyapp.contracts.notification.NotificationResponse
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.observability.traced
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

object NotificationService {
    fun listUnread(callerId: UUID): List<Notification> {
        logger.info { "[LIST-UNREAD] Fetching unread notifications for user ${callerId.toString().maskUUID()}" }
        return NotificationRepository.findUnreadByUserId(callerId)
    }

    // lightweight badge count: one integer, no row hydration. The 60s poller reads
    // this instead of the full unread feed.
    fun countUnread(callerId: UUID): Int {
        logger.info { "[COUNT-UNREAD] Counting unread notifications for user ${callerId.toString().maskUUID()}" }
        return NotificationRepository.countUnreadByUserId(callerId)
    }

    /**
     * bounded permanent-history page (read + unread, newest first; ownership is the
     * WHERE clause). Fetches one row past the page to derive the next cursor; callers pass
     * that cursor back verbatim.
     */
    fun browseHistory(
        callerId: UUID,
        cursor: NotificationHistoryCursor?,
        limit: Int,
    ): NotificationHistoryResponse {
        logger.info { "[BROWSE-HISTORY] Fetching notification history for user ${callerId.toString().maskUUID()}" }
        val fetched = NotificationRepository.findHistoryPage(callerId, cursor, limit + 1)
        val hasMore = fetched.size > limit
        val entries = if (hasMore) fetched.dropLast(1) else fetched
        val nextCursor =
            if (hasMore) {
                entries.lastOrNull()?.let {
                    encodeNotificationCursor(NotificationHistoryCursor(it.createdAt, it.id))
                }
            } else {
                null
            }
        return NotificationHistoryResponse(
            entries = entries.map { it.toResponse() },
            nextCursor = nextCursor,
        )
    }

    fun markAllRead(callerId: UUID): Int =
        traced("NotificationService.markAllRead") {
            transaction {
                NotificationRepository.markAllReadInTransaction(callerId)
            }.also { remainingUnread ->
                logger.info {
                    "[MARK-ALL-READ] Marked all notifications as read for user ${callerId.toString().maskUUID()}, " +
                        "$remainingUnread unread remaining"
                }
            }
        }

    fun markRead(
        callerId: UUID,
        notificationId: UUID,
    ): Notification {
        return traced("NotificationService.markRead") {
            val notification =
                transaction {
                    NotificationRepository.markReadInTransaction(callerId, notificationId)
                } ?: throw NotFoundException("Notification not found")

            logger.info { "[MARK-READ] Notification ${notification.id.toString().maskUUID()} marked as read" }
            return@traced notification
        }
    }
}

internal fun Notification.toResponse(): NotificationResponse =
    NotificationResponse(
        id = id.toString(),
        sessionId = sessionId?.toString(),
        branchId = branchId.toString(),
        message = message,
        isRead = isRead,
        readAt = readAt?.toString(),
        createdAt = createdAt.toString(),
        targetDate = targetDate?.toString(),
    )
