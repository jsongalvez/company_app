package com.companyb.companyapp.commission

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.commerce.CommerceReads
import com.companyb.companyapp.commerce.ProductSale
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.workforce.Attendance
import com.companyb.companyapp.workforce.AttendanceRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/** Per-user commission (#497): summed scale-4 shares plus the eligible sale count. */
data class CommissionShare(
    val amount: BigDecimal,
    val eligibleSaleCount: Int,
)

/**
 * Pure per-sale eligibility/split (#497, docs/engines.md Engine 1): a user is eligible for a
 * sale when an attendance window covers soldAt (clockIn <= soldAt, clockOut null or >=
 * soldAt), manual true adds and manual false removes; each sale's commissionAmountAtTime *
 * quantity splits at scale 4 HALF_UP and the rounded shares sum per user. eligibleSaleCount
 * counts eligible sale records (zero-commission sales included); sales with an empty
 * eligible set contribute nothing.
 */
internal fun aggregateShares(
    sales: List<ProductSale>,
    attendance: List<Attendance>,
    inclusions: List<CommissionManualInclusion>,
): Map<UUID, CommissionShare> {
    val inclusionsBySale = inclusions.groupBy { it.productSaleId }
    val totals = mutableMapOf<UUID, CommissionShare>()
    for (sale in sales) {
        val eligibleUsers = eligibleUsersFor(sale, attendance, inclusionsBySale)
        if (eligibleUsers.isNotEmpty()) {
            val perUser =
                CommissionService.splitCommission(sale.commissionAmountAtTime, sale.quantity, eligibleUsers.size)
            for (userId in eligibleUsers) {
                val current = totals[userId] ?: CommissionShare(BigDecimal.ZERO, 0)
                totals[userId] = CommissionShare(current.amount.add(perUser), current.eligibleSaleCount + 1)
            }
        }
    }
    return totals
}

private fun eligibleUsersFor(
    sale: ProductSale,
    attendance: List<Attendance>,
    inclusionsBySale: Map<UUID, List<CommissionManualInclusion>>,
): Set<UUID> {
    val eligibleUsers = mutableSetOf<UUID>()
    for (row in attendance) {
        if (row.clockIn <= sale.soldAt && (row.clockOut == null || row.clockOut >= sale.soldAt)) {
            eligibleUsers.add(row.userId)
        }
    }
    for (inclusion in inclusionsBySale[sale.id].orEmpty()) {
        if (inclusion.isIncluded) {
            eligibleUsers.add(inclusion.userId)
        } else {
            eligibleUsers.remove(inclusion.userId)
        }
    }
    return eligibleUsers
}

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
            CommerceReads.findSaleById(productSaleId)
                ?: throw NotFoundException("Product sale not found")

        // #516 — locked day read: serializes this inclusion with remittance's
        // REMITTED transition inside the same command transaction.
        val (branchDay, isRemitted) =
            BranchDayService.checkBranchDayEditableInTransaction(callerId, sale.branchDayId, reason)

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

        val context = AuditContext(callerId, branchDay.branchId, isRemitted, reason)
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
        BranchDayService.lockDayInTransaction(branchDayId)
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

        val totals = computeTotalsInTransaction(branchDayId)

        CommissionSplitRepository.replaceForBranchDayInTransaction(branchDayId, totals.mapValues { it.value.amount })
        failIfInjectedForTests()

        logger.info {
            "[COMMISSION-SERVICE] Recalculated commission for branchDay=$branchDayId: " +
                "${totals.size} users"
        }
    }

    /**
     * Live commission view (#497): the same batched aggregation the persisted recalculation
     * writes, computed on demand and never stored. The dashboard selects the caller's entry;
     * split rows stay the frozen history (a forced recalc on a PAST day must not move the card).
     */
    fun liveCommissions(branchDayId: UUID): Map<UUID, CommissionShare> =
        transaction {
            computeTotalsInTransaction(branchDayId)
        }

    /**
     * Single batched aggregation (#497) serving both the persisted recalculation and the live
     * card: the local read projection loads the day's facts once, then [aggregateShares] groups
     * them by sale. Runs on the caller's transaction so enclosing commands observe their own
     * uncommitted writes.
     */
    internal fun computeTotalsInTransaction(branchDayId: UUID): Map<UUID, CommissionShare> {
        val facts = CommissionFactReads.loadDayFactsInTransaction(branchDayId)
        if (facts.sales.isEmpty()) return emptyMap()
        return aggregateShares(facts.sales, facts.attendance, facts.inclusions)
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
 * Local batched read projection (map #533 #544): the day's commission facts in one pass —
 * non-voided sales through the commerce seam, attendance windows through the recorded
 * workforce read grant, manual inclusions from the internal store. Read-only on the caller's
 * transaction; it never calls attendance/commerce commands and never mutates their stores.
 * Batching is unchanged: one query per fact family, inclusions skipped when no sales exist.
 */
internal data class CommissionDayFacts(
    val sales: List<ProductSale>,
    val attendance: List<Attendance>,
    val inclusions: List<CommissionManualInclusion>,
)

internal object CommissionFactReads {
    fun loadDayFactsInTransaction(branchDayId: UUID): CommissionDayFacts {
        val sales = CommerceReads.findNonVoidedSalesByBranchDayInTransaction(branchDayId)
        if (sales.isEmpty()) return CommissionDayFacts(emptyList(), emptyList(), emptyList())
        val attendance = AttendanceRepository.findByBranchDayIdInTransaction(branchDayId)
        val inclusions = CommissionManualInclusionRepository.findBySaleIdsInTransaction(sales.map { it.id })
        return CommissionDayFacts(sales, attendance, inclusions)
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
    ) = AuditLog.recordInsert(
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
    ) = AuditLog.recordUpdate(
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
