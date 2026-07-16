package com.companyb.companyapp.service

import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.MonthlyRemittanceSummaryRepository
import com.companyb.companyapp.repository.model.MonthlyRemittanceSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import java.util.UUID

object MonthlyRemittanceSummaryService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount")
    fun getMonthlySummary(
        branchId: UUID,
        year: Int,
        month: Int,
    ): MonthlyRemittanceSummary {
        BranchRepository.findById(branchId)
            ?: throw NotFoundResponse("Branch not found")

        val summary =
            MonthlyRemittanceSummaryRepository.findByBranchYearMonth(branchId, year, month)
                ?: throw NotFoundResponse("No remittance data for this branch, year, and month")

        return summary
    }
}
