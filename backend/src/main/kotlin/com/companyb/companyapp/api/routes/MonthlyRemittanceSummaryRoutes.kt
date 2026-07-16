package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.repository.model.MonthlyRemittanceSummary
import com.companyb.companyapp.service.MonthlyRemittanceSummaryService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import java.util.UUID

object MonthlyRemittanceSummaryRoutes {
    private const val MAX_MONTH = 12
    private const val MIN_MONTH = 1

    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.before("/api/branches/{branchId}/monthly-summary") { context ->
            val branchId = context.pathParamAsUuid("branchId")
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.VIEW_BRANCH_DATA,
            )
        }

        config.routes.get("/api/branches/{branchId}/monthly-summary") { context ->
            val branchId = context.pathParamAsUuid("branchId")

            val yearParam =
                context.queryParam("year")
                    ?: throw BadRequestResponse("year query param is required")
            val year =
                runCatching { yearParam.toInt() }
                    .getOrElse { throw BadRequestResponse("Invalid year format") }

            val monthParam =
                context.queryParam("month")
                    ?: throw BadRequestResponse("month query param is required")
            val month =
                runCatching { monthParam.toInt() }
                    .getOrElse { throw BadRequestResponse("Invalid month format") }
            if (month < MIN_MONTH || month > MAX_MONTH) {
                throw BadRequestResponse("month must be between 1 and 12")
            }

            val summary = MonthlyRemittanceSummaryService.getMonthlySummary(branchId, year, month)

            context.status(HttpStatus.OK)
            context.json(summary.toResponse())
        }
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
