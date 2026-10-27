package com.companyb.companyapp.session.dashboard

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.callerUuid
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.contracts.session.TodayScreenResponse
import com.companyb.companyapp.contracts.workforce.MemberAttendanceResponse
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.session.dashboard.DashboardRoutes.toResponse
import io.javalin.config.JavalinConfig
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCH_TODAY_SCREEN_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "today_screen",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = TodayScreenResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object TodayScreenRoutes {
    fun register(config: JavalinConfig) {
        config.routes.get(ApiRoutes.BRANCH_TODAY_SCREEN_PATH) { context ->
            context.json(response(TodayScreenService.get(context.callerUuid(), context.pathParamAsUuid("branchId"))))
        }
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
                    )
                },
        )
}
