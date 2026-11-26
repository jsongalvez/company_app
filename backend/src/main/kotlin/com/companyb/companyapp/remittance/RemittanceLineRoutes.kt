package com.companyb.companyapp.remittance

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.delete
import com.companyb.companyapp.api.post
import com.companyb.companyapp.api.routes.parsePositiveBigDecimal
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.api.routes.requireMoneyAmount
import com.companyb.companyapp.api.routes.uuidOrThrow
import com.companyb.companyapp.contracts.remittance.AddDayBreakdownRequest
import com.companyb.companyapp.contracts.remittance.CreateRemittanceLineRequest
import com.companyb.companyapp.contracts.remittance.RemittanceDayBreakdownResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
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
import java.util.UUID

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
internal object RemittanceLineRoutes {
    fun register(config: JavalinConfig) {
        config.post(ApiRoutes.REMITTANCE_LINES_PATH, remittanceGate, ::handleAddLine)
        config.delete(ApiRoutes.REMITTANCE_LINE_PATH, remittanceGate, ::handleRemoveLine)
        config.post(ApiRoutes.REMITTANCE_DAY_BREAKDOWNS_PATH, remittanceGate, ::handleAddDayBreakdown)
        config.delete(ApiRoutes.REMITTANCE_DAY_BREAKDOWN_PATH, remittanceGate, ::handleRemoveDayBreakdown)
    }

    private fun handleRemoveDayBreakdown(context: Context) {
        val callerId = context.callerUuid()
        val remittanceId = context.pathParamAsUuid("remittanceId")
        val breakdownId = context.pathParamAsUuid("breakdownId")

        val breakdown = RemittanceService.removeDayBreakdown(callerId, remittanceId, breakdownId)
        context.json(breakdown.toResponse())
    }

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
        requireMoneyAmount(amount, "amount")

        when (type) {
            RemittanceLineType.SESSION -> {
                requireSessionLinePayload(sessionId, productSaleId)
            }

            RemittanceLineType.PRODUCT_SALE -> {
                requireProductSaleLinePayload(sessionId, productSaleId)
            }
        }

        val line =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittanceId,
                new =
                    NewRemittanceLine(
                        id = id,
                        type = type,
                        sessionId = sessionId,
                        productSaleId = productSaleId,
                        amount = amount,
                    ),
            )

        context.status(HttpStatus.CREATED)
        context.json(line.toResponse())
    }

    private fun requireSessionLinePayload(
        sessionId: UUID?,
        productSaleId: UUID?,
    ) {
        if (sessionId == null) throw BadRequestResponse("sessionId is required for SESSION line type")
        if (productSaleId != null) throw BadRequestResponse("productSaleId must be null for SESSION line type")
    }

    private fun requireProductSaleLinePayload(
        sessionId: UUID?,
        productSaleId: UUID?,
    ) {
        if (productSaleId == null) {
            throw BadRequestResponse("productSaleId is required for PRODUCT_SALE line type")
        }
        if (sessionId != null) throw BadRequestResponse("sessionId must be null for PRODUCT_SALE line type")
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
}
