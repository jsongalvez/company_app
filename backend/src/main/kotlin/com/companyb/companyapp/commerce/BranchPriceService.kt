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
 * Per-branch product price and commission (feedback 503bf228). Any member who can edit the
 * branch sets them; null puts the product back on the shared price. Each change is an audited
 * UPDATE of the branch's inventory card, so Change history shows who set what and when.
 */
internal object BranchPriceService {
    private val logger = KotlinLogging.logger {}

    fun setPrice(
        callerId: UUID,
        branchId: UUID,
        productId: UUID,
        unitPrice: BigDecimal?,
        commissionAmount: BigDecimal?,
    ): BranchInventory =
        transaction {
            if (BranchService.findByIdOrNull(branchId) == null) throw NotFoundException("Branch not found")
            ProductRepository
                .findByIdForUpdateInTransaction(productId)
                ?.takeIf { it.isActive }
                ?: throw NotFoundException("Product not found")
            unitPrice?.let { validateMoneyAmount(it, "unitPrice") }
            commissionAmount?.let { validateMoneyAmount(it, "commissionAmount") }

            val context = AuditContext(callerId, branchId)
            val ensured = BranchInventoryRepository.ensureCardInTransaction(branchId, productId)
            if (ensured.created) BranchInventoryAudit.inserted(context, ensured.card)
            val before =
                BranchInventoryRepository.findCardForUpdateInTransaction(branchId, productId)
                    ?: error("inventory card vanished for branch=$branchId product=$productId")
            if (same(before.unitPrice, unitPrice) &&
                same(before.commissionAmount, commissionAmount)
            ) {
                return@transaction before
            }
            BranchInventoryRepository.setPricesInTransaction(before.id, unitPrice, commissionAmount)
            val after = before.copy(unitPrice = unitPrice, commissionAmount = commissionAmount)
            BranchInventoryAudit.updated(context, before, after)
            after
        }.also { logger.info { "[SET-BRANCH-PRICE] branch=$branchId product=$productId" } }

    private fun same(
        a: BigDecimal?,
        b: BigDecimal?,
    ): Boolean = if (a == null || b == null) a == b else a.compareTo(b) == 0

    /** The product as sold at [branchId]: its card's own price and commission replace the shared ones. */
    fun priceAtBranch(
        product: Product,
        branchId: UUID,
    ): Product {
        val card = BranchInventoryRepository.findCardInTransaction(branchId, product.id) ?: return product
        return product.copy(
            unitPrice = card.unitPrice ?: product.unitPrice,
            commissionAmount = card.commissionAmount ?: product.commissionAmount,
        )
    }
}
