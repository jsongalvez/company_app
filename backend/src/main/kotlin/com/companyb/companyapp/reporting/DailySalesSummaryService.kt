package com.companyb.companyapp.reporting

import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryBrowseResponse
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

private val ZERO_MONEY: BigDecimal = BigDecimal.ZERO.setScale(2)

object DailySalesSummaryService {
    private val logger = KotlinLogging.logger {}

    fun getDailySummary(
        branchId: UUID,
        date: LocalDate,
    ): DailySalesSummary {
        BranchService.findById(branchId)

        return DailySalesSummaryRepository.findByBranchAndDate(branchId, date)
            ?: DailySalesSummary(
                branchDayId = null,
                branchId = branchId,
                date = date,
                grossIncome = ZERO_MONEY,
                totalCompensation = ZERO_MONEY,
                totalExpenses = ZERO_MONEY,
                netIncome = ZERO_MONEY,
                totalProductSales = ZERO_MONEY,
            )
    }

    /**
     * Paged feed over `(date DESC, branch_day_id DESC)`. [limit] entries are
     * returned plus the [DailySalesSummaryBrowseResponse.nextCursor] to fetch
     * the next page; `null` cursor = no more pages. [from]/[to] bound the
     * window inclusively (null = unbounded) — the feed modes.
     */
    fun browseDailySummaries(
        branchId: UUID,
        cursor: DailySummaryBrowseCursor?,
        limit: Int,
        from: LocalDate? = null,
        to: LocalDate? = null,
    ): DailySalesSummaryBrowseResponse {
        BranchService.findById(branchId)

        val fetched = DailySalesSummaryRepository.findPagedByBranch(branchId, cursor, limit + 1, from, to)
        val hasMore = fetched.size > limit
        val entries = if (hasMore) fetched.dropLast(1) else fetched
        val nextCursor =
            if (hasMore) {
                entries.lastOrNull()?.let {
                    encodeDailySummaryCursor(DailySummaryBrowseCursor(it.date, checkNotNull(it.branchDayId)))
                }
            } else {
                null
            }
        logger.info {
            "[DAILY-SUMMARY-BROWSE] Branch $branchId window=${from ?: "*"}..${to ?: "*"}" +
                " returned ${entries.size} summaries hasMore=$hasMore"
        }
        return DailySalesSummaryBrowseResponse(
            entries = entries.map { it.toResponse() },
            nextCursor = nextCursor,
        )
    }
}

fun DailySalesSummary.toResponse(): DailySalesSummaryResponse =
    DailySalesSummaryResponse(
        branchDayId = branchDayId?.toString(),
        branchId = branchId.toString(),
        date = date.toString(),
        grossIncome = grossIncome.toPlainString(),
        totalCompensation = totalCompensation.toPlainString(),
        totalExpenses = totalExpenses.toPlainString(),
        netIncome = netIncome.toPlainString(),
        totalProductSales = totalProductSales.toPlainString(),
    )
