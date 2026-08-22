package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.DenyReliefAccessRequest
import com.companyb.companyapp.dto.GrantReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.dto.ReliefBranchOptionResponse
import com.companyb.companyapp.repository.ReliefRequestWithBranch
import com.companyb.companyapp.repository.model.ReliefAccess
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
import java.time.LocalDate
import java.time.format.DateTimeParseException
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
    path = ApiRoutes.RELIEF_ACCESS_MINE,
    methods = [HttpMethod.GET],
    operationId = "relief_access_mine",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_BRANCH_OPTIONS,
    methods = [HttpMethod.GET],
    operationId = "relief_access_branch_options",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_CANCEL_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "requestId", type = UUID::class, required = true)],
    operationId = "relief_access_cancel",
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
    /**
     * Shared response mapper — every command and read projects the same shape. Branch
     * context rides only on the mine list (the per-day read implies it).
     */
    private fun ReliefAccess.toResponse(): ReliefAccessResponse =
        ReliefAccessResponse(
            id = id.toString(),
            branchDayId = branchDayId.toString(),
            requestedBy = requestedBy.toString(),
            requestStatus = requestStatus,
            grantedBy = grantedBy?.toString(),
            grantedAt = grantedAt?.toString(),
        )

    private fun ReliefRequestWithBranch.toResponse(): ReliefAccessResponse =
        ReliefAccessResponse(
            id = access.id.toString(),
            branchDayId = access.branchDayId.toString(),
            requestedBy = access.requestedBy.toString(),
            requestStatus = access.requestStatus,
            grantedBy = access.grantedBy?.toString(),
            grantedAt = access.grantedAt?.toString(),
            branchId = branchId.toString(),
            branchName = branchName,
            date = date.toString(),
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

    fun listMine(config: JavalinConfig) {
        config.routes.get(ApiRoutes.RELIEF_ACCESS_MINE) { context ->
            val mine = ReliefAccessService.listMine(context.callerUuid())

            context.status(HttpStatus.OK)
            context.json(mine.map { it.toResponse() })
        }
    }

    fun listBranchOptions(config: JavalinConfig) {
        config.routes.get(ApiRoutes.RELIEF_ACCESS_BRANCH_OPTIONS) { context ->
            val options = ReliefAccessService.listBranchOptions()

            context.status(HttpStatus.OK)
            context.json(
                options.map {
                    ReliefBranchOptionResponse(
                        branchId = it.id.toString(),
                        branchName = it.name,
                        branchType = it.branchType,
                    )
                },
            )
        }
    }

    @Suppress("ThrowsCount")
    fun grantReliefAccess(config: JavalinConfig) {
        config.routes.patch(ApiRoutes.RELIEF_ACCESS_GRANT_PATH) { context ->
            val callerId = context.callerUuid()
            val requestId = context.pathParamAsUuid("requestId")
            val reason = context.bodyIfPresent<GrantReliefAccessRequest>()?.reason

            // #357: no supersede outcome exists anymore — a non-PENDING replay is an
            // idempotent 200 with the row's current state.
            val result = ReliefAccessService.grantAccess(requestId, callerId, reason)

            context.status(HttpStatus.OK)
            context.json(result.toResponse())
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
    fun cancelReliefAccess(config: JavalinConfig) {
        config.routes.patch(ApiRoutes.RELIEF_ACCESS_CANCEL_PATH) { context ->
            val callerId = context.callerUuid()
            val requestId = context.pathParamAsUuid("requestId")
            val reason = context.bodyIfPresent<DenyReliefAccessRequest>()?.reason

            val result = ReliefAccessService.cancelRequest(requestId, callerId, reason)

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
            val branchId = uuidOrThrow(request.branchId, "branch id")
            val date = parseOptionalDate(request.date)

            val result =
                ReliefAccessService.requestReliefAccess(
                    requestId,
                    branchId,
                    date,
                    callerId,
                    request.reason,
                )

            context.status(HttpStatus.CREATED)
            context.json(result.toResponse())
        }
    }

    private fun parseOptionalDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            LocalDate.parse(raw)
        }.getOrElse { throw BadRequestResponse("Invalid date (expected ISO yyyy-MM-dd)") }
    }
}
