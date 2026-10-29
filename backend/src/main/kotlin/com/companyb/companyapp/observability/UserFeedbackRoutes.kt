package com.companyb.companyapp.observability

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.Access
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.incident.FeedbackNoteResponse
import com.companyb.companyapp.contracts.incident.FeedbackStatus
import com.companyb.companyapp.contracts.incident.SendFeedbackRequest
import com.companyb.companyapp.contracts.incident.SetFeedbackStatusRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.identity.RateLimiter
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.FEEDBACK_NOTES,
    methods = [HttpMethod.POST],
    operationId = "feedback_notes_send",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SendFeedbackRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = FeedbackNoteResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "429"),
    ],
)
@OpenApi(
    path = ApiRoutes.FEEDBACK_NOTES,
    methods = [HttpMethod.GET],
    operationId = "feedback_notes_list",
    security = [OpenApiSecurity(name = "BearerAuth")],
    queryParams = [OpenApiParam(name = "status", type = String::class, required = false)],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<FeedbackNoteResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.FEEDBACK_NOTE_PATH,
    methods = [HttpMethod.PATCH],
    operationId = "feedback_notes_set_status",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = "feedbackId", type = UUID::class, required = true)],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SetFeedbackStatusRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = FeedbackNoteResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object UserFeedbackRoutes {
    private const val KEY_PREFIX = "feedback-note:user:"
    private const val MESSAGE_MAX = 4000
    private const val DETAILS_MAX = 2000
    private const val PAGE_MAX = 300
    private const val VERSION_MAX = 64

    /** Anyone signed in may send; only GLOBAL MANAGE_USERS (owners) read and triage. */
    fun register(config: JavalinConfig) {
        config.routes.post(ApiRoutes.FEEDBACK_NOTES) { context ->
            val callerId = context.callerUuid()
            val note = parseNote(callerId, context.bodyAsClass<SendFeedbackRequest>())
            if (!RateLimiter.isAllowed(KEY_PREFIX + callerId)) {
                context.status(HttpStatus.TOO_MANY_REQUESTS)
            } else {
                context.status(HttpStatus.CREATED)
                context.json(UserFeedbackService.send(note).toResponse())
            }
        }

        config.routes.get(ApiRoutes.FEEDBACK_NOTES) { context ->
            requireTriage(context)
            val status =
                context.queryParam("status")?.let { raw ->
                    FeedbackStatus.entries.firstOrNull { it.name == raw } ?: throw BadRequestResponse("Unknown status")
                }
            context.json(UserFeedbackService.list(status).map { it.toResponse() })
        }

        config.routes.patch(ApiRoutes.FEEDBACK_NOTE_PATH) { context ->
            requireTriage(context)
            val id = context.pathParamAsUuid("feedbackId")
            val status = context.bodyAsClass<SetFeedbackStatusRequest>().status
            context.json(UserFeedbackService.setStatus(id, status).toResponse())
        }
    }

    private fun parseNote(
        callerId: UUID,
        request: SendFeedbackRequest,
    ): NewUserFeedback {
        val message = request.message.trim()
        if (message.isEmpty() || message.length > MESSAGE_MAX) {
            throw BadRequestResponse(
                "Please write up to $MESSAGE_MAX characters about what happened or what you'd like",
            )
        }
        return NewUserFeedback(
            id = uuidOrThrow(request.id, "feedback id"),
            userId = callerId,
            kind = request.kind,
            message = message,
            page = request.page.clean(PAGE_MAX),
            details = request.details.clean(DETAILS_MAX),
            appVersion = request.appVersion.clean(VERSION_MAX),
        )
    }

    private fun String?.clean(max: Int): String? = this?.trim()?.take(max)?.ifEmpty { null }

    private fun requireTriage(context: Context) =
        Access.require(context, Act.MANAGE_USERS, message = "Only owners can read feedback")

    private fun UserFeedback.toResponse() =
        FeedbackNoteResponse(
            id = id.toString(),
            kind = kind,
            message = message,
            page = page,
            details = details,
            appVersion = appVersion,
            status = status,
            createdAt = createdAt.toString(),
            userId = userId.toString(),
            userDisplayName = userDisplayName,
        )
}
