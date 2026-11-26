package com.companyb.companyapp.remittance

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.get
import com.companyb.companyapp.api.patch
import com.companyb.companyapp.api.post
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.uuidFromQuery
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.authorization.Act
import com.companyb.companyapp.authorization.Place
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.contracts.remittance.CreateRemittanceDraftRequest
import com.companyb.companyapp.contracts.remittance.RemittanceDetailResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDriftResponse
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceResponse
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceSubmitResponse
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.remittance.SubmitRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UndoRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UpdateRemittanceHeaderRequest
import com.companyb.companyapp.dto.ErrorResponse
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
import java.util.UUID

@OpenApi(
    path = ApiRoutes.REMITTANCES,
    methods = [HttpMethod.GET],
    queryParams = [
        OpenApiParam(
            name = "status",
            type = String::class,
            required = false,
        ), OpenApiParam(name = "branchId", type = UUID::class, required = true),
    ],
    operationId = "remittances_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<RemittanceResponse>::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.REMITTANCES,
    methods = [HttpMethod.POST],
    operationId = "remittances_post",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateRemittanceDraftRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = RemittanceResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.REMITTANCE_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "remittanceId", type = UUID::class, required = true)],
    operationId = "remittance_get",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = RemittanceDetailResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.REMITTANCE_PATH,
    methods = [HttpMethod.PATCH],
    pathParams = [OpenApiParam(name = "remittanceId", type = UUID::class, required = true)],
    operationId = "remittance_patch",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UpdateRemittanceHeaderRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = RemittanceResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.REMITTANCE_DRIFT_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "remittanceId", type = UUID::class, required = true)],
    operationId = "remittance_drift",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = RemittanceDriftResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.REMITTANCE_SUBMIT_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "remittanceId", type = UUID::class, required = true)],
    operationId = "remittance_submit",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = SubmitRemittanceRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = RemittanceSubmitResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.REMITTANCE_UNDO_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "remittanceId", type = UUID::class, required = true)],
    operationId = "remittance_undo",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = UndoRemittanceRequest::class)]),
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = RemittanceResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object RemittanceRoutes {
    fun register(config: JavalinConfig) {
        registerReads(config)
        registerMutations(config)
    }

    private fun remittancesGate(branchIdOf: (Context) -> UUID) =
        Gate.At(Act.SUBMIT_REMITTANCE) { Place.Branch(BranchService.findById(branchIdOf(it)).id) }

    private fun registerReads(config: JavalinConfig) {
        config.get(ApiRoutes.REMITTANCES, remittancesGate { it.uuidFromQuery("branchId") }, ::handleListRemittances)
        config.get(ApiRoutes.REMITTANCE_PATH, remittanceGate, ::handleGetRemittance)
        config.get(ApiRoutes.REMITTANCE_DRIFT_PATH, remittanceGate, ::handleGetDrift)
    }

    private fun registerMutations(config: JavalinConfig) {
        config.post(
            ApiRoutes.REMITTANCES,
            remittancesGate { uuidOrThrow(it.bodyAsClass<CreateRemittanceDraftRequest>().branchId, "branch id") },
            ::handleCreateDraft,
        )
        RemittanceLineRoutes.register(config)
        config.post(ApiRoutes.REMITTANCE_SUBMIT_PATH, remittanceGate, ::handleSubmit)
        config.post(ApiRoutes.REMITTANCE_UNDO_PATH, remittanceGate, ::handleUndo)
        config.patch(ApiRoutes.REMITTANCE_PATH, remittanceGate, ::handleUpdateHeader)
    }

    private fun handleCreateDraft(context: Context) {
        val callerId = context.callerUuid()
        val request = context.bodyAsClass<CreateRemittanceDraftRequest>()

        val id = uuidOrThrow(request.id, "remittance id")
        val header = parseHeader(request.type.name, request.method.name, request.dateRangeStart, request.dateRangeEnd)
        val branchId = uuidOrThrow(request.branchId, "branch id")

        val remittance =
            RemittanceService.createDraft(
                callerId = callerId,
                new =
                    NewRemittance(
                        id = id,
                        type = header.type,
                        branchId = branchId,
                        method = header.method,
                        dateRangeStart = header.dateRangeStart,
                        dateRangeEnd = header.dateRangeEnd,
                    ),
            )

        context.status(HttpStatus.CREATED)
        context.json(remittance.toResponse())
    }

    private data class ParsedRemittanceHeader(
        val type: RemittanceType,
        val method: RemittanceMethod,
        val dateRangeStart: LocalDate,
        val dateRangeEnd: LocalDate,
    )

    private fun parseHeader(
        type: String,
        method: String,
        dateRangeStart: String,
        dateRangeEnd: String,
    ): ParsedRemittanceHeader {
        val parsedType = parseRemittanceType(type)
        val parsedMethod = parseRemittanceMethod(method)
        val parsedStart = parseHeaderDate(dateRangeStart, "dateRangeStart")
        val parsedEnd = parseHeaderDate(dateRangeEnd, "dateRangeEnd")
        if (parsedEnd.isBefore(parsedStart)) {
            throw BadRequestResponse("dateRangeEnd must not be before dateRangeStart")
        }
        return ParsedRemittanceHeader(parsedType, parsedMethod, parsedStart, parsedEnd)
    }

    private fun parseRemittanceType(raw: String): RemittanceType =
        runCatching {
            RemittanceType.valueOf(raw.uppercase())
        }.getOrElse {
            throw BadRequestResponse(
                "Invalid remittance type: must be SESSION or PRODUCT",
            )
        }

    private fun parseRemittanceMethod(raw: String): RemittanceMethod =
        runCatching {
            RemittanceMethod.valueOf(raw.uppercase())
        }.getOrElse {
            throw BadRequestResponse(
                "Invalid remittance method: must be BANK_TRANSFER or HANDED_TO_ACCOUNTANT",
            )
        }

    private fun parseHeaderDate(
        raw: String,
        paramName: String,
    ): LocalDate =
        runCatching { LocalDate.parse(raw) }
            .getOrElse { throw BadRequestResponse("Invalid $paramName") }

    private fun handleGetRemittance(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")

        val detail = RemittanceService.getRemittance(remittanceId)
        context.json(detail.toResponse())
    }

    private fun handleListRemittances(context: Context) {
        val branchId = context.uuidFromQuery("branchId")
        val rawStatus = context.queryParam("status")
        val status =
            when {
                rawStatus == null || rawStatus.equals("ALL", ignoreCase = true) -> {
                    null
                }

                else -> {
                    runCatching {
                        RemittanceStatus.valueOf(rawStatus.uppercase())
                    }.getOrElse {
                        throw BadRequestResponse("Invalid status: must be DRAFT, SUBMITTED or ALL")
                    }
                }
            }

        val remittances = RemittanceService.listRemittances(branchId, status)
        context.json(remittances.map { it.toListResponse() })
    }

    private fun handleGetDrift(context: Context) {
        val remittanceId = context.pathParamAsUuid("remittanceId")

        val drift = RemittanceService.getDrift(remittanceId)
        context.json(drift.toResponse())
    }

    private fun handleSubmit(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val request = context.bodyAsClass<SubmitRemittanceRequest>()

        val result =
            RemittanceService.submit(
                callerId = callerId,
                remittanceId = remittanceId,
                expectedVersion = request.expectedVersion,
            )

        context.json(result.toSubmitResponse())
    }

    private fun handleUndo(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val request = context.bodyAsClass<UndoRemittanceRequest>()

        val reason = request.reason.trim()
        if (reason.isBlank()) {
            throw BadRequestResponse("reason is required")
        }
        if (reason.any { it == '\n' || it == '\r' }) {
            throw BadRequestResponse("reason must be a single line")
        }

        val remittance =
            RemittanceService.undo(
                callerId = callerId,
                remittanceId = remittanceId,
                expectedVersion = request.expectedVersion,
                reason = reason,
            )

        context.json(remittance.toResponse())
    }

    private fun handleUpdateHeader(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val request = context.bodyAsClass<UpdateRemittanceHeaderRequest>()

        val header = parseHeader(request.type.name, request.method.name, request.dateRangeStart, request.dateRangeEnd)

        val remittance =
            RemittanceService.updateHeader(
                callerId = callerId,
                remittanceId = remittanceId,
                edit =
                    RemittanceHeader(
                        type = header.type,
                        method = header.method,
                        dateRangeStart = header.dateRangeStart,
                        dateRangeEnd = header.dateRangeEnd,
                    ),
                expectedVersion = request.expectedVersion,
            )

        context.json(remittance.toResponse())
    }
}

internal val remittanceGate =
    Gate.At(Act.SUBMIT_REMITTANCE) { RemittanceAuthz.placeOf(it.pathParamAsUuid("remittanceId")) }
