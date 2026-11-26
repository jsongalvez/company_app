package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.Gate
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.get
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.session.SiteHubResponse
import com.companyb.companyapp.contracts.session.TodayScreenResponse
import com.companyb.companyapp.contracts.workforce.MemberAttendanceResponse
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.session.dashboard.DashboardRoutes.toResponse
import io.javalin.config.JavalinConfig
import io.javalin.http.BadRequestResponse
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.time.LocalDate
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCH_TODAY_SCREEN_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    queryParams = [
        OpenApiParam(
            name = "date",
            type = String::class,
            required = false,
            description = "A past day (YYYY-MM-DD) for that day's document; today when left out",
        ),
    ],
    operationId = "today_screen",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = TodayScreenResponse::class)]),
        OpenApiResponse(status = "400", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "403", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object TodayScreenRoutes {
    fun register(config: JavalinConfig) {
        config.get(
            ApiRoutes.BRANCH_TODAY_SCREEN_PATH,
            Gate.InHandler("TodayScreenService: per part of the screen"),
        ) { context ->
            val date = context.queryParam("date")?.let(::pastOrToday)
            context.json(
                response(TodayScreenService.get(context.callerUuid(), context.pathParamAsUuid("branchId"), date)),
            )
        }
    }

    private fun pastOrToday(raw: String): LocalDate {
        val date = runCatching { LocalDate.parse(raw) }.getOrElse { throw BadRequestResponse("Invalid date") }
        if (date.isAfter(BranchDayService.currentOperationalDate())) throw BadRequestResponse("date is after today")
        return date
    }

    internal fun response(screen: TodayScreenData) =
        TodayScreenResponse(
            dashboard = screen.dashboard?.toResponse(),
            dashboardRefusal = screen.dashboardRefusal,
            bookings = screen.bookings,
            roster =
                screen.roster?.map {
                    MemberAttendanceResponse(
                        it.assignmentId.toString(),
                        it.userId.toString(),
                        it.displayName,
                        it.slot,
                        it.present,
                        it.clockedInAt?.toString(),
                    )
                },
            earlierWaitingDays = screen.earlierWaitingDays?.map { it.toString() },
            siteHub = screen.siteHub?.let { SiteHubResponse(it.room, it.key, it.date.toString()) },
            date = screen.date.toString(),
        )
}
