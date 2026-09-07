package com.companyb.companyapp.reporting
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.routes.parseBrowseLimit
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.dto.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.dto.DailySalesSummaryResponse
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
import java.time.LocalDate
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCH_DAILY_SUMMARIES_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [
        OpenApiParam(
            name = "cursor",
            type = String::class,
            required = false,
        ), OpenApiParam(
            name = "limit",
            type = String::class,
            required = false,
        ), OpenApiParam(
            name = "from",
            type = String::class,
            required = false,
        ), OpenApiParam(name = "to", type = String::class, required = false),
    ],
    operationId = "daily_summaries",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = DailySalesSummaryBrowseResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_DAILY_SUMMARY_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [OpenApiParam(name = "date", type = String::class, required = true)],
    operationId = "daily_summary",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = DailySalesSummaryResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object DailySalesSummaryRoutes {
    @Suppress("ThrowsCount")
    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.BRANCH_DAILY_SUMMARY_PATH) { context ->
            val branchId = context.pathParamAsUuid("branchId")
            val date = parseRequiredDate(context)
            // #158 — the single-day read accepts the relief grant: a BRANCH_DAY
            // `EDIT_BRANCH_DATA` holder reads the granted day's summary (the read mirror
            // of the #157 day-scoped write surface). The day resolves find-only — a
            // missing day row means no day grant can exist for it, and the branch/global
            // leg alone governs. The browse (`/daily-summaries`) stays VIEW_BRANCH_DATA-only:
            // a multi-day list cannot be authorized by a single-day grant.
            val branchDayId =
                BranchDayService.findByBranchAndDate(branchId, date)?.id
            CapabilityFilter.requireBranchOrGlobalOrBranchDayCapabilityForBranchId(
                context,
                branchId,
                branchDayId,
            )
        }

        config.routes.before(ApiRoutes.BRANCH_DAILY_SUMMARIES_PATH) { context ->
            val branchId = context.pathParamAsUuid("branchId")
            CapabilityFilter.requireBranchOrGlobalCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.VIEW_BRANCH_DATA,
            )
        }

        config.routes.get(ApiRoutes.BRANCH_DAILY_SUMMARY_PATH) { context ->
            val branchId = context.pathParamAsUuid("branchId")
            val date = parseRequiredDate(context)

            val summary = DailySalesSummaryService.getDailySummary(branchId, date)

            context.status(HttpStatus.OK)
            context.json(summary.toResponse())
        }

        config.routes.get(ApiRoutes.BRANCH_DAILY_SUMMARIES_PATH) { context ->
            val branchId = context.pathParamAsUuid("branchId")
            val cursor = parseCursor(context.queryParam("cursor"))
            val limit = parseBrowseLimit(context.queryParam("limit"))
            val from = parseOptionalDate(context.queryParam("from"), "from")
            val to = parseOptionalDate(context.queryParam("to"), "to")
            if (from != null && to != null && from.isAfter(to)) {
                throw BadRequestResponse("from must be on or before to")
            }

            val response =
                DailySalesSummaryService.browseDailySummaries(branchId, cursor, limit, from, to)

            context.status(HttpStatus.OK)
            context.json(response)
        }
    }

    private fun parseRequiredDate(context: io.javalin.http.Context): LocalDate {
        val dateParam =
            context.queryParam("date")
                ?: throw BadRequestResponse("date query param is required")
        return runCatching { LocalDate.parse(dateParam) }
            .getOrElse { throw BadRequestResponse("Invalid date format (expected yyyy-MM-dd)") }
    }

    private fun parseOptionalDate(
        raw: String?,
        paramName: String,
    ): LocalDate? {
        if (raw == null) return null
        return runCatching { LocalDate.parse(raw) }
            .getOrElse { throw BadRequestResponse("Invalid $paramName format (expected yyyy-MM-dd)") }
    }

    private fun parseCursor(raw: String?): DailySummaryBrowseCursor? {
        if (raw == null) return null
        return runCatching { decodeDailySummaryCursor(raw) }
            .getOrElse { throw BadRequestResponse("Invalid cursor") }
    }
}
