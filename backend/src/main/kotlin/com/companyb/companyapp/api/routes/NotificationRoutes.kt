package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.NotificationMarkAllReadResponse
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.repository.model.Notification
import com.companyb.companyapp.service.NotificationService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.NOTIFICATIONS,
    methods = [HttpMethod.GET],
    operationId = "notifications",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.NOTIFICATIONS_READ_ALL,
    methods = [HttpMethod.POST],
    operationId = "notifications_read_all",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.NOTIFICATIONS_HISTORY,
    methods = [HttpMethod.GET],
    operationId = "notifications_history",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.NOTIFICATION_READ_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "notificationId", type = UUID::class, required = true)],
    operationId = "notification_read",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object NotificationRoutes {
    fun register(config: JavalinConfig) {
        config.routes.get(ApiRoutes.NOTIFICATIONS) { context ->
            val callerId = context.callerUuid()

            val notifications = NotificationService.listUnread(callerId)

            context.status(HttpStatus.OK)
            context.json(notifications.map { it.toResponse() })
        }

        // #356 — history: every row the caller owns, read + unread, newest first.
        config.routes.get(ApiRoutes.NOTIFICATIONS_HISTORY) { context ->
            val callerId = context.callerUuid()

            val notifications = NotificationService.listHistory(callerId)

            context.status(HttpStatus.OK)
            context.json(notifications.map { it.toResponse() })
        }

        config.routes.post(ApiRoutes.NOTIFICATIONS_READ_ALL) { context ->
            val callerId = context.callerUuid()

            val unreadCount = NotificationService.markAllRead(callerId)

            context.status(HttpStatus.OK)
            context.json(NotificationMarkAllReadResponse(unreadCount))
        }

        config.routes.patch(ApiRoutes.NOTIFICATION_READ_PATH) { context ->
            val callerId = context.callerUuid()
            val notificationId = context.pathParamAsUuid("notificationId")

            val notification = NotificationService.markRead(callerId, notificationId)

            context.status(HttpStatus.OK)
            context.json(notification.toResponse())
        }
    }

    private fun Notification.toResponse(): NotificationResponse =
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
}
