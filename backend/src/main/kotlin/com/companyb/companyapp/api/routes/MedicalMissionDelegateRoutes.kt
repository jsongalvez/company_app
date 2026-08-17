package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.AssignDelegateRequest
import com.companyb.companyapp.dto.DelegateResponse
import com.companyb.companyapp.service.MedicalMissionDelegateService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = "/api/delegates",
    methods = [HttpMethod.POST],
    operationId = "delegates",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/delegates/{delegateId}",
    methods = [HttpMethod.DELETE],
    pathParams = [OpenApiParam(name = "delegateId", type = UUID::class, required = true)],
    operationId = "delegate_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object MedicalMissionDelegateRoutes {
    @Suppress("ThrowsCount")
    fun assignDelegate(config: JavalinConfig) {
        config.routes.before("/api/delegates") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.ASSIGN_DELEGATE,
            )
        }

        config.routes.post("/api/delegates") { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<AssignDelegateRequest>()

            val delegateId = uuidOrThrow(request.delegateId, "delegate id")
            val targetUserId = uuidOrThrow(request.targetUserId, "target user id")
            val branchId = uuidOrThrow(request.branchId, "branch id")

            val result = MedicalMissionDelegateService.assignDelegate(delegateId, targetUserId, branchId, callerId)

            context.status(HttpStatus.CREATED)
            context.json(
                DelegateResponse(
                    id = result.id.toString(),
                    targetUser = result.targetUser.toString(),
                    assignedAt = result.assignedAt.toString(),
                    assignedBy = result.assignedBy.toString(),
                    branchId = result.branchId.toString(),
                    endedAt = result.endedAt?.toString(),
                ),
            )
        }
    }

    @Suppress("ThrowsCount")
    fun revokeDelegate(config: JavalinConfig) {
        config.routes.before("/api/delegates/{delegateId}") { context ->
            CapabilityFilter.requireGlobalCapability(
                context,
                CapabilityCodes.ASSIGN_DELEGATE,
            )
        }

        config.routes.delete("/api/delegates/{delegateId}") { context ->
            val callerId = context.callerUuid()
            val delegateId = context.pathParamAsUuid("delegateId")

            val result = MedicalMissionDelegateService.revokeDelegate(delegateId, callerId)

            context.status(HttpStatus.OK)
            context.json(
                DelegateResponse(
                    id = result.id.toString(),
                    targetUser = result.targetUser.toString(),
                    assignedAt = result.assignedAt.toString(),
                    assignedBy = result.assignedBy.toString(),
                    branchId = result.branchId.toString(),
                    endedAt = result.endedAt?.toString(),
                ),
            )
        }
    }
}
