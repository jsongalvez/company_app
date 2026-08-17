package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.DenyReliefAccessRequest
import com.companyb.companyapp.dto.GrantReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.service.ReliefAccessService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.javalin.http.bodyAsClass
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_REQUEST,
    methods = [HttpMethod.POST],
    operationId = "relief_access_request",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_DENY_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "requestId", type = UUID::class, required = true)],
    operationId = "relief_access_deny",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_GRANT_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "requestId", type = UUID::class, required = true)],
    operationId = "relief_access_grant",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object ReliefAccessRoutes {
    @Suppress("ThrowsCount")
    fun grantReliefAccess(config: JavalinConfig) {
        config.routes.patch(ApiRoutes.RELIEF_ACCESS_GRANT_PATH) { context ->
            val callerId = context.callerUuid()
            val requestId = context.pathParamAsUuid("requestId")
            val reason = context.bodyIfPresent<GrantReliefAccessRequest>()?.reason

            val result = ReliefAccessService.grantAccess(requestId, callerId, reason)

            context.status(HttpStatus.OK)
            context.json(
                ReliefAccessResponse(
                    id = result.id.toString(),
                    branchDayId = result.branchDayId.toString(),
                    requestedBy = result.requestedBy.toString(),
                    requestStatus = result.requestStatus.name,
                    targetUser = result.targetUser.toString(),
                    grantedBy = result.grantedBy?.toString(),
                    grantedAt = result.grantedAt?.toString(),
                ),
            )
        }
    }

    @Suppress("ThrowsCount")
    fun denyReliefAccess(config: JavalinConfig) {
        config.routes.patch(ApiRoutes.RELIEF_ACCESS_DENY_PATH) { context ->
            val callerId = context.callerUuid()
            val requestId = context.pathParamAsUuid("requestId")
            val reason = context.bodyIfPresent<DenyReliefAccessRequest>()?.reason

            val result = ReliefAccessService.denyAccess(requestId, callerId, reason)

            context.status(HttpStatus.OK)
            context.json(
                ReliefAccessResponse(
                    id = result.id.toString(),
                    branchDayId = result.branchDayId.toString(),
                    requestedBy = result.requestedBy.toString(),
                    requestStatus = result.requestStatus.name,
                    targetUser = result.targetUser.toString(),
                    grantedBy = result.grantedBy?.toString(),
                    grantedAt = result.grantedAt?.toString(),
                ),
            )
        }
    }

    @Suppress("ThrowsCount")
    fun requestReliefAccess(config: JavalinConfig) {
        config.routes.post(ApiRoutes.RELIEF_ACCESS_REQUEST) { context ->
            val callerId = context.callerUuid()
            val request = context.bodyAsClass<ReliefAccessRequest>()

            val requestId = uuidOrThrow(request.requestId, "request id")
            val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")
            val targetUserId = uuidOrThrow(request.targetUserId, "target user id")

            val result =
                ReliefAccessService.requestReliefAccess(
                    requestId,
                    branchDayId,
                    targetUserId,
                    callerId,
                    request.reason,
                )

            context.status(HttpStatus.CREATED)
            context.json(
                ReliefAccessResponse(
                    id = result.id.toString(),
                    branchDayId = result.branchDayId.toString(),
                    requestedBy = result.requestedBy.toString(),
                    requestStatus = result.requestStatus.name,
                    targetUser = result.targetUser.toString(),
                    grantedBy = result.grantedBy?.toString(),
                    grantedAt = result.grantedAt?.toString(),
                ),
            )
        }
    }
}
