package com.companyb.companyapp.session

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.delete
import com.companyb.companyapp.api.get
import com.companyb.companyapp.api.post
import com.companyb.companyapp.api.routes.bodyIfPresent
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.contracts.session.AddSessionConcernRequest
import com.companyb.companyapp.contracts.session.ConcernResponse
import com.companyb.companyapp.contracts.session.PromoteConcernRequest
import com.companyb.companyapp.contracts.session.RemoveSessionConcernRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.session.dashboard.toResponse
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
    path = ApiRoutes.CONCERNS,
    methods = [HttpMethod.GET],
    operationId = "concerns",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ConcernResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.CONCERN_PATH,
    methods = [HttpMethod.DELETE],
    operationId = "removeConcernFromList",
    security = [OpenApiSecurity(name = "BearerAuth")],
    pathParams = [OpenApiParam(name = "concernId", type = UUID::class, required = true)],
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_CONCERNS_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_concerns_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ConcernResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_CONCERNS_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_concerns_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = AddSessionConcernRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_CONCERN_PATH,
    methods = [HttpMethod.DELETE],
    pathParams = [
        OpenApiParam(
            name = "sessionId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "concernId", type = UUID::class, required = true),
    ],
    operationId = "session_concern_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = RemoveSessionConcernRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_PROMOTE_CONCERN_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_promote_concern",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = PromoteConcernRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = ConcernResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
internal object SessionConcernRoutes {
    fun register(config: JavalinConfig) {
        config.get(ApiRoutes.CONCERNS, concernsGate, ::handleGetConcerns)
        config.delete(ApiRoutes.CONCERN_PATH, concernsGate, ::handleRemoveConcernFromList)
        config.get(ApiRoutes.SESSION_CONCERNS_PATH, sessionDayGate, ::handleGetSessionConcerns)
        config.post(ApiRoutes.SESSION_CONCERNS_PATH, sessionDayGate, ::handleAddSessionConcern)
        config.delete(ApiRoutes.SESSION_CONCERN_PATH, sessionDayGate, ::handleRemoveSessionConcern)
        config.post(ApiRoutes.SESSION_PROMOTE_CONCERN_PATH, sessionDayGate, ::handlePromoteConcern)
    }

    private fun handleGetConcerns(context: Context) {
        val concerns = ConcernService.listAll()
        context.json(concerns.map { it.toResponse() })
    }

    private fun handleRemoveConcernFromList(context: Context) {
        ConcernService.removeFromList(context.callerUuid(), context.pathParamAsUuid("concernId"))
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handleGetSessionConcerns(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")

        val concerns = SessionConcernService.getForSession(callerId, sessionId)
        context.json(concerns.map { it.toResponse() })
    }

    private fun handleAddSessionConcern(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<AddSessionConcernRequest>()

        val concernId = uuidOrThrow(request.concernId, "concern id")

        SessionConcernService.addToSession(callerId, sessionId, concernId, request.reason)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handleRemoveSessionConcern(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val concernId = context.pathParamAsUuid("concernId")
        val reason = context.bodyIfPresent<RemoveSessionConcernRequest>()?.reason

        SessionConcernService.removeFromSession(callerId, sessionId, concernId, reason)
        context.status(HttpStatus.NO_CONTENT)
    }

    private fun handlePromoteConcern(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<PromoteConcernRequest>()

        if (request.label.isBlank()) throw BadRequestResponse("label must not be blank")
        val concernId = uuidOrThrow(request.id, "concern id")

        val concern =
            if (request.clearOtherConcerns) {
                SessionConcernService.promoteConcern(
                    callerId,
                    sessionId,
                    concernId,
                    request.label.trim(),
                    request.reason,
                )
            } else {
                SessionConcernService.addNewConcern(
                    callerId,
                    sessionId,
                    concernId,
                    request.label.trim(),
                    request.reason,
                )
            }
        context.status(HttpStatus.CREATED)
        context.json(concern.toResponse())
    }
}

private val concernsGate =
    Gate.Can(Act.WORK_ANYWHERE, message = "EDIT_BRANCH_DATA capability required to manage concerns")
