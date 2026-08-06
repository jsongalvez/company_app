package com.companyb.companyapp.service.finance.commission

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.CommissionManualInclusionRepository
import com.companyb.companyapp.repository.CommissionSplitRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.model.CommissionManualInclusion
import com.companyb.companyapp.repository.model.CommissionManualInclusionTable
import com.companyb.companyapp.repository.model.CommissionManualInclusionUpsertParams
import com.companyb.companyapp.repository.model.CommissionSplit
import com.companyb.companyapp.repository.model.DayStatus
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

object CommissionService {
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

    fun getByBranchDayId(branchDayId: UUID): List<CommissionSplit> {
        BranchDayService.requireBranchDayExists(branchDayId)
        return CommissionSplitRepository.findByBranchDayId(branchDayId)
    }

    @Suppress("ThrowsCount", "LongParameterList")
    fun createManualInclusion(
        callerId: UUID,
        id: UUID,
        productSaleId: UUID,
        userId: UUID,
        isIncluded: Boolean,
        reason: String?,
    ): CommissionManualInclusion {
        val sale =
            ProductSaleRepository.findById(productSaleId)
                ?: throw NotFoundException("Product sale not found")

        val branchId = BranchDayService.requireBranchDayExists(sale.branchDayId).branchId

        val result =
            CommissionManualInclusionRepository.upsert(
                CommissionManualInclusionUpsertParams(
                    id = id,
                    productSaleId = productSaleId,
                    userId = userId,
                    isIncluded = isIncluded,
                    reason = reason,
                    assignedBy = callerId,
                ),
            ) { existing, updated ->
                if (existing == null) {
                    AuditLogRepository.recordInsert(
                        tableName = CommissionManualInclusionTable.tableName,
                        recordId = updated.id,
                        changedBy = callerId,
                        branchId = branchId,
                        fields = CommissionManualInclusionTable.auditFields(updated),
                    )
                } else {
                    AuditLogRepository.recordUpdate(
                        tableName = CommissionManualInclusionTable.tableName,
                        recordId = updated.id,
                        before = existing,
                        after = updated,
                        changedBy = callerId,
                        branchId = branchId,
                        auditFields = CommissionManualInclusionTable::auditFields,
                    )
                }
            }

        recalculate(sale.branchDayId)

        logger.info {
            "[COMMISSION-INCLUSION] Created inclusion ${result.id} for productSale=$productSaleId " +
                "userId=$userId isIncluded=$isIncluded"
        }
        return result
    }

    @Suppress("ReturnCount")
    fun recalculate(
        branchDayId: UUID,
        force: Boolean = false,
    ) {
        logger.info { "[COMMISSION-SERVICE] Recalculating commission for branchDay=$branchDayId force=$force" }

        val effectiveStatus = BranchDayService.getEffectiveStatus(branchDayId)
        if (effectiveStatus == null) {
            logger.warn { "[COMMISSION-SERVICE] Branch day $branchDayId not found, skipping" }
            return
        }
        if (!force && effectiveStatus != DayStatus.OPEN) {
            logger.info {
                "[COMMISSION-SERVICE] Branch day $branchDayId is $effectiveStatus, skipping automatic recalculation"
            }
            return
        }

        val sales = ProductSaleRepository.findNonVoidedSalesByBranchDay(branchDayId)
        if (sales.isEmpty()) {
            logger.info { "[COMMISSION-SERVICE] No non-voided sales for branchDay=$branchDayId" }
            CommissionSplitRepository.replaceForBranchDay(branchDayId, emptyMap())
            return
        }

        val accumulatedTotals = mutableMapOf<UUID, BigDecimal>()

        for (sale in sales) {
            val eligibleUsers = mutableSetOf<UUID>()

            val clockedInUsers = AttendanceService.findUsersClockedInAt(branchDayId, sale.soldAt)
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
            "[COMMISSION-SERVICE] Recalculated commission for branchDay=$branchDayId: " +
                "${accumulatedTotals.size} users, ${sales.size} sales"
        }
    }

    fun manualRecalculate(branchDayId: UUID) {
        BranchDayService.requireBranchDayExists(branchDayId)
        recalculate(branchDayId, force = true)
    }
}
