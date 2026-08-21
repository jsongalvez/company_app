package com.companyb.companyapp.service.finance.commission

import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.CommissionManualInclusionRepository
import com.companyb.companyapp.repository.CommissionSplitRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.model.CommissionManualInclusion
import com.companyb.companyapp.repository.model.CommissionManualInclusionTable
import com.companyb.companyapp.repository.model.CommissionManualInclusionUpsertParams
import com.companyb.companyapp.repository.model.CommissionSplit
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.service.branchday.BranchDayRepository
import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

object CommissionService {
    private val logger = KotlinLogging.logger {}

    private const val COMMISSION_SCALE = 4

    internal var failAfterReplacementForTests: Boolean = false

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
    ): CommissionManualInclusion =
        transaction {
            createManualInclusionInTransaction(callerId, id, productSaleId, userId, isIncluded, reason)
        }

    @Suppress("ThrowsCount", "LongParameterList")
    private fun createManualInclusionInTransaction(
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

        val mutation =
            CommissionManualInclusionRepository.upsertInTransaction(
                CommissionManualInclusionUpsertParams(
                    id = id,
                    productSaleId = productSaleId,
                    userId = userId,
                    isIncluded = isIncluded,
                    reason = reason,
                    assignedBy = callerId,
                ),
            )

        val context = AuditContext(callerId, branchId)
        if (mutation.existing == null) {
            CommissionAudit.inserted(context, mutation.inclusion)
        } else {
            CommissionAudit.updated(context, mutation.existing, mutation.inclusion)
        }

        recalculateInTransaction(sale.branchDayId)

        logger.info {
            "[COMMISSION-INCLUSION] Created inclusion ${mutation.inclusion.id} for productSale=$productSaleId " +
                "userId=$userId isIncluded=$isIncluded"
        }
        return mutation.inclusion
    }

    @Suppress("ReturnCount")
    fun recalculate(
        branchDayId: UUID,
        force: Boolean = false,
    ) = transaction {
        recalculateInTransaction(branchDayId, force)
    }

    /**
     * Store-side body for callers already inside their own command-owned write (#326 closeout:
     * attendance/sale/manual-inclusion commands must not nest this module's write block).
     * Acquires the branch-day row lock so concurrent recalcs serialize identically whether the
     * entry point is the standalone wrapper or an enclosing command.
     */
    @Suppress("ReturnCount")
    internal fun recalculateInTransaction(
        branchDayId: UUID,
        force: Boolean = false,
    ) {
        BranchDayRepository.acquireLock(branchDayId)
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
            CommissionSplitRepository.replaceForBranchDayInTransaction(branchDayId, emptyMap())
            failIfInjectedForTests()
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

        CommissionSplitRepository.replaceForBranchDayInTransaction(branchDayId, accumulatedTotals)
        failIfInjectedForTests()

        logger.info {
            "[COMMISSION-SERVICE] Recalculated commission for branchDay=$branchDayId: " +
                "${accumulatedTotals.size} users, ${sales.size} sales"
        }
    }

    fun manualRecalculate(branchDayId: UUID) {
        BranchDayService.requireBranchDayExists(branchDayId)
        recalculate(branchDayId, force = true)
    }

    private fun failIfInjectedForTests() {
        if (failAfterReplacementForTests) {
            error("injected commission trigger failure")
        }
    }
}

/**
 * Commission audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so each row commits atomically with the inclusion upsert. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object CommissionAudit {
    fun inserted(
        context: AuditContext,
        inclusion: CommissionManualInclusion,
    ) = AuditLogRepository.recordInsert(
        tableName = CommissionManualInclusionTable.tableName,
        recordId = inclusion.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = CommissionManualInclusionTable.auditFields(inclusion),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    fun updated(
        context: AuditContext,
        before: CommissionManualInclusion,
        after: CommissionManualInclusion,
    ) = AuditLogRepository.recordUpdate(
        tableName = CommissionManualInclusionTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = CommissionManualInclusionTable::auditFields,
    )
}
