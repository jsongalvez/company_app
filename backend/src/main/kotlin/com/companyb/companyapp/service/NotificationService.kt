package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.NotificationRepository
import com.companyb.companyapp.repository.model.Notification
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

private val logger = KotlinLogging.logger {}

object NotificationService {
    fun listUnread(callerId: UUID): List<Notification> {
        logger.info { "[LIST-UNREAD] Fetching unread notifications for user $callerId" }
        return NotificationRepository.findUnreadByUserId(callerId)
    }

    fun markAllRead(callerId: UUID): Int {
        val remainingUnread = NotificationRepository.markAllRead(callerId)
        logger.info {
            "[MARK-ALL-READ] Marked all notifications as read for user ${callerId.toString().maskUUID()}, " +
                "$remainingUnread unread remaining"
        }
        return remainingUnread
    }

    fun markRead(
        callerId: UUID,
        notificationId: UUID,
    ): Notification {
        val notification =
            NotificationRepository.markRead(notificationId)
                ?: throw NotFoundException("Notification not found")

        if (notification.userId != callerId) {
            throw NotFoundException("Notification not found")
        }

        logger.info { "[MARK-READ] Notification $notificationId marked as read" }
        return notification
    }
}
