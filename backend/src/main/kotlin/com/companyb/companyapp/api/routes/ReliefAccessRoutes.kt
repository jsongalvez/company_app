package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.dto.DenyReliefAccessRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.GrantReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.dto.ReliefBranchOptionResponse
import com.companyb.companyapp.identity.AccountReads
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
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiRequestBody
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_REQUEST,
    methods = [HttpMethod.POST],
    operationId = "relief_access_request",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = ReliefAccessRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = ReliefAccessResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS,
    methods = [HttpMethod.GET],
    queryParams = [
        OpenApiParam(
            name = "branchDayId",
            type = String::class,
            required = false,
        ), OpenApiParam(
            name = "branchId",
            type = String::class,
            required = true,
        ), OpenApiParam(name = "date", type = String::class, required = true),
    ],
    operationId = "relief_access_list",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ReliefAccessResponse>::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_MINE,
    methods = [HttpMethod.GET],
    operationId = "relief_access_mine",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ReliefAccessResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_BRANCH_OPTIONS,
    methods = [HttpMethod.GET],
    operationId = "relief_access_branch_options",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<ReliefBranchOptionResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_CANCEL_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "requestId", type = UUID::class, required = true)],
    operationId = "relief_access_cancel",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = DenyReliefAccessRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ReliefAccessResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "409", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_DENY_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "requestId", type = UUID::class, required = true)],
    operationId = "relief_access_deny",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = DenyReliefAccessRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ReliefAccessResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.RELIEF_ACCESS_GRANT_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "requestId", type = UUID::class, required = true)],
    operationId = "relief_access_grant",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = GrantReliefAccessRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = ReliefAccessResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@Suppress("TooManyFunctions")
object ReliefAccessRoutes {
    /**
     * Shared response mapper — every command and read projects the same shape. Branch
     * context rides only on the mine list (the per-day read implies it).
     */
    private fun ReliefAccess.toResponse(
        branchId: String? = null,
        branchName: String? = null,
        date: String? = null,
        requesterName: String? = null,
    ): ReliefAccessResponse =
        ReliefAccessResponse(
            id = id.toString(),
            branchDayId = branchDayId.toString(),
            requestedBy = requestedBy.toString(),
            requestStatus = requestStatus,
            grantedBy = grantedBy?.toString(),
            grantedAt = grantedAt?.toString(),
            branchId = branchId,
            branchName = branchName,
            date = date,
            requesterName = requesterName,
        )

    private fun ReliefRequestWithBranch.toResponse(): ReliefAccessResponse =
        access.toResponse(
            branchId = branchId.toString(),
            branchName = branchName,
            date = date.toString(),
        )

    // #358 — day-read rows carry requester display names (the deep-link panel labels rows
    // with them); one batched lookup per read.
    private fun List<ReliefAccess>.toNamedResponses(): List<ReliefAccessResponse> {
        val names = AccountReads.findDisplayNamesByIds(map { it.requestedBy })
        return map { it.toResponse(requesterName = names[it.requestedBy]) }
    }

    @Suppress("ThrowsCount")
    fun listReliefAccess(config: JavalinConfig) {
        config.routes.get(ApiRoutes.RELIEF_ACCESS) { context ->
            context.status(HttpStatus.OK)
            // #358 — two addressing forms: branchDayId directly, or the notification
            // deep-link pair (branchId + date). Each leg keeps the single-val service-call
            // shape the OpenAPI response-inference pass resolves.
            if (context.queryParam("branchDayId") != null) {
                val callerId = context.callerUuid()
                val branchDayId =
                    runCatching { UUID.fromString(context.queryParam("branchDayId")) }.getOrElse {
                        throw BadRequestResponse("Invalid branchDayId")
                    }
                val requests = ReliefAccessService.listForCaller(callerId, branchDayId)
                context.json(requests.toNamedResponses())
            } else {
                val callerId = context.callerUuid()
                val branchParam =
                    context.queryParam("branchId") ?: throw BadRequestResponse("branchDayId or branchId is required")
                val dateParam = context.queryParam("date") ?: throw BadRequestResponse("date is required")
                val branchId =
                    runCatching {
                        UUID.fromString(
                            branchParam,
                        )
                    }.getOrElse { throw BadRequestResponse("Invalid branchId") }
                val date =
                    runCatching { LocalDate.parse(dateParam) }.getOrElse {
                        throw BadRequestResponse("Invalid date (expected ISO yyyy-MM-dd)")
                    }
                val dayRequests = ReliefAccessService.listForCallerByDay(callerId, branchId, date)
                context.json(dayRequests.toNamedResponses())
            }
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
