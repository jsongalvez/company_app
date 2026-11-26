package com.companyb.companyapp.session

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.delete
import com.companyb.companyapp.api.get
import com.companyb.companyapp.api.patch
import com.companyb.companyapp.api.post
import com.companyb.companyapp.api.routes.bodyIfPresent
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.contracts.session.AddSessionPractitionerRequest
import com.companyb.companyapp.contracts.session.RemovePractitionerRequest
import com.companyb.companyapp.contracts.session.SessionPractitionerResponse
import com.companyb.companyapp.contracts.session.UpdatePractitionerRemarksRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.session.dashboard.toResponse
import io.javalin.config.JavalinConfig
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
    path = ApiRoutes.SESSION_PRACTITIONERS_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_practitioners",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = AddSessionPractitionerRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SessionPractitionerResponse::class)]),
        OpenApiResponse(status = "201", content = [OpenApiContent(from = SessionPractitionerResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_PRACTITIONERS_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "sessionId", type = UUID::class, required = true)],
    operationId = "session_practitioners_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<SessionPractitionerResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_PRACTITIONER_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [
        OpenApiParam(
            name = "sessionId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "practitionerId", type = UUID::class, required = true),
    ],
    operationId = "session_practitioner_patch",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdatePractitionerRemarksRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = SessionPractitionerResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.SESSION_PRACTITIONER_PATH,
    methods = [HttpMethod.DELETE],
    pathParams = [
        OpenApiParam(
            name = "sessionId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "practitionerId", type = UUID::class, required = true),
    ],
    operationId = "session_practitioner_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = RemovePractitionerRequest::class)]),
    responses = [
        OpenApiResponse(status = "204"),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
internal object SessionPractitionerRoutes {
    fun registerRead(config: JavalinConfig) {
        // The practitioners list read (add-self refresh) takes the same gate as the mutations.
        config.get(ApiRoutes.SESSION_PRACTITIONERS_PATH, sessionDayGate, ::handleGetPractitioners)
    }

    fun registerMutations(config: JavalinConfig) {
        config.post(ApiRoutes.SESSION_PRACTITIONERS_PATH, sessionDayGate, ::handleAddPractitioner)
        config.patch(ApiRoutes.SESSION_PRACTITIONER_PATH, sessionDayGate, ::handleUpdatePractitionerRemarks)
        config.delete(ApiRoutes.SESSION_PRACTITIONER_PATH, sessionDayGate, ::handleRemovePractitioner)
    }

    private fun handleGetPractitioners(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")

        val practitioners = SessionPractitionerService.getForSession(callerId, sessionId)
        context.json(practitioners.map { it.toResponse() })
    }

    private fun handleAddPractitioner(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val request = context.bodyAsClass<AddSessionPractitionerRequest>()

        val practitionerId = uuidOrThrow(request.practitionerId, "practitioner id")
        val id = uuidOrThrow(request.id, "id")

        val result =
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                new =
                    NewSessionPractitioner(
                        id = id,
                        practitionerId = practitionerId,
                        remarks = request.remarks,
                        reason = request.reason,
                    ),
                sessionId = sessionId,
                version = request.version,
            )

        context.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
        context.json(result.practitioner.toResponse())
    }

    private fun handleUpdatePractitionerRemarks(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val practitionerId = context.pathParamAsUuid("practitionerId")
        val request = context.bodyAsClass<UpdatePractitionerRemarksRequest>()

        val updated =
            SessionPractitionerService.updatePractitionerRemarks(
                callerId = callerId,
                sessionId = sessionId,
                practitionerId = practitionerId,
                edit =
                    RemarksEdit(
                        remarks = request.remarks,
                        reason = request.reason,
                    ),
                version = request.version,
            )

        context.status(HttpStatus.OK)
        context.json(updated.toResponse())
    }

    private fun handleRemovePractitioner(context: Context) {
        val callerId = context.callerUuid()
        val sessionId = context.pathParamAsUuid("sessionId")
        val practitionerId = context.pathParamAsUuid("practitionerId")
        val request = context.bodyIfPresent<RemovePractitionerRequest>()

        SessionPractitionerService.removePractitioner(
            callerId = callerId,
            sessionId = sessionId,
            practitionerId = practitionerId,
            reason = request?.reason,
            version = request?.version,
        )

        context.status(HttpStatus.NO_CONTENT)
    }

    private fun SessionPractitioner.toResponse(): SessionPractitionerResponse =
        SessionPractitionerResponse(
            id = id.toString(),
            sessionId = sessionId.toString(),
            practitionerId = practitionerId.toString(),
            remarks = remarks,
            slotAtTime = slotAtTime.toInt(),
        )
}
