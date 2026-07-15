package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.CommissionSplitRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.CommissionSplit
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import java.util.UUID

object CommissionSplitService {
    private val logger = KotlinLogging.logger {}

    fun getByBranchDayId(
        callerId: UUID,
        branchDayId: UUID,
    ): List<CommissionSplit> {
        val branchDay =
            BranchDayRepository.findById(branchDayId)
                ?: throw NotFoundResponse("Branch day not found")

        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchDay.branchId,
        )

        return CommissionSplitRepository.findByBranchDayId(branchDayId)
    }
}
