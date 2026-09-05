package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.dto.AddDayBreakdownRequest
import com.companyb.companyapp.dto.CreateRemittanceDraftRequest
import com.companyb.companyapp.dto.CreateRemittanceLineRequest
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.RemittanceDayBreakdownResponse
import com.companyb.companyapp.dto.RemittanceDetailResponse
import com.companyb.companyapp.dto.RemittanceDriftResponse
import com.companyb.companyapp.dto.RemittanceFinancialSnapshotResponse
import com.companyb.companyapp.dto.RemittanceLineResponse
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.dto.RemittanceSubmitResponse
import com.companyb.companyapp.dto.SubmitRemittanceRequest
import com.companyb.companyapp.dto.UndoRemittanceRequest
import com.companyb.companyapp.dto.UpdateRemittanceHeaderRequest
import com.companyb.companyapp.service.finance.remittance.Remittance
import com.companyb.companyapp.service.finance.remittance.RemittanceDayBreakdown
import com.companyb.companyapp.service.finance.remittance.RemittanceDetail
import com.companyb.companyapp.service.finance.remittance.RemittanceDrift
import com.companyb.companyapp.service.finance.remittance.RemittanceFinancialSnapshot
import com.companyb.companyapp.service.finance.remittance.RemittanceLine
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.service.finance.remittance.RemittanceSubmissionResult
import com.companyb.companyapp.service.finance.remittance.RemittanceWithNet
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.HandlerType
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

@Suppress("TooManyFunctions")
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
    path = ApiRoutes.REMITTANCE_DAY_BREAKDOWNS_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "remittanceId", type = UUID::class, required = true)],
    operationId = "remittance_day_breakdowns",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = AddDayBreakdownRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = RemittanceDayBreakdownResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.REMITTANCE_DAY_BREAKDOWN_PATH,
    methods = [HttpMethod.DELETE],
    pathParams = [
        OpenApiParam(
            name = "remittanceId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "breakdownId", type = UUID::class, required = true),
    ],
    operationId = "remittance_day_breakdown_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = RemittanceDayBreakdownResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.REMITTANCE_LINES_PATH,
    methods = [HttpMethod.POST],
    pathParams = [OpenApiParam(name = "remittanceId", type = UUID::class, required = true)],
    operationId = "remittance_lines",
    security = [OpenApiSecurity(name = "BearerAuth")],
    requestBody = OpenApiRequestBody(content = [OpenApiContent(from = CreateRemittanceLineRequest::class)]),
    responses = [
        OpenApiResponse(status = "201", content = [OpenApiContent(from = RemittanceLineResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.REMITTANCE_LINE_PATH,
    methods = [HttpMethod.DELETE],
    pathParams = [
        OpenApiParam(
            name = "remittanceId",
            type = UUID::class,
            required = true,
        ), OpenApiParam(name = "lineId", type = UUID::class, required = true),
    ],
    operationId = "remittance_line_delete",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = RemittanceLineResponse::class)]),
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
    @Suppress("LongMethod")
    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.REMITTANCES) { context ->
            val branchId =
                when (context.method()) {
                    HandlerType.POST -> {
                        uuidOrThrow(
                            context.bodyAsClass<CreateRemittanceDraftRequest>().branchId,
                            "branch id",
                        )
                    }

                    HandlerType.GET -> {
                        context.uuidFromQuery("branchId")
                    }

                    else -> {
                        return@before
                    }
                }
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.SUBMIT_REMITTANCE,
            )
        }

        config.routes.before(ApiRoutes.REMITTANCE_PATH) { context ->
            val remittanceId = context.pathParamAsUuid("remittanceId")
            CapabilityFilter.requireBranchCapabilityForRemittance(context, remittanceId)
        }

        config.routes.before(ApiRoutes.REMITTANCE_LINES_PATH) { context ->
            CapabilityFilter.requireBranchCapabilityForRemittance(
                context,
                context.pathParamAsUuid("remittanceId"),
            )
        }

        config.routes.before(ApiRoutes.REMITTANCE_LINE_PATH) { context ->
            CapabilityFilter.requireBranchCapabilityForRemittance(
                context,
                context.pathParamAsUuid("remittanceId"),
            )
        }

        config.routes.before(ApiRoutes.REMITTANCE_DAY_BREAKDOWNS_PATH) { context ->
            CapabilityFilter.requireBranchCapabilityForRemittance(
                context,
                context.pathParamAsUuid("remittanceId"),
            )
        }

        config.routes.before(ApiRoutes.REMITTANCE_DAY_BREAKDOWN_PATH) { context ->
            CapabilityFilter.requireBranchCapabilityForRemittance(
                context,
                context.pathParamAsUuid("remittanceId"),
            )
        }

        config.routes.before(ApiRoutes.REMITTANCE_SUBMIT_PATH) { context ->
            CapabilityFilter.requireBranchCapabilityForRemittance(
                context,
                context.pathParamAsUuid("remittanceId"),
            )
        }

        config.routes.before(ApiRoutes.REMITTANCE_UNDO_PATH) { context ->
            CapabilityFilter.requireBranchCapabilityForRemittance(
                context,
                context.pathParamAsUuid("remittanceId"),
            )
        }

        config.routes.before(ApiRoutes.REMITTANCE_DRIFT_PATH) { context ->
            CapabilityFilter.requireBranchCapabilityForRemittance(
                context,
                context.pathParamAsUuid("remittanceId"),
            )
        }

        config.routes.post(ApiRoutes.REMITTANCES, ::handleCreateDraft)
        config.routes.get(ApiRoutes.REMITTANCES, ::handleListRemittances)
        config.routes.get(ApiRoutes.REMITTANCE_PATH, ::handleGetRemittance)
        config.routes.get(ApiRoutes.REMITTANCE_DRIFT_PATH, ::handleGetDrift)
        config.routes.post(ApiRoutes.REMITTANCE_LINES_PATH, ::handleAddLine)
        config.routes.delete(ApiRoutes.REMITTANCE_LINE_PATH, ::handleRemoveLine)
        config.routes.post(ApiRoutes.REMITTANCE_DAY_BREAKDOWNS_PATH, ::handleAddDayBreakdown)
        config.routes.delete(ApiRoutes.REMITTANCE_DAY_BREAKDOWN_PATH, ::handleRemoveDayBreakdown)
        config.routes.post(ApiRoutes.REMITTANCE_SUBMIT_PATH, ::handleSubmit)
        config.routes.post(ApiRoutes.REMITTANCE_UNDO_PATH, ::handleUndo)
        config.routes.patch(ApiRoutes.REMITTANCE_PATH, ::handleUpdateHeader)
    }

    @Suppress("ThrowsCount")
    private fun handleCreateDraft(context: Context) {
        val callerId = context.callerUuid()
        val request = context.bodyAsClass<CreateRemittanceDraftRequest>()

        val id = uuidOrThrow(request.id, "remittance id")
        val header = parseHeader(request.type.name, request.method.name, request.dateRangeStart, request.dateRangeEnd)
        val branchId = uuidOrThrow(request.branchId, "branch id")

        val remittance =
            RemittanceService.createDraft(
                callerId = callerId,
                id = id,
                type = header.type,
                branchId = branchId,
                method = header.method,
                dateRangeStart = header.dateRangeStart,
                dateRangeEnd = header.dateRangeEnd,
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

    @Suppress("ThrowsCount")
    private fun parseHeader(
        type: String,
        method: String,
        dateRangeStart: String,
        dateRangeEnd: String,
    ): ParsedRemittanceHeader {
        val parsedType =
            runCatching { RemittanceType.valueOf(type.uppercase()) }
                .getOrElse {
                    throw BadRequestResponse(
                        "Invalid remittance type: must be SESSION or PRODUCT",
                    )
                }
        val parsedMethod =
            runCatching { RemittanceMethod.valueOf(method.uppercase()) }
                .getOrElse {
                    throw BadRequestResponse(
                        "Invalid remittance method: must be BANK_TRANSFER or HANDED_TO_ACCOUNTANT",
                    )
                }
        val parsedStart =
            runCatching { LocalDate.parse(dateRangeStart) }
                .getOrElse { throw BadRequestResponse("Invalid dateRangeStart") }
        val parsedEnd =
            runCatching { LocalDate.parse(dateRangeEnd) }
                .getOrElse { throw BadRequestResponse("Invalid dateRangeEnd") }
        if (parsedEnd.isBefore(parsedStart)) {
            throw BadRequestResponse("dateRangeEnd must not be before dateRangeStart")
        }
        return ParsedRemittanceHeader(parsedType, parsedMethod, parsedStart, parsedEnd)
    }

    private fun handleGetRemittance(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")

        val detail = RemittanceService.getRemittance(remittanceId)
        context.json(detail.toResponse())
    }

    @Suppress("ThrowsCount")
    private fun handleListRemittances(context: Context) {
        val branchId = context.uuidFromQuery("branchId")
        val rawStatus = context.queryParam("status")
        val status =
            when {
                rawStatus == null || rawStatus.equals("ALL", ignoreCase = true) -> {
                    null
                }

                else -> {
                    runCatching { RemittanceStatus.valueOf(rawStatus.uppercase()) }
                        .getOrElse {
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

    private fun handleRemoveDayBreakdown(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val breakdownId = context.pathParamAsUuid("breakdownId")

        val breakdown = RemittanceService.removeDayBreakdown(callerId, remittanceId, breakdownId)
        context.json(breakdown.toResponse())
    }

    @Suppress("ThrowsCount")
    private fun handleAddLine(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val request = context.bodyAsClass<CreateRemittanceLineRequest>()

        val id = uuidOrThrow(request.id, "line id")
        val type =
            RemittanceLineType.valueOf(request.type.name)
        val sessionId = request.sessionId?.let { uuidOrThrow(it, "session id") }
        val productSaleId = request.productSaleId?.let { uuidOrThrow(it, "product sale id") }
        val amount = parsePositiveBigDecimal(request.amount, "amount")

        when (type) {
            RemittanceLineType.SESSION -> {
                if (sessionId == null) throw BadRequestResponse("sessionId is required for SESSION line type")
                if (productSaleId != null) throw BadRequestResponse("productSaleId must be null for SESSION line type")
            }

            RemittanceLineType.PRODUCT_SALE -> {
                if (productSaleId == null) {
                    throw BadRequestResponse("productSaleId is required for PRODUCT_SALE line type")
                }
                if (sessionId != null) throw BadRequestResponse("sessionId must be null for PRODUCT_SALE line type")
            }
        }

        val line =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittanceId,
                id = id,
                type = type,
                sessionId = sessionId,
                productSaleId = productSaleId,
                amount = amount,
            )

        context.status(HttpStatus.CREATED)
        context.json(line.toResponse())
    }

    private fun handleRemoveLine(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val lineId = context.pathParamAsUuid("lineId")

        val line = RemittanceService.removeLine(callerId, remittanceId, lineId)
        context.json(line.toResponse())
    }

    private fun handleAddDayBreakdown(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val request = context.bodyAsClass<AddDayBreakdownRequest>()

        val id = uuidOrThrow(request.id, "day breakdown id")
        val branchDayId = uuidOrThrow(request.branchDayId, "branch day id")

        val breakdown =
            RemittanceService.addDayBreakdown(
                callerId = callerId,
                remittanceId = remittanceId,
                id = id,
                branchDayId = branchDayId,
            )

        context.status(HttpStatus.CREATED)
        context.json(breakdown.toResponse())
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

    @Suppress("ThrowsCount")
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

    @Suppress("ThrowsCount")
    private fun handleUpdateHeader(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val request = context.bodyAsClass<UpdateRemittanceHeaderRequest>()

        val header = parseHeader(request.type.name, request.method.name, request.dateRangeStart, request.dateRangeEnd)

        val remittance =
            RemittanceService.updateHeader(
                callerId = callerId,
                remittanceId = remittanceId,
                type = header.type,
                method = header.method,
                dateRangeStart = header.dateRangeStart,
                dateRangeEnd = header.dateRangeEnd,
                expectedVersion = request.expectedVersion,
            )

        context.json(remittance.toResponse())
    }

    private fun Remittance.toResponse(): RemittanceResponse =
        RemittanceResponse(
            id = id.toString(),
            type = type,
            status = status,
            branchId = branchId.toString(),
            method = method,
            submittedDate = submittedDate.toString(),
            submittedAt = submittedAt?.toString(),
            submittedBy = submittedBy.toString(),
            dateRangeStart = dateRangeStart.toString(),
            dateRangeEnd = dateRangeEnd.toString(),
            createdAt = createdAt.toString(),
            version = version,
        )

    private fun RemittanceWithNet.toListResponse(): RemittanceResponse =
        remittance.toResponse().copy(netIncome = netIncome?.toPlainString())

    private fun RemittanceLine.toResponse(): RemittanceLineResponse =
        RemittanceLineResponse(
            id = id.toString(),
            remittanceId = remittanceId.toString(),
            type = type,
            sessionId = sessionId?.toString(),
            productSaleId = productSaleId?.toString(),
            createdBy = createdBy.toString(),
            createdAt = createdAt.toString(),
            deletedBy = deletedBy?.toString(),
            deletedAt = deletedAt?.toString(),
            amount = amount.toPlainString(),
        )

    private fun RemittanceDayBreakdown.toResponse(): RemittanceDayBreakdownResponse =
        RemittanceDayBreakdownResponse(
            id = id.toString(),
            remittanceId = remittanceId.toString(),
            branchDayId = branchDayId.toString(),
        )

    private fun RemittanceDetail.toResponse(): RemittanceDetailResponse =
        RemittanceDetailResponse(
            id = remittance.id.toString(),
            type = remittance.type,
            status = remittance.status,
            branchId = remittance.branchId.toString(),
            method = remittance.method,
            submittedDate = remittance.submittedDate.toString(),
            submittedAt = remittance.submittedAt?.toString(),
            submittedBy = remittance.submittedBy.toString(),
            dateRangeStart = remittance.dateRangeStart.toString(),
            dateRangeEnd = remittance.dateRangeEnd.toString(),
            createdAt = remittance.createdAt.toString(),
            version = remittance.version,
            lines = lines.map { it.toResponse() },
            totalAmount = totalAmount.toPlainString(),
            dayBreakdowns = dayBreakdowns.map { it.toResponse() },
            snapshot = snapshot?.toResponse(),
        )

    private fun RemittanceFinancialSnapshot.toResponse(): RemittanceFinancialSnapshotResponse =
        RemittanceFinancialSnapshotResponse(
            remittanceId = remittanceId.toString(),
            grossIncome = grossIncome.toPlainString(),
            totalCompensation = totalCompensation.toPlainString(),
            totalExpenses = totalExpenses.toPlainString(),
            netIncome = netIncome.toPlainString(),
            snapshottedAt = snapshottedAt.toString(),
        )

    private fun RemittanceDrift.toResponse(): RemittanceDriftResponse =
        RemittanceDriftResponse(
            frozen = frozen.toResponse(),
            currentCompensation = currentCompensation.toPlainString(),
            currentExpenses = currentExpenses.toPlainString(),
            currentNet = currentNet.toPlainString(),
        )

    private fun RemittanceSubmissionResult.toSubmitResponse(): RemittanceSubmitResponse =
        RemittanceSubmitResponse(
            id = remittance.id.toString(),
            type = remittance.type,
            status = remittance.status,
            branchId = remittance.branchId.toString(),
            method = remittance.method,
            submittedDate = remittance.submittedDate.toString(),
            submittedAt = remittance.submittedAt?.toString(),
            submittedBy = remittance.submittedBy.toString(),
            dateRangeStart = remittance.dateRangeStart.toString(),
            dateRangeEnd = remittance.dateRangeEnd.toString(),
            createdAt = remittance.createdAt.toString(),
            version = remittance.version,
            grossIncome = grossIncome.toPlainString(),
            totalCompensation = totalCompensation.toPlainString(),
            totalExpenses = totalExpenses.toPlainString(),
            netIncome = netIncome.toPlainString(),
        )
}
