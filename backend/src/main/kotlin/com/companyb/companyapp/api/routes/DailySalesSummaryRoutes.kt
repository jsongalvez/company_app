package com.companyb.companyapp.api.routes

import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.repository.model.DailySalesSummary
import com.companyb.companyapp.service.DailySalesSummaryService
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import java.time.LocalDate
import java.util.UUID

object DailySalesSummaryRoutes {
    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.get("/api/branches/{branchId}/daily-summary") { context ->
            val callerId = UUID.fromString(context.attribute<String>("userId"))
            val branchId =
                runCatching { UUID.fromString(context.pathParam("branchId")) }
                    .getOrElse { throw BadRequestResponse("Invalid branch id") }
            val dateParam =
                context.queryParam("date")
                    ?: throw BadRequestResponse("date query param is required")
            val date =
                runCatching { LocalDate.parse(dateParam) }
                    .getOrElse { throw BadRequestResponse("Invalid date format (expected yyyy-MM-dd)") }

            val summary = DailySalesSummaryService.getDailySummary(callerId, branchId, date)

            context.status(HttpStatus.OK)
            context.json(summary.toResponse())
        }
    }

    private fun DailySalesSummary.toResponse(): DailySalesSummaryResponse =
        DailySalesSummaryResponse(
            branchDayId = branchDayId.toString(),
            branchId = branchId.toString(),
            date = date.toString(),
            grossIncome = grossIncome.toPlainString(),
            totalCompensation = totalCompensation.toPlainString(),
            totalExpenses = totalExpenses.toPlainString(),
            netIncome = netIncome.toPlainString(),
            totalProductSales = totalProductSales.toPlainString(),
            totalCommission = totalCommission.toPlainString(),
        )
}
