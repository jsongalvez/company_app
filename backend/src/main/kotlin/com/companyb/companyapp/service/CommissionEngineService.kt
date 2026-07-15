package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.AttendanceRepository
import com.companyb.companyapp.repository.BranchDayRepository
import com.companyb.companyapp.repository.CommissionManualInclusionRepository
import com.companyb.companyapp.repository.CommissionSplitRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.DayStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.NotFoundResponse
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

object CommissionEngineService {
    private val logger = KotlinLogging.logger {}

    private const val COMMISSION_SCALE = 4

    /**
     * Pure computation: splits total commission (amount x quantity) equally among
     * [eligibleUserCount] users. Extracted so it can be unit-tested and benchmarked
     * without a database.
     */
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

    fun manualRecalculate(
        callerId: UUID,
        branchDayId: UUID,
    ) {
        val branchDay =
            BranchDayRepository.findById(branchDayId)
                ?: throw NotFoundResponse("Branch day not found")

        CapabilityService.requireCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_PAST_DAY,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchDay.branchId,
            message = "EDIT_PAST_DAY capability required to manually recalculate commissions on PAST/REMITTED days",
        )

        recalculate(branchDayId, force = true)
    }
}
