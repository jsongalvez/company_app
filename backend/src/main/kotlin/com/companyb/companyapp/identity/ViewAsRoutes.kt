package com.companyb.companyapp.identity

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.contracts.identity.StopViewAsRequest
import com.companyb.companyapp.contracts.identity.ViewAsRequest
import com.companyb.companyapp.contracts.identity.ViewAsResponse
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity

@OpenApi(
    path = ApiRoutes.VIEW_AS,
    methods = [HttpMethod.POST],
    operationId = "view_as_start",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ViewAsRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ViewAsResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.VIEW_AS_STOP,
    methods = [HttpMethod.POST],
    operationId = "view_as_stop",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = StopViewAsRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object ViewAsRoutes {
    private val EVENTS = setOf("stopped", "timed out", "changes allowed", "changes off")

    /** SUPERUSER only: start viewing as someone (checked and logged), and log the stop. */
    fun register(config: JavalinConfig) {
        config.routes.post(ApiRoutes.VIEW_AS) { context ->
            val request = context.bodyAsClass<ViewAsRequest>()
            val target = uuidOrThrow(request.userId, "user id")
            context.json(ViewAs.start(context.callerUuid(), target, request.allowChanges))
        }
        config.routes.post(ApiRoutes.VIEW_AS_STOP) { context ->
            val request = context.bodyAsClass<StopViewAsRequest>()
            if (request.event !in EVENTS) throw BadRequestResponse("Unknown event")
            ViewAs.stop(context.callerUuid(), uuidOrThrow(request.userId, "user id"), request.event)
            context.status(HttpStatus.NO_CONTENT)
        }
    }
}
