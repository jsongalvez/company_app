package com.companyb.companyapp.branchday
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.authorization.CapabilityFilter
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.dto.BranchDayTodayResponse
import com.companyb.companyapp.dto.BranchDayUserResponse
import com.companyb.companyapp.dto.ErrorResponse
import com.companyb.companyapp.service.attendance.AttendanceService
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiContent
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiResponse
import io.javalin.openapi.OpenApiSecurity
import java.util.UUID

@OpenApi(
    path = ApiRoutes.BRANCH_DAY_USERS_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchDayId", type = UUID::class, required = true)],
    operationId = "branch_day_users",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = Array<BranchDayUserResponse>::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
@OpenApi(
    path = ApiRoutes.BRANCH_TODAY_PATH,
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_today",
    security = [OpenApiSecurity(name = "BearerAuth")],
    responses = [
        OpenApiResponse(status = "200", content = [OpenApiContent(from = BranchDayTodayResponse::class)]),
        OpenApiResponse(status = "401", content = [OpenApiContent(from = ErrorResponse::class)]),
        OpenApiResponse(status = "404", content = [OpenApiContent(from = ErrorResponse::class)]),
    ],
)
object BranchDayRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val BRANCH_DAY_ID_PARAM = "branchDayId"

    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.BRANCH_TODAY_PATH) { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            // #158 via the shared today gate (#452): a BRANCH_DAY relief grant for today
            // reads today's status for its granted day.
            CapabilityFilter.requireBranchOrDayForBranch(context, branchId)
        }

        config.routes.before(ApiRoutes.BRANCH_DAY_USERS_PATH) { context ->
            val branchDayId = context.pathParamAsUuid(BRANCH_DAY_ID_PARAM)
            CapabilityFilter.requireBranchCapability(
                context,
                branchDayId,
                CapabilityCodes.ASSIGN_COMPENSATION,
            )
        }

        config.routes.get(ApiRoutes.BRANCH_TODAY_PATH, ::handleGetToday)

        config.routes.get(ApiRoutes.BRANCH_DAY_USERS_PATH, ::handleGetUsers)
    }

    private fun handleGetToday(context: Context) {
        val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
        val branchDay = BranchDayService.getToday(branchId)
        val effectiveStatus =
            BranchDayService.evaluateStatus(
                branchDay.status,
                branchDay.date,
                BranchDayService.currentOperationalDate(),
            )
        context.json(
            BranchDayTodayResponse(
                branchDayId = branchDay.id.toString(),
                status = DayStatus.valueOf(effectiveStatus.name),
            ),
        )
    }

    private fun handleGetUsers(context: Context) {
        val branchDayId = context.pathParamAsUuid(BRANCH_DAY_ID_PARAM)
        val users = AttendanceService.findUsersByBranchDayId(branchDayId)
        context.json(
            users.map { BranchDayUserResponse(userId = it.userId.toString(), displayName = it.displayName) },
        )
    }
}
