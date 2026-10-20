package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.utils.validateMoneyAmount
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

/**
 * Per-branch product price (feedback 503bf228). Any member who can edit the branch sets it;
 * null puts the product back on the shared price. Commission is branch-wide
 * (Branch.commissionAmount). Each change is an audited
 * UPDATE of the branch's inventory card, so Change history shows who set what and when.
 */
internal object BranchPriceService {
    private val logger = KotlinLogging.logger {}

    fun setPrice(
        callerId: UUID,
        branchId: UUID,
        productId: UUID,
        unitPrice: BigDecimal?,
    ): BranchInventory =
        transaction {
            if (BranchService.findByIdOrNull(branchId) == null) throw NotFoundException("Branch not found")
            ProductRepository
                .findByIdForUpdateInTransaction(productId)
                ?.takeIf { it.isActive }
                ?: throw NotFoundException("Product not found")
            unitPrice?.let { validateMoneyAmount(it, "unitPrice") }

            val context = AuditContext(callerId, branchId)
            val ensured = BranchInventoryRepository.ensureCardInTransaction(branchId, productId)
            if (ensured.created) BranchInventoryAudit.inserted(context, ensured.card)
            val before =
                BranchInventoryRepository.findCardForUpdateInTransaction(branchId, productId)
                    ?: error("inventory card vanished for branch=$branchId product=$productId")
            if (same(before.unitPrice, unitPrice)) return@transaction before
            BranchInventoryRepository.setPriceInTransaction(before.id, unitPrice)
            val after = before.copy(unitPrice = unitPrice)
            BranchInventoryAudit.updated(context, before, after)
            after
        }.also { logger.info { "[SET-BRANCH-PRICE] branch=$branchId product=$productId" } }

    private fun same(
        a: BigDecimal?,
        b: BigDecimal?,
    ): Boolean = if (a == null || b == null) a == b else a.compareTo(b) == 0

    /** The product as sold at [branchId]: the card's own price and the branch's commission win. */
    fun priceAtBranch(
        product: Product,
        branchId: UUID,
    ): Product {
        val card = BranchInventoryRepository.findCardInTransaction(branchId, product.id)
        val commission = BranchService.findByIdOrNull(branchId)?.commissionAmount
        return product.copy(
            unitPrice = card?.unitPrice ?: product.unitPrice,
            commissionAmount = commission ?: product.commissionAmount,
        )
    }
}
