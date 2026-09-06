package com.companyb.companyapp.workforce.relief
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.AssignDelegateRequest
import com.companyb.companyapp.dto.DelegateResponse
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
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
    path = ApiRoutes.DELEGATES,
    methods = [HttpMethod.POST],
    operationId = "delegates",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = AssignDelegateRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = DelegateResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.DELEGATE_PATH,
    methods = [HttpMethod.DELETE],
    pathParams = [OpenApiParam(name = "delegateId", type = UUID::class, required = true)],
    operationId = "delegate_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = DelegateResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_DELEGATES_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_delegates",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<DelegateResponse>::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object MedicalMissionDelegateRoutes {
    fun listDelegates(config: JavalinConfig) {
        config.routes.before(ApiRoutes.BRANCH_DELEGATES_PATH) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.ASSIGN_DELEGATE,
            )
        }

        config.routes.get(ApiRoutes.BRANCH_DELEGATES_PATH) { context ->
            val branchId = context.pathParamAsUuid("branchId")
            context.json(MedicalMissionDelegateService.listDelegates(branchId).map { it.toResponse() })
        }
    }

    @Suppress("ThrowsCount")
    fun assignDelegate(config: JavalinConfig) {
        config.routes.before(ApiRoutes.DELEGATES) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.ASSIGN_DELEGATE,
            )
        }

        config.routes.post(ApiRoutes.DELEGATES) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<AssignDelegateRequest>()

            val delegateId = uuidOrThrow(request.delegateId, "delegate id")
            val targetUserId = uuidOrThrow(request.targetUserId, "target user id")
            val branchId = uuidOrThrow(request.branchId, "branch id")

            val result = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

            context.status(HttpStatus.CREATED)
            context.json(result.toResponse())
        }
    }

    @Suppress("ThrowsCount")
    fun revokeDelegate(config: JavalinConfig) {
        config.routes.before(ApiRoutes.DELEGATE_PATH) { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.ASSIGN_DELEGATE,
            )
        }

        config.routes.delete(ApiRoutes.DELEGATE_PATH) { context ->
            val callerId = context.callerUuid()
            val delegateId = context.pathParamAsUuid("delegateId")

            val result = MedicalMissionDelegateService.revokeDelegate(delegateId, callerId)

            context.status(HttpStatus.OK)
            context.json(result.toResponse())
        }
    }

    private fun MedicalMissionDelegate.toResponse(): DelegateResponse =
        DelegateResponse(
            id = id.toString(),
            targetUser = targetUser.toString(),
            assignedAt = assignedAt.toString(),
            assignedBy = assignedBy.toString(),
            branchId = branchId.toString(),
            endedAt = endedAt?.toString(),
        )
}
