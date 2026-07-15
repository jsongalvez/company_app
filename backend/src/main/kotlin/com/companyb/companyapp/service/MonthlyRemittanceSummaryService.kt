package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.MonthlyRemittanceSummaryRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.MonthlyRemittanceSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import java.util.UUID

object MonthlyRemittanceSummaryService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount")
    fun getMonthlySummary(
        callerId: UUID,
        branchId: UUID,
        year: Int,
        month: Int,
    ): MonthlyRemittanceSummary {
        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
        )

        BranchRepository.findById(branchId)
            ?: throw NotFoundResponse("Branch not found")

        val summary =
            MonthlyRemittanceSummaryRepository.findByBranchYearMonth(branchId, year, month)
                ?: throw NotFoundResponse("No remittance data for this branch, year, and month")

        return summary
    }
}
