package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.dto.RemittanceDayPickerEntryResponse
import com.companyb.companyapp.dto.RemittanceProductSalePickerEntryResponse
import com.companyb.companyapp.dto.RemittanceSessionPickerEntryResponse
import com.companyb.companyapp.service.finance.remittance.RemittanceDayPickerEntry
import com.companyb.companyapp.service.finance.remittance.RemittanceProductSalePickerEntry
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.service.finance.remittance.RemittanceSessionPickerEntry
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.time.LocalDate
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCH_REMITTANCE_DAYS_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [
        OpenApiParam(name = "from", type = String::class, required = true),
        OpenApiParam(name = "to", type = String::class, required = true),
    ],
    operationId = "branch_remittance_days",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(
            status = "200",
            content = [OpenApiContent(from = Array<RemittanceDayPickerEntryResponse>::class)],
        ),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_REMITTANCE_PRODUCT_SALES_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [
        OpenApiParam(name = "from", type = String::class, required = true),
        OpenApiParam(name = "to", type = String::class, required = true),
    ],
    operationId = "branch_remittance_product_sales",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(
            status = "200",
            content = [OpenApiContent(from = Array<RemittanceProductSalePickerEntryResponse>::class)],
        ),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_REMITTANCE_SESSIONS_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [
        OpenApiParam(name = "from", type = String::class, required = true),
        OpenApiParam(name = "to", type = String::class, required = true),
    ],
    operationId = "branch_remittance_sessions",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(
            status = "200",
            content = [OpenApiContent(from = Array<RemittanceSessionPickerEntryResponse>::class)],
        ),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object RemittancePickerRoutes {
    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.BRANCH_REMITTANCE_SESSIONS_PATH) { context ->
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                context.pathParamAsUuid("branchId"),
                CapabilityCodes.SUBMIT_REMITTANCE,
            )
        }

        config.routes.before(ApiRoutes.BRANCH_REMITTANCE_PRODUCT_SALES_PATH) { context ->
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                context.pathParamAsUuid("branchId"),
                CapabilityCodes.SUBMIT_REMITTANCE,
            )
        }

        config.routes.before(ApiRoutes.BRANCH_REMITTANCE_DAYS_PATH) { context ->
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                context.pathParamAsUuid("branchId"),
                CapabilityCodes.SUBMIT_REMITTANCE,
            )
        }

        config.routes.get(ApiRoutes.BRANCH_REMITTANCE_SESSIONS_PATH, ::handleListSessionsInRange)
        config.routes.get(ApiRoutes.BRANCH_REMITTANCE_PRODUCT_SALES_PATH, ::handleListProductSalesInRange)
        config.routes.get(ApiRoutes.BRANCH_REMITTANCE_DAYS_PATH, ::handleListDaysInRange)
    }

    private fun handleListSessionsInRange(context: Context) {
        val branchId = context.pathParamAsUuid("branchId")
        val (from, to) = parseRange(context)

        context.json(RemittanceService.findSessionsInRange(branchId, from, to).map { it.toResponse() })
    }

    private fun handleListProductSalesInRange(context: Context) {
        val branchId = context.pathParamAsUuid("branchId")
        val (from, to) = parseRange(context)

        context.json(RemittanceService.findProductSalesInRange(branchId, from, to).map { it.toResponse() })
    }

    private fun handleListDaysInRange(context: Context) {
        val branchId = context.pathParamAsUuid("branchId")
        val (from, to) = parseRange(context)

        context.json(RemittanceService.findBranchDaysInRange(branchId, from, to).map { it.toResponse() })
    }

    private fun parseRange(context: Context): Pair<LocalDate, LocalDate> {
        val from = parseDateParam(context, "from")
        val to = parseDateParam(context, "to")
        if (to.isBefore(from)) throw BadRequestResponse("to must not be before from")
        return from to to
    }

    private fun parseDateParam(
        context: Context,
        name: String,
    ): LocalDate {
        val raw = context.queryParam(name) ?: throw BadRequestResponse("$name is required")
        return runCatching { LocalDate.parse(raw) }
            .getOrElse { throw BadRequestResponse("Invalid $name") }
    }

    private fun RemittanceSessionPickerEntry.toResponse(): RemittanceSessionPickerEntryResponse =
        RemittanceSessionPickerEntryResponse(
            id = id.toString(),
            clientName = clientName,
            bookedAt = bookedAt?.toString(),
            sessionStatus = sessionStatus,
            finalPrice = finalPrice.toPlainString(),
        )

    private fun RemittanceProductSalePickerEntry.toResponse(): RemittanceProductSalePickerEntryResponse =
        RemittanceProductSalePickerEntryResponse(
            id = id.toString(),
            productName = productName,
            quantity = quantity,
            totalAmountAtTime = totalAmountAtTime.toPlainString(),
            soldAt = soldAt.toString(),
        )

    private fun RemittanceDayPickerEntry.toResponse(): RemittanceDayPickerEntryResponse =
        RemittanceDayPickerEntryResponse(
            id = id.toString(),
            date = date.toString(),
            status = status,
        )
}
