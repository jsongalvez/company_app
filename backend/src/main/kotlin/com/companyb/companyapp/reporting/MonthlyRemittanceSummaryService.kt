package com.companyb.companyapp.reporting

import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.exception.NotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

object MonthlyRemittanceSummaryService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount")
    fun getMonthlySummary(
        branchId: UUID,
        year: Int,
        month: Int,
    ): MonthlyRemittanceSummary {
        BranchService.findById(branchId)

        val summary =
            MonthlyRemittanceSummaryRepository.findByBranchYearMonth(branchId, year, month)
                ?: throw NotFoundException("No remittance data for this branch, year, and month")

        return summary
    }
}
