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
 * Each product's own list price and commission at a branch (503bf228, a6946922). Any member who
 * can edit the branch sets them; a value left out stays as it is. Each change is an audited
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
            val after =
                before.copy(
                    unitPrice = unitPrice ?: before.unitPrice,
                    commissionAmount = commissionAmount ?: before.commissionAmount,
                )
            if (before.unitPrice.compareTo(after.unitPrice) == 0 &&
                before.commissionAmount.compareTo(after.commissionAmount) == 0
            ) {
                return@transaction before
            }
            BranchInventoryRepository.setPriceInTransaction(before.id, after.unitPrice, after.commissionAmount)
            BranchInventoryAudit.updated(context, before, after)
            after
        }.also { logger.info { "[SET-BRANCH-PRICE] branch=$branchId product=$productId" } }

    /** The product as sold at [branchId]: the card's own price and commission win. */
    fun priceAtBranch(
        product: Product,
        branchId: UUID,
    ): Product {
        val card = BranchInventoryRepository.findCardInTransaction(branchId, product.id)
        return product.copy(
            unitPrice = card?.unitPrice ?: product.unitPrice,
            commissionAmount = card?.commissionAmount ?: product.commissionAmount,
        )
    }
}
