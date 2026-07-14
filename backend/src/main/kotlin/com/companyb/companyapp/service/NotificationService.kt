package com.companyb.companyapp.service

import com.companyb.companyapp.repository.NotificationRepository
import com.companyb.companyapp.repository.model.Notification
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import java.util.UUID

private val logger = KotlinLogging.logger {}

object NotificationService {
    fun listUnread(callerId: UUID): List<Notification> {
        logger.info { "[LIST-UNREAD] Fetching unread notifications for user $callerId" }
        return NotificationRepository.findUnreadByUserId(callerId)
    }

    fun markRead(
        callerId: UUID,
        notificationId: UUID,
    ): Notification {
        val notification =
            NotificationRepository.markRead(notificationId)
                ?: throw NotFoundResponse("Notification not found")

        if (notification.userId != callerId) {
            throw NotFoundResponse("Notification not found")
        }

        logger.info { "[MARK-READ] Notification $notificationId marked as read" }
        return notification
    }
}
