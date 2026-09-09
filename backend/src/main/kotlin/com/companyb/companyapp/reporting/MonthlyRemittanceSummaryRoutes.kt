package com.companyb.companyapp.reporting
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.reporting.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.dto.ErrorResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCH_MONTHLY_SUMMARY_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [
        OpenApiParam(
            name = "year",
            type = Int::class,
            required = true,
        ), OpenApiParam(name = "month", type = Int::class, required = true),
    ],
    operationId = "monthly_summary",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = MonthlyRemittanceSummaryResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object MonthlyRemittanceSummaryRoutes {
    private const val MAX_MONTH = 12
    private const val MIN_MONTH = 1

    fun register(config: JavalinConfig) {
        // #744 — 404 precedence for an unknown branch before the capability gate (#730/#732 precedent).
        config.routes.before(ApiRoutes.BRANCH_MONTHLY_SUMMARY_PATH) { context ->
            val branchId = context.pathParamAsUuid("branchId")
            BranchService.findById(branchId)
            CapabilityFilter.requireBranchOrGlobalCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.VIEW_BRANCH_DATA,
            )
        }

        config.routes.get(ApiRoutes.BRANCH_MONTHLY_SUMMARY_PATH) { context ->
            val branchId = context.pathParamAsUuid("branchId")

            val year = parseRequiredInt(context, "year")
            val month = parseRequiredInt(context, "month")
            if (month < MIN_MONTH || month > MAX_MONTH) {
                throw BadRequestResponse("month must be between 1 and 12")
            }

            val summary = MonthlyRemittanceSummaryService.getMonthlySummary(branchId, year, month)

            context.status(HttpStatus.OK)
            context.json(summary.toResponse())
        }
    }

    private fun parseRequiredInt(
        context: io.javalin.http.Context,
        paramName: String,
    ): Int {
        val param =
            context.queryParam(paramName)
                ?: throw BadRequestResponse("$paramName query param is required")
        return runCatching { param.toInt() }
            .getOrElse { throw BadRequestResponse("Invalid $paramName format") }
    }

    private fun MonthlyRemittanceSummary.toResponse(): MonthlyRemittanceSummaryResponse =
        MonthlyRemittanceSummaryResponse(
            branchId = branchId.toString(),
            year = year,
            month = month,
            totalRemittances = totalRemittances,
            sessionCount = sessionCount,
            productCount = productCount,
            grossIncome = grossIncome.toPlainString(),
            totalCompensation = totalCompensation.toPlainString(),
            totalExpenses = totalExpenses.toPlainString(),
            netIncome = netIncome.toPlainString(),
        )
}
