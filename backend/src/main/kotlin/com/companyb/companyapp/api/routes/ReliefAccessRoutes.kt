package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.BranchDayUserResponse
import com.companyb.companyapp.dto.DenyReliefAccessRequest
import com.companyb.companyapp.dto.GrantReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.repository.model.ReliefAccess
import com.companyb.companyapp.service.ReliefAccessService
import com.companyb.companyapp.service.ReliefGrantOutcome
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
    path = ApiRoutes.RELIEF_ACCESS,
    methods = [HttpMethod.GET],
    operationId = "relief_access_list",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_CANDIDATES,
    methods = [HttpMethod.GET],
    operationId = "relief_access_candidates",
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
    /** Shared response mapper — the three write commands and the list read all project the same shape. */
    private fun ReliefAccess.toResponse(): ReliefAccessResponse =
        ReliefAccessResponse(
            id = id.toString(),
            branchDayId = branchDayId.toString(),
            requestedBy = requestedBy.toString(),
            requestStatus = requestStatus,
            targetUser = targetUser.toString(),
            grantedBy = grantedBy?.toString(),
            grantedAt = grantedAt?.toString(),
        )

    fun listReliefAccess(config: JavalinConfig) {
        config.routes.get(ApiRoutes.RELIEF_ACCESS) { context ->
            val callerId = context.callerUuid()
            val branchDayParam =
                context.queryParam("branchDayId") ?: throw BadRequestResponse("branchDayId is required")
            val branchDayId =
                runCatching { UUID.fromString(branchDayParam) }.getOrElse {
                    throw BadRequestResponse("Invalid branchDayId")
                }

            val requests = ReliefAccessService.listForCaller(callerId, branchDayId)

            context.status(HttpStatus.OK)
            context.json(requests.map { it.toResponse() })
        }
    }

    fun listReliefCandidates(config: JavalinConfig) {
        config.routes.get(ApiRoutes.RELIEF_ACCESS_CANDIDATES) { context ->
            val callerId = context.callerUuid()
            val branchDayParam =
                context.queryParam("branchDayId") ?: throw BadRequestResponse("branchDayId is required")
            val branchDayId =
                runCatching { UUID.fromString(branchDayParam) }.getOrElse {
                    throw BadRequestResponse("Invalid branchDayId")
                }

            val candidates = ReliefAccessService.listCandidates(callerId, branchDayId)

            context.status(HttpStatus.OK)
            context.json(candidates.map { BranchDayUserResponse(it.userId.toString(), it.displayName) })
        }
    }

    @Suppress("ThrowsCount")
    fun grantReliefAccess(config: JavalinConfig) {
        config.routes.patch(ApiRoutes.RELIEF_ACCESS_GRANT_PATH) { context ->
            val callerId = context.callerUuid()
            val requestId = context.pathParamAsUuid("requestId")
            val reason = context.bodyIfPresent<GrantReliefAccessRequest>()?.reason

            when (val outcome = ReliefAccessService.grantAccess(requestId, callerId, reason)) {
                is ReliefGrantOutcome.Granted -> {
                    context.status(HttpStatus.OK)
                    context.json(outcome.reliefAccess.toResponse())
                }

                is ReliefGrantOutcome.Superseded -> {
                    // #354 edge 3: a losing decision must not read as success (200 with the
                    // winner's row); the conflict carries the surviving request id instead.
                    throw ConflictException(
                        "This relief request was already decided — granted request ${outcome.reliefAccess.id} stands",
                    )
                }
            }
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
            context.json(result.toResponse())
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
            context.json(result.toResponse())
        }
    }
}
