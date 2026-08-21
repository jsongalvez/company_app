package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.NotificationRepository
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
