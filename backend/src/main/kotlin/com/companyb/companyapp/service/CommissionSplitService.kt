package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.CommissionSplitRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CommissionSplit
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object CommissionSplitService {
    private val logger = KotlinLogging.logger {}

    private const val VIEW_BRANCH_DATA = "VIEW_BRANCH_DATA"

    fun getByBranchDayId(
        callerId: UUID,
        branchDayId: UUID,
    ): List<CommissionSplit> {
        val branchDay =
            BranchDayRepository.findById(branchDayId)
                ?: throw NotFoundResponse("Branch day not found")

        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = VIEW_BRANCH_DATA,
                contextType = CapabilityContextType.BRANCH,
                contextId = branchDay.branchId,
            )
        if (!authorized) {
            logger.warn {
                "[COMMISSION-SPLIT] User $callerId lacks $VIEW_BRANCH_DATA" +
                    " capability for branch ${branchDay.branchId}"
            }
            throw ForbiddenResponse("VIEW_BRANCH_DATA capability required")
        }

        return CommissionSplitRepository.findByBranchDayId(branchDayId)
    }
}
