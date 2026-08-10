package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.DailySummaryBrowseCursor
import com.companyb.companyapp.repository.decodeDailySummaryCursor
import com.companyb.companyapp.service.DailySalesSummaryService
import com.companyb.companyapp.service.toResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import java.time.LocalDate

object DailySalesSummaryRoutes {
    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.before("/api/branches/{branchId}/daily-summary") { context ->
            val branchId = context.pathParamAsUuid("branchId")
            CapabilityFilter.requireBranchOrGlobalCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.VIEW_BRANCH_DATA,
            )
        }

        config.routes.before("/api/branches/{branchId}/daily-summaries") { context ->
            val branchId = context.pathParamAsUuid("branchId")
            CapabilityFilter.requireBranchOrGlobalCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.VIEW_BRANCH_DATA,
            )
        }

        config.routes.get("/api/branches/{branchId}/daily-summary") { context ->
            val branchId = context.pathParamAsUuid("branchId")
            val dateParam =
                context.queryParam("date")
                    ?: throw BadRequestResponse("date query param is required")
            val date =
                runCatching { LocalDate.parse(dateParam) }
                    .getOrElse { throw BadRequestResponse("Invalid date format (expected yyyy-MM-dd)") }

            val summary = DailySalesSummaryService.getDailySummary(branchId, date)

            context.status(HttpStatus.OK)
            context.json(summary.toResponse())
        }

        config.routes.get("/api/branches/{branchId}/daily-summaries") { context ->
            val branchId = context.pathParamAsUuid("branchId")
            val cursor = parseCursor(context.queryParam("cursor"))
            val limit = parseBrowseLimit(context.queryParam("limit"))

            val response = DailySalesSummaryService.browseDailySummaries(branchId, cursor, limit)

            context.status(HttpStatus.OK)
            context.json(response)
        }
    }

    private fun parseCursor(raw: String?): DailySummaryBrowseCursor? {
        if (raw == null) return null
        return runCatching { decodeDailySummaryCursor(raw) }
            .getOrElse { throw BadRequestResponse("Invalid cursor") }
    }
}
