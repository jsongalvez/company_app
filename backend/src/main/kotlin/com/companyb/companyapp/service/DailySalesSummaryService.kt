package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.DailySalesSummaryRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.DailySalesSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.time.LocalDate
import java.util.UUID

object DailySalesSummaryService {
    private val logger = KotlinLogging.logger {}

    private const val VIEW_BRANCH_DATA = "VIEW_BRANCH_DATA"

    @Suppress("ThrowsCount")
    fun getDailySummary(
        callerId: UUID,
        branchId: UUID,
        date: LocalDate,
    ): DailySalesSummary {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = VIEW_BRANCH_DATA,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[DAILY-SUMMARY] User $callerId lacks $VIEW_BRANCH_DATA capability" }
            throw ForbiddenResponse("VIEW_BRANCH_DATA capability required")
        }

        BranchRepository.findById(branchId)
            ?: throw NotFoundResponse("Branch not found")

        val summary =
            DailySalesSummaryRepository.findByBranchAndDate(branchId, date)
                ?: throw NotFoundResponse("No data for this branch and date")

        return summary
    }
}
