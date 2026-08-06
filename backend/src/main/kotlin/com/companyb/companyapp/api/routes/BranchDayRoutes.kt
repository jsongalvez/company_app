package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.middleware.CapabilityFilter
import com.companyb.companyapp.api.routes.pathParamAsUuid
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.BranchDayTodayResponse
import com.companyb.companyapp.service.branchday.BranchDayService
import io.javalin.config.JavalinConfig
import io.javalin.http.Context
import java.time.LocalDate

object BranchDayRoutes {
    private const val BRANCH_ID_PARAM = "branchId"

    fun register(config: JavalinConfig) {
        config.routes.before("/api/branches/{$BRANCH_ID_PARAM}/today") { context ->
            val branchId = context.pathParamAsUuid(BRANCH_ID_PARAM)
            CapabilityFilter.requireBranchCapabilityForBranchId(
                context,
                branchId,
                CapabilityCodes.EDIT_BRANCH_DATA,
            )
        }

        config.routes.get("/api/branches/{$BRANCH_ID_PARAM}/today", ::handleGetToday)
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
                status = effectiveStatus.name,
            ),
        )
    }
}
