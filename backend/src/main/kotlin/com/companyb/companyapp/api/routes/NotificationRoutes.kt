package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.repository.model.Notification
import com.companyb.companyapp.service.NotificationService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import java.util.UUID

object NotificationRoutes {
    fun register(config: JavalinConfig) {
        config.routes.get("/api/notifications") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))

            val notifications = NotificationService.listUnread(callerId)

            context.status(HttpStatus.OK)
            context.json(notifications.map { it.toResponse() })
        }

        config.routes.patch("/api/notifications/{notificationId}/read") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val notificationId = context.pathParamAsUuid("notificationId")

            val notification = NotificationService.markRead(callerId, notificationId)

            context.status(HttpStatus.OK)
            context.json(notification.toResponse())
        }
    }

    private fun Notification.toResponse(): NotificationResponse =
        NotificationResponse(
            id = id.toString(),
            sessionId = sessionId.toString(),
            branchId = branchId.toString(),
            isRead = isRead,
            readAt = readAt?.toString(),
            createdAt = createdAt.toString(),
        )
}
