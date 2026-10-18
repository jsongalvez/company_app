package com.companyb.companyapp.reporting

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.contracts.reporting.MyHistoryResponse
import com.companyb.companyapp.dto.ErrorResponse
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
import java.time.temporal.ChronoUnit

private const val MAX_RANGE_DAYS = 400L

@OpenApi(
    path = ApiRoutes.ME_HISTORY,
    methods = [HttpMethod.GET],
    queryParams = [
        OpenApiParam(name = "from", type = String::class, required = true),
        OpenApiParam(name = "to", type = String::class, required = true),
    ],
    operationId = "me_history",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = MyHistoryResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object MyHistoryRoutes {
    fun register(config: JavalinConfig) {
        // Only ever the caller's own rows, so signing in is the only gate.
        config.routes.get(ApiRoutes.ME_HISTORY) { context ->
            val from = date(context, "from")
            val to = date(context, "to")
            if (from.isAfter(to)) throw BadRequestResponse("from must be on or before to")
            if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
                throw BadRequestResponse("Pick at most $MAX_RANGE_DAYS days")
            }
            context.json(MyHistoryRepository.load(context.callerUuid(), from, to))
        }
    }

    private fun date(
        context: Context,
        name: String,
    ): LocalDate {
        val raw = context.queryParam(name) ?: throw BadRequestResponse("$name query param is required")
        return runCatching { LocalDate.parse(raw) }
            .getOrElse { throw BadRequestResponse("Invalid $name format (expected yyyy-MM-dd)") }
    }
}
