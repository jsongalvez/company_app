package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.MonthlyRemittanceSummaryRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.MonthlyRemittanceSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.util.UUID

object MonthlyRemittanceSummaryService {
    private val logger = KotlinLogging.logger {}

    private const val VIEW_BRANCH_DATA = "VIEW_BRANCH_DATA"

    @Suppress("ThrowsCount")
    fun getMonthlySummary(
        callerId: UUID,
        branchId: UUID,
        year: Int,
        month: Int,
    ): MonthlyRemittanceSummary {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = VIEW_BRANCH_DATA,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[MONTHLY-SUMMARY] User $callerId lacks $VIEW_BRANCH_DATA capability" }
            throw ForbiddenResponse("VIEW_BRANCH_DATA capability required")
        }

        BranchRepository.findById(branchId)
            ?: throw NotFoundResponse("Branch not found")

        val summary =
            MonthlyRemittanceSummaryRepository.findByBranchYearMonth(branchId, year, month)
                ?: throw NotFoundResponse("No remittance data for this branch, year, and month")

        return summary
    }
}
