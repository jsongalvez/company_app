package com.companyb.companyapp.service.finance.commission

import com.companyb.companyapp.repository.AttendanceRepository
import com.companyb.companyapp.repository.CommissionManualInclusionRepository
import com.companyb.companyapp.repository.CommissionSplitRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.model.DayStatus
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

internal object CommissionEngine {
    private val logger = KotlinLogging.logger {}

    private const val COMMISSION_SCALE = 4

    fun splitCommission(
        commissionAmount: BigDecimal,
        quantity: Int,
        eligibleUserCount: Int,
    ): BigDecimal {
        val total = commissionAmount.multiply(BigDecimal.valueOf(quantity.toLong()))
        return total.divide(
            BigDecimal.valueOf(eligibleUserCount.toLong()),
            COMMISSION_SCALE,
            RoundingMode.HALF_UP,
        )
    }

    @Suppress("ReturnCount")
    fun recalculate(
        branchDayId: UUID,
        force: Boolean = false,
    ) {
        logger.info { "[COMMISSION-ENGINE] Recalculating commission for branchDay=$branchDayId force=$force" }

        val effectiveStatus = BranchDayService.getEffectiveStatus(branchDayId)
        if (effectiveStatus == null) {
            logger.warn { "[COMMISSION-ENGINE] Branch day $branchDayId not found, skipping" }
            return
        }
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
                val perUser = splitCommission(sale.commissionAmountAtTime, sale.quantity, eligibleUsers.size)

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

    fun manualRecalculate(branchDayId: UUID) {
        BranchDayService.requireBranchDayExists(branchDayId)
        recalculate(branchDayId, force = true)
    }
}
