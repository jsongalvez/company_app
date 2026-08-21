package com.companyb.companyapp.service
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.AuditContext
import com.companyb.companyapp.repository.AuditLogRepository
import com.companyb.companyapp.repository.BranchInventoryRepository
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.ProductSaleRepository
import com.companyb.companyapp.repository.RetryProductSaleParams
import com.companyb.companyapp.repository.SellProductParams
import com.companyb.companyapp.repository.SellProductResult
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.model.BranchInventory
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.ProductSale
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.commission.CommissionService
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

    @Suppress("ReturnCount", "ThrowsCount", "LongParameterList", "CyclomaticComplexMethod", "LongMethod")
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
                val (branchDay, isRemitted) =
                    BranchDayService.checkBranchDayEditable(callerId, branchDayId, reason)

                if (BranchRepository.findById(branchDay.branchId) == null) {
                    throw NotFoundException("Branch not found")
                }

                val product =
                    ProductRepository.findById(productId)
                        ?: throw NotFoundException("Product not found")

                if (!product.isActive) {
                    throw ValidationException("Product is not active")
                }

                if (sessionId != null) {
                    val session =
                        SessionRepository.findById(sessionId)
                            ?: throw NotFoundException("Session not found")
                    if (session.branchDayId != branchDayId) {
                        throw NotFoundException("Session not found for this branch day")
                    }
                }

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
                        ?: error("inventory card not found for branch=${params.branchId} product=${params.productId}")

                if (beforeCard.currentStock < params.quantity) throw ValidationException("Insufficient stock")

                val newCard =
                    BranchInventoryRepository.requireCardForUpdate(
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

                // Commission splits join this same command transaction — a rollback of any write
                // above also rolls them back (pinned by existing sell/commission failure tests).
                CommissionService.recalculateInTransaction(branchDayId)

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
    ) = AuditLogRepository.recordInsert(
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
    ) = AuditLogRepository.recordUpdate(
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
