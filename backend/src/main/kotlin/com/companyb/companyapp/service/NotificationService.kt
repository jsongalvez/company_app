package com.companyb.companyapp.service

import com.companyb.companyapp.dto.NotificationHistoryResponse
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.NotificationHistoryCursor
import com.companyb.companyapp.repository.NotificationRepository
import com.companyb.companyapp.repository.encodeNotificationCursor
import com.companyb.companyapp.repository.model.Notification
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

private val logger = KotlinLogging.logger {}

object NotificationService {
    fun listUnread(callerId: UUID): List<Notification> {
        logger.info { "[LIST-UNREAD] Fetching unread notifications for user ${callerId.toString().maskUUID()}" }
        return NotificationRepository.findUnreadByUserId(callerId)
    }

    // #508 — lightweight badge count: one integer, no row hydration. The 60s poller reads
    // this instead of the full unread feed.
    fun countUnread(callerId: UUID): Int {
        logger.info { "[COUNT-UNREAD] Counting unread notifications for user ${callerId.toString().maskUUID()}" }
        return NotificationRepository.countUnreadByUserId(callerId)
    }

    /**
     * #508 — bounded permanent-history page (read + unread, newest first; ownership is the
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
        transaction {
            NotificationRepository.markAllReadInTransaction(callerId)
        }.also { remainingUnread ->
            logger.info {
                "[MARK-ALL-READ] Marked all notifications as read for user ${callerId.toString().maskUUID()}, " +
                    "$remainingUnread unread remaining"
            }
        }

    fun markRead(
        callerId: UUID,
        notificationId: UUID,
    ): Notification {
        // Ownership is enforced inside the store's WHERE clause — the 404-on-foreign-row
        // case must never have mutated the other user's row (audit finding #141: the pre-fix
        // version updated by id first, then threw 404 after the foreign row committed).
        val notification =
            transaction {
                NotificationRepository.markReadInTransaction(callerId, notificationId)
            } ?: throw NotFoundException("Notification not found")

        logger.info { "[MARK-READ] Notification ${notification.id.toString().maskUUID()} marked as read" }
        return notification
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
