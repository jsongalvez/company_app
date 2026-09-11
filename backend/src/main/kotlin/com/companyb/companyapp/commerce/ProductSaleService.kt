package com.companyb.companyapp.commerce
import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.client.ClientReads
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.session.SessionReads
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID

/**
 * Product-sale feature command (#323, ADR-0024). [sell] owns exactly one transaction: the day
 * gate, lookups, idempotent insert, locked card read + stock guard, movement insert, both audit
 * rows, and the commission recalculation all run inside it — sale, stock decrement, movement,
 * audit, and commission splits commit atomically or not at all.
 */
object ProductSaleService {
    private val logger = KotlinLogging.logger {}

    // #597: 10-param sale command stays whole per #535; the single command-owned transaction
    // (ADR-0024) keeps day gate + stock guard + audit + commission recalc atomic — splitting
    // the sequence would break atomicity (same precedent as RemittanceService.submit #595).
    @Suppress("LongParameterList", "CyclomaticComplexMethod", "LongMethod") // #597
    fun sell(
        callerId: UUID,
        id: UUID,
        branchDayId: UUID,
        sessionId: UUID?,
        clientId: UUID?,
        isWalkIn: Boolean,
        productId: UUID,
        quantity: Int,
        expectedVersion: Int,
        reason: String? = null,
    ): ProductSale {
        val retryParams =
            RetryProductSaleParams(
                id = id,
                branchDayId = branchDayId,
                sessionId = sessionId,
                clientId = clientId,
                isWalkIn = isWalkIn,
                productId = productId,
                quantity = quantity,
                handledBy = callerId,
            )

        var createdBranchId: UUID? = null

        val result =
            transaction {
                // Fast path: same-day idempotent retry. A day-mismatched id returns null here so
                // it falls through to the gate below (gate-beats-NotFound precedence preserved).
                ProductSaleRepository.findSameDaySaleInTransaction(retryParams)?.let {
                    return@transaction SellProductResult(it, created = false)
                }
                // Locked day read (#454): serializes this sale with remittance's
                // REMITTED transition inside the same command transaction. The day row
                // is locked before the product row (#518 day → product → card order,
                // shared with InventoryService.recordMovement).
                val (branchDay, isRemitted) =
                    BranchDayService.checkBranchDayEditableInTransaction(callerId, branchDayId, reason)

                // #710 precedence preserved via findById (throws the same 404):
                // an unknown branch fails closed before the product/session gates.
                BranchService.findById(branchDay.branchId)

                val product = requireActiveProduct(productId)

                requireSessionInDay(sessionId, branchDayId)

                requireClientExistsInTransaction(clientId)

                val params =
                    SellProductParams(
                        id = id,
                        branchDayId = branchDayId,
                        sessionId = sessionId,
                        clientId = clientId,
                        isWalkIn = isWalkIn,
                        productId = productId,
                        branchId = branchDay.branchId,
                        quantity = quantity,
                        expectedVersion = expectedVersion,
                        handledBy = callerId,
                        product = product,
                    )

                if (!ProductSaleRepository.insertSaleInTransaction(params)) {
                    // Lost the idempotent-insert race: validate full ownership of the winner.
                    val concurrentSale =
                        ProductSaleRepository.findByIdInTransaction(params.id)
                            ?: error("product sale missing after idempotent insert for ${params.id}")
                    ProductSaleRepository.validateRetryOwnership(concurrentSale, retryParams)
                    return@transaction SellProductResult(concurrentSale, created = false)
                }

                val beforeCard =
                    BranchInventoryRepository.findCardForUpdateInTransaction(params.branchId, params.productId)
                        ?: throw NotFoundException("Inventory card not found")

                if (beforeCard.currentStock < params.quantity) throw ValidationException("Insufficient stock")

                val newCard =
                    BranchInventoryRepository.requireCardForUpdateInTransaction(
                        beforeCard,
                        params.expectedVersion,
                        -params.quantity,
                    )

                ProductSaleRepository.insertSaleMovementInTransaction(params)

                val sale =
                    ProductSaleRepository.findByIdInTransaction(params.id)
                        ?: error("product sale not found after insert for ${params.id}")

                val context = AuditContext(callerId, branchDay.branchId, isRemitted, reason)
                ProductSaleAudit.inserted(context, sale)
                ProductSaleAudit.updated(context, beforeCard, newCard)

                // #687 — PAST/REMITTED sales force the recalc: the day gate above already
                // required EDIT_PAST_DAY (+reason on REMITTED), so the caller holds the same
                // authority the manual-recalculate route requires; joining the splits in this
                // transaction keeps persisted commission_split equal to liveCommissions
                // instead of stale-until-manual (daily_sales_summary.total_commission).
                // OPEN-day behavior is unchanged (force is a no-op there).
                CommissionService.recalculateInTransaction(branchDayId, force = true)

                createdBranchId = params.branchId
                SellProductResult(sale, created = true)
            }

        if (result.created) {
            logger.info {
                "[PRODUCT-SALE] Sale ${result.sale.id} created " +
                    "product=$productId branch=$createdBranchId qty=$quantity " +
                    "total=${result.sale.totalAmountAtTime}"
            }
        }
        return result.sale
    }

    private fun requireActiveProduct(productId: UUID): Product {
        val product =
            ProductRepository.findByIdForUpdateInTransaction(productId)
                ?: throw NotFoundException("Product not found")

        if (!product.isActive) {
            throw ValidationException("Product is not active")
        }
        return product
    }

    /**
     * #858 — same-day + void gate for sell (runs on the caller's command
     * transaction): the in-transaction reads join the sale's atomic commit, so a
     * void committed before this transaction's snapshot is observed. A concurrent
     * void racing the insert stays tolerable: the sale then lands linked to a
     * voided session and is excluded everywhere financial (daily totals, picker,
     * PRODUCT lines, commission) by the same rule — no ledger diverges.
     */
    private fun requireSessionInDay(
        sessionId: UUID?,
        branchDayId: UUID,
    ) {
        if (sessionId == null) return
        val session =
            SessionReads.findByIdInTransaction(sessionId)
                ?: throw NotFoundException("Session not found")
        if (session.branchDayId != branchDayId) {
            throw NotFoundException("Session not found for this branch day")
        }
        if (SessionReads.isVoidedInTransaction(sessionId)) {
            throw ValidationException("Session is voided")
        }
    }

    /**
     * #684 — client existence gate for sell (runs on the caller's command
     * transaction): unknown clientId fails closed with 404 before the insert,
     * so the FK never surfaces as a 500. Null stays allowed (anonymous walk-in).
     */
    private fun requireClientExistsInTransaction(clientId: UUID?) {
        if (clientId == null) return
        ClientReads.acquireLockInTransaction(clientId) ?: throw NotFoundException("Client not found")
    }
}

/**
 * Product-sale audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so both rows commit atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object ProductSaleAudit {
    fun inserted(
        context: AuditContext,
        sale: ProductSale,
    ) = AuditLog.recordInsert(
        tableName = ProductSaleTable.tableName,
        recordId = sale.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = ProductSaleTable.auditFields(sale),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    fun updated(
        context: AuditContext,
        before: BranchInventory,
        after: BranchInventory,
    ) = AuditLog.recordUpdate(
        tableName = BranchInventoryTable.tableName,
        recordId = before.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = BranchInventoryTable::auditFields,
    )
}
