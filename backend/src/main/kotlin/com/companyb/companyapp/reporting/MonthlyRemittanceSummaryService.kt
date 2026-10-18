package com.companyb.companyapp.reporting

import com.companyb.companyapp.branch.BranchService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.util.UUID

object MonthlyRemittanceSummaryService {
    private val logger = KotlinLogging.logger {}

    fun getMonthlySummary(
        branchId: UUID,
        year: Int,
        month: Int,
    ): MonthlyRemittanceSummary {
        BranchService.findById(branchId)

        // A month with nothing sent yet is a normal state, not an error: answer with zeros.
        return MonthlyRemittanceSummaryRepository.findByBranchYearMonth(branchId, year, month)
            ?: MonthlyRemittanceSummary(
                branchId = branchId,
                year = year,
                month = month,
                totalRemittances = 0,
                sessionCount = 0,
                productCount = 0,
                grossIncome = BigDecimal.ZERO,
                totalCompensation = BigDecimal.ZERO,
                totalExpenses = BigDecimal.ZERO,
                netIncome = BigDecimal.ZERO,
            )
    }
}
