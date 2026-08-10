package com.companyb.companyapp.service

import com.companyb.companyapp.dto.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.DailySalesSummaryRepository
import com.companyb.companyapp.repository.DailySummaryBrowseCursor
import com.companyb.companyapp.repository.encodeDailySummaryCursor
import com.companyb.companyapp.repository.model.DailySalesSummary
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate
import java.util.UUID

object DailySalesSummaryService {
    private val logger = KotlinLogging.logger {}

    @Suppress("ThrowsCount")
    fun getDailySummary(
        branchId: UUID,
        date: LocalDate,
    ): DailySalesSummary {
        BranchRepository.findById(branchId)
            ?: throw NotFoundException("Branch not found")

        val summary =
            DailySalesSummaryRepository.findByBranchAndDate(branchId, date)
                ?: throw NotFoundException("No data for this branch and date")

        return summary
    }

    /**
     * Paged feed over `(date DESC, branch_day_id DESC)`. [limit] entries are
     * returned plus the [DailySalesSummaryBrowseResponse.nextCursor] to fetch
     * the next page; `null` cursor = no more pages.
     */
    fun browseDailySummaries(
        branchId: UUID,
        cursor: DailySummaryBrowseCursor?,
        limit: Int,
    ): DailySalesSummaryBrowseResponse {
        BranchRepository.findById(branchId)
            ?: throw NotFoundException("Branch not found")

        val fetched = DailySalesSummaryRepository.findPagedByBranch(branchId, cursor, limit + 1)
        val hasMore = fetched.size > limit
        val entries = if (hasMore) fetched.dropLast(1) else fetched
        val nextCursor =
            if (hasMore) {
                entries.lastOrNull()?.let {
                    encodeDailySummaryCursor(DailySummaryBrowseCursor(it.date, it.branchDayId))
                }
            } else {
                null
            }
        logger.info { "[DAILY-SUMMARY-BROWSE] Branch $branchId returned ${entries.size} summaries hasMore=$hasMore" }
        return DailySalesSummaryBrowseResponse(
            entries = entries.map { it.toResponse() },
            nextCursor = nextCursor,
        )
    }
}

fun DailySalesSummary.toResponse(): DailySalesSummaryResponse =
    DailySalesSummaryResponse(
        branchDayId = branchDayId.toString(),
        branchId = branchId.toString(),
        date = date.toString(),
        grossIncome = grossIncome.toPlainString(),
        totalCompensation = totalCompensation.toPlainString(),
        totalExpenses = totalExpenses.toPlainString(),
        netIncome = netIncome.toPlainString(),
        totalProductSales = totalProductSales.toPlainString(),
        totalCommission = totalCommission.toPlainString(),
    )
