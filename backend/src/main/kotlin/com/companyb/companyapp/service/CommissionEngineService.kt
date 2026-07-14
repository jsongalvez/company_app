package com.companyb.companyapp.service

import com.companyb.companyapp.repository.AttendanceRepository
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.CommissionManualInclusionRepository
import com.companyb.companyapp.repository.CommissionSplitRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.model.DayStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

object CommissionEngineService {
    private val logger = KotlinLogging.logger {}

    private const val COMMISSION_SCALE = 4

    @Suppress("ReturnCount")
    fun recalculate(
        branchDayId: UUID,
        force: Boolean = false,
    ) {
        logger.info { "[COMMISSION-ENGINE] Recalculating commission for branchDay=$branchDayId force=$force" }

        val branchDay = BranchDayRepository.findById(branchDayId)
        if (branchDay == null) {
            logger.warn { "[COMMISSION-ENGINE] Branch day $branchDayId not found, skipping" }
            return
        }

        val effectiveStatus =
            BranchDayService.evaluateStatus(
                branchDay.status,
                branchDay.date,
                java.time.LocalDate.now(BranchDayService.manilaZone),
            )
        if (!force && effectiveStatus != DayStatus.OPEN) {
            logger.info {
                "[COMMISSION-ENGINE] Branch day $branchDayId is $effectiveStatus, skipping automatic recalculation"
            }
            return
        }

        val sales = ProductSaleRepository.findNonVoidedSalesByBranchDay(branchDayId)
        if (sales.isEmpty()) {
            logger.info { "[COMMISSION-ENGINE] No non-voided sales for branchDay=$branchDayId" }
            CommissionSplitRepository.replaceForBranchDay(branchDayId, emptyMap())
            return
        }

        val accumulatedTotals = mutableMapOf<UUID, BigDecimal>()

        for (sale in sales) {
            val eligibleUsers = mutableSetOf<UUID>()

            val clockedInUsers = AttendanceRepository.findUsersClockedInAt(branchDayId, sale.soldAt)
            eligibleUsers.addAll(clockedInUsers)

            val inclusions = CommissionManualInclusionRepository.findByProductSaleId(sale.id)
            for (inclusion in inclusions) {
                if (inclusion.isIncluded) {
                    eligibleUsers.add(inclusion.userId)
                } else {
                    eligibleUsers.remove(inclusion.userId)
                }
            }

            if (eligibleUsers.isNotEmpty()) {
                val totalCommission =
                    sale.commissionAmountAtTime.multiply(BigDecimal.valueOf(sale.quantity.toLong()))
                val perUser =
                    totalCommission.divide(
                        BigDecimal.valueOf(eligibleUsers.size.toLong()),
                        COMMISSION_SCALE,
                        RoundingMode.HALF_UP,
                    )

                for (userId in eligibleUsers) {
                    accumulatedTotals.merge(userId, perUser, BigDecimal::add)
                }
            }
        }

        CommissionSplitRepository.replaceForBranchDay(branchDayId, accumulatedTotals)

        logger.info {
            "[COMMISSION-ENGINE] Recalculated commission for branchDay=$branchDayId: " +
                "${accumulatedTotals.size} users, ${sales.size} sales"
        }
    }
}
