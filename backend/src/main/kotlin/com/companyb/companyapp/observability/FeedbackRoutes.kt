package com.companyb.companyapp.observability

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.FeedbackRequest
import com.companyb.companyapp.dto.FeedbackResponse
import com.companyb.companyapp.identity.RateLimiter
import io.javalin.config.JavalinConfig
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.FEEDBACK,
    methods = [HttpMethod.POST],
    operationId = "submit_feedback",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = FeedbackRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = FeedbackResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "429"),
    ],
)
object FeedbackRoutes {
    private const val USER_KEY_PREFIX = "feedback:user:"
    private const val IP_KEY_PREFIX = "feedback:ip:"

    /**
     * #475 — authenticated incident report. Bearer-only on purpose (clock-in
     * precedent): every signed-in user may report, so no capability gate.
     * Abuse is bounded by per-user and per-IP budgets sharing [RateLimiter].
     */
    fun register(config: JavalinConfig) {
        config.routes.post(ApiRoutes.FEEDBACK) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<FeedbackRequest>()
            if (!withinBudget(callerId, context.ip())) {
                context.status(HttpStatus.TOO_MANY_REQUESTS)
            } else {
                val filed = IncidentService.fileUserReport(callerId, request)
                context.status(HttpStatus.OK)
                context.json(FeedbackResponse(filed.packet, filed.duplicate))
            }
        }
    }

    private fun withinBudget(
        callerId: UUID,
        ip: String,
    ): Boolean = RateLimiter.isAllowed(USER_KEY_PREFIX + callerId) && RateLimiter.isAllowed(IP_KEY_PREFIX + ip)
}
