package com.companyb.companyapp.notification
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.parseBrowseLimit
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.NotificationHistoryResponse
import com.companyb.companyapp.dto.NotificationMarkAllReadResponse
import com.companyb.companyapp.dto.NotificationResponse
import com.companyb.companyapp.dto.NotificationUnreadCountResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.NOTIFICATIONS,
    methods = [HttpMethod.GET],
    operationId = "notifications",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<NotificationResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.NOTIFICATIONS_READ_ALL,
    methods = [HttpMethod.POST],
    operationId = "notifications_read_all",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = NotificationMarkAllReadResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.NOTIFICATIONS_HISTORY,
    methods = [HttpMethod.GET],
    operationId = "notifications_history",
    security = [OpenApiSecurity(name = "BearerAuth")],
    queryParams = [
        OpenApiParam(name = "cursor", type = String::class, required = false),
        OpenApiParam(name = "limit", type = String::class, required = false),
    ],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = NotificationHistoryResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.NOTIFICATIONS_UNREAD_COUNT,
    methods = [HttpMethod.GET],
    operationId = "notifications_unread_count",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = NotificationUnreadCountResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.NOTIFICATION_READ_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "notificationId", type = UUID::class, required = true)],
    operationId = "notification_read",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = NotificationResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
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
        // #508 — keyset-paged: bounded per response, stable across concurrent inserts.
        config.routes.get(ApiRoutes.NOTIFICATIONS_HISTORY) { context ->
            val callerId = context.callerUuid()

            val response =
                NotificationService.browseHistory(
                    callerId = callerId,
                    cursor = parseHistoryCursor(context.queryParam("cursor")),
                    limit = parseBrowseLimit(context.queryParam("limit")),
                )

            context.status(HttpStatus.OK)
            context.json(response)
        }

        // #508 — badge count without row hydration: the poller reads one integer.
        config.routes.get(ApiRoutes.NOTIFICATIONS_UNREAD_COUNT) { context ->
            val callerId = context.callerUuid()

            context.status(HttpStatus.OK)
            context.json(NotificationUnreadCountResponse(NotificationService.countUnread(callerId)))
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

    private fun parseHistoryCursor(raw: String?): NotificationHistoryCursor? {
        if (raw == null) return null
        return runCatching { decodeNotificationCursor(raw) }
            .getOrElse { throw BadRequestResponse("Invalid cursor") }
    }
}
