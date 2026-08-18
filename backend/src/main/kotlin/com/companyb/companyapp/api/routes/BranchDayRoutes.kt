package com.companyb.companyapp.api.routes
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.dto.BranchDayTodayResponse
import com.companyb.companyapp.dto.BranchDayUserResponse
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.service.branchday.BranchDayService
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import io.javalin.openapi.HttpMethod
import io.javalin.openapi.OpenApi
import io.javalin.openapi.OpenApiParam
import io.javalin.openapi.OpenApiSecurity
import java.time.LocalDate
import java.util.UUID

@OpenApi(
    path = "/api/branch-days/{branchDayId}/users",
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchDayId", type = UUID::class, required = true)],
    operationId = "branch_day_users",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
@OpenApi(
    path = "/api/branches/{branchId}/today",
    methods = [HttpMethod.GET],
    pathParams = [OpenApiParam(name = "branchId", type = UUID::class, required = true)],
    operationId = "branch_today",
    security = [OpenApiSecurity(name = "BearerAuth")],
)
object BranchDayRoutes {
    private const val BRANCH_ID_PARAM = "branchId"
    private const val BRANCH_DAY_ID_PARAM = "branchDayId"

    fun register(config: JavalinConfig) {
        config.routes.before(ApiRoutes.BRANCH_TODAY_PATH) { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            // #158 — the day-status read accepts the relief grant: a BRANCH_DAY
            // `EDIT_BRANCH_DATA` holder reads today's status for their granted day.
            // The day resolves find-only; a missing day row means no day grant can
            // exist for it, and the plain branch gate governs.
            val todayDay = BranchDayService.findToday(branchId)
            if (todayDay != null) {
                CapabilityFilter.requireBranchOrBranchDayCapability(
                    context,
                    todayDay.id,
                    CapabilityCodes.EDIT_BRANCH_DATA,
                )
            } else {
                CapabilityFilter.requireBranchCapabilityForBranchId(
                    context,
                    branchId,
                    CapabilityCodes.EDIT_BRANCH_DATA,
                )
            }
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
                LocalDate.now(BranchDayService.manilaZone),
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
