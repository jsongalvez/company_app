package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.DailySalesSummaryRepository
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
}
