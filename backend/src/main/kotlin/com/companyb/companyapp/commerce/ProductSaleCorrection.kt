package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.DayWrite
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.utils.validateMoneyAmount
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Owed #21: a product sale is changed, deleted and restored under the expense rules (any branch
 * member until the day is remitted, a reason on an earlier day). Every correction is the same
 * step: write the sale's new state, then move each product's stock by what the sale held before
 * minus what it holds now ([stockMoves]), audit both, and recount the day's commission.
 */
object ProductSaleCorrection {
    fun change(
        callerId: UUID,
        saleId: UUID,
        to: SaleChange,
        reason: String?,
    ): ProductSale =
        correct(callerId, saleId, reason) { before, branchId ->
            if (before.deletedAt != null) throw ValidationException("Cannot change a deleted product sale")
            val price = if (to.productId == before.productId) before.price() else priceOf(to.productId, branchId)
            validateMoneyAmount(price.unitPrice.multiply(BigDecimal.valueOf(to.quantity.toLong())), "totalAmount")
            ProductSaleRepository.changeInTransaction(before, price, to.quantity, to.expectedVersion)
        }

    fun delete(
        callerId: UUID,
        saleId: UUID,
        reason: String,
        expectedVersion: Int,
    ): ProductSale =
        correct(callerId, saleId, reason) { before, _ ->
            if (before.deletedAt != null) throw ValidationException("Product sale is already deleted")
            ProductSaleRepository.softDeleteInTransaction(saleId, callerId, reason, expectedVersion)
        }

    fun restore(
        callerId: UUID,
        saleId: UUID,
        reason: String?,
    ): ProductSale =
        correct(callerId, saleId, reason) { _, _ ->
            ProductSaleRepository.restoreInTransaction(saleId)
                ?: throw ValidationException("Product sale is not deleted")
        }

    private fun correct(
        callerId: UUID,
        saleId: UUID,
        reason: String?,
        write: (before: ProductSale, branchId: UUID) -> ProductSale,
    ): ProductSale =
        transaction {
            val before =
                ProductSaleRepository.findByIdInTransaction(saleId) ?: throw NotFoundException("Product sale not found")
            // Day row first, then product, then card: the lock order every stock write shares (#518).
            val (day, isRemitted) =
                BranchDayService.checkBranchDayEditableInTransaction(callerId, before.branchDayId, reason, DayWrite.FIX)
            val after = write(before, day.branchId)
            val context = AuditContext(callerId, day.branchId, isRemitted, reason)
            for ((productId, delta) in stockMoves(before, after).toSortedMap()) {
                val card =
                    BranchInventoryRepository.findCardForUpdateInTransaction(day.branchId, productId)
                        ?: throw NotFoundException("Inventory card not found")
                val moved = BranchInventoryRepository.requireCardForUpdateInTransaction(card, card.version, delta)
                ProductSaleRepository.insertStockMoveInTransaction(after, day.branchId, productId, delta, callerId)
                ProductSaleAudit.updated(context, card, moved)
            }
            ProductSaleAudit.changed(context, before, after)
            CommissionService.recalculateInTransaction(before.branchDayId, force = true)
            after
        }

    private fun priceOf(
        productId: UUID,
        branchId: UUID,
    ): SalePrice {
        val product =
            ProductRepository.findByIdForUpdateInTransaction(productId) ?: throw NotFoundException("Product not found")
        if (!product.isActive) throw ValidationException("Product is not active")
        val priced = BranchPriceService.priceAtBranch(product, branchId)
        validateMoneyAmount(priced.unitPrice, "unitPrice")
        return SalePrice(priced.id, priced.name, priced.unitPrice, priced.commissionAmount)
    }

    private fun ProductSale.price() = SalePrice(productId, productName, unitPriceAtTime, commissionAmountAtTime)
}

/** What a sale becomes: its product and quantity, against the version the person saw. */
data class SaleChange(
    val productId: UUID,
    val quantity: Int,
    val expectedVersion: Int,
)

/** Units a sale keeps out of stock, per product: its quantity while live, none once deleted. */
internal fun held(sale: ProductSale): Map<UUID, Int> =
    if (sale.deletedAt != null) emptyMap() else mapOf(sale.productId to sale.quantity)

/** Stock change per product when a sale goes from [before] to [after]: positive gives units back. */
internal fun stockMoves(
    before: ProductSale,
    after: ProductSale,
): Map<UUID, Int> {
    val was = held(before)
    val now = held(after)
    return (was.keys + now.keys)
        .associateWith { (was[it] ?: 0) - (now[it] ?: 0) }
        .filterValues { it != 0 }
}
