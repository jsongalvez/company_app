package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.DailySalesSummaryRepository
import com.companyb.companyapp.repository.model.DailySalesSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import java.time.LocalDate
import java.util.UUID

object DailySalesSummaryService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount", "UnusedParameter")
    fun getDailySummary(
        callerId: UUID,
        branchId: UUID,
        date: LocalDate,
    ): DailySalesSummary {
        BranchRepository.findById(branchId)
            ?: throw NotFoundResponse("Branch not found")

        val summary =
            DailySalesSummaryRepository.findByBranchAndDate(branchId, date)
                ?: throw NotFoundResponse("No data for this branch and date")

        return summary
    }
}
