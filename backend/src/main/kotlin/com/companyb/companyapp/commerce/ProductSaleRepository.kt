package com.companyb.companyapp.commerce

import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.session.ActiveSessionVoidsView
import com.companyb.companyapp.session.SessionTable
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

data class SellProductParams(
    val id: UUID,
    val branchDayId: UUID,
    val sessionId: UUID?,
    val clientId: UUID?,
    val isWalkIn: Boolean,
    val productId: UUID,
    val branchId: UUID,
    val quantity: Int,
    val expectedVersion: Int,
    val handledBy: UUID,
    val product: Product,
)

data class RetryProductSaleParams(
    val id: UUID,
    val branchDayId: UUID,
    val sessionId: UUID?,
    val clientId: UUID?,
    val isWalkIn: Boolean,
    val productId: UUID,
    val quantity: Int,
    val handledBy: UUID,
)

data class SellProductResult(
    val sale: ProductSale,
    val created: Boolean,
)

internal object ProductSaleRepository {
    /**
     * In-transaction store operation (#323, ADR-0024) — idempotent fast-path for a same-day retry:
     * returns the existing sale after ownership validation, null when the id belongs to a sale of a
     * different branch day so the caller falls through to the day gate (gate-beats-NotFound).
     */
    fun findSameDaySaleInTransaction(params: RetryProductSaleParams): ProductSale? {
        val existing = findByIdInTransaction(params.id) ?: return null
        if (existing.branchDayId != params.branchDayId) return null
        validateRetryOwnership(existing, params)
        return existing
    }

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun insertSaleInTransaction(params: SellProductParams): Boolean =
        ProductSaleTable
            .insertIgnore {
                it[ProductSaleTable.id] = params.id
                it[ProductSaleTable.branchDayId] = params.branchDayId
                if (params.sessionId != null) it[ProductSaleTable.sessionId] = params.sessionId
                if (params.clientId != null) it[ProductSaleTable.clientId] = params.clientId
                it[ProductSaleTable.isWalkIn] = params.isWalkIn
                it[ProductSaleTable.productId] = params.productId
                it[ProductSaleTable.productName] = params.product.name
                it[ProductSaleTable.handledBy] = params.handledBy
                it[ProductSaleTable.quantity] = params.quantity
                it[ProductSaleTable.unitPriceAtTime] = params.product.unitPrice
                it[ProductSaleTable.totalAmountAtTime] =
                    params.product.unitPrice * BigDecimal.valueOf(params.quantity.toLong())
                it[ProductSaleTable.commissionAmountAtTime] = params.product.commissionAmount
                it[ProductSaleTable.soldAt] = CurrentTimestampWithTimeZone
            }.insertedCount > 0

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun insertSaleMovementInTransaction(params: SellProductParams) {
        InventoryMovementTable.insert {
            it[InventoryMovementTable.productId] = params.productId
            it[InventoryMovementTable.productSaleId] = params.id
            it[InventoryMovementTable.branchId] = params.branchId
            it[InventoryMovementTable.branchDayId] = params.branchDayId
            it[InventoryMovementTable.reason] = InventoryMovementReason.SALE
            it[InventoryMovementTable.quantityChange] = -params.quantity
            it[InventoryMovementTable.movedBy] = params.handledBy
            it[InventoryMovementTable.movedAt] = CurrentTimestampWithTimeZone
        }
    }

    /**
     * Full retry-ownership validation for a lost idempotent-insert race: day mismatch means the
     * id belongs to another branch day's sale (404), any field mismatch means another request
     * (409). Pure comparison — no database access.
     */
    internal fun validateRetryOwnership(
        existing: ProductSale,
        params: RetryProductSaleParams,
    ) {
        if (existing.branchDayId != params.branchDayId) {
            throw NotFoundException("Product sale not found for this branch day")
        }
        if (!sameSaleParties(existing, params) || !sameSaleItems(existing, params)) {
            throw ConflictException("Product sale id already belongs to another create request")
        }
    }

    private fun sameSaleParties(
        existing: ProductSale,
        params: RetryProductSaleParams,
    ): Boolean =
        existing.handledBy == params.handledBy &&
            existing.sessionId == params.sessionId &&
            existing.clientId == params.clientId &&
            existing.isWalkIn == params.isWalkIn

    private fun sameSaleItems(
        existing: ProductSale,
        params: RetryProductSaleParams,
    ): Boolean = existing.productId == params.productId && existing.quantity == params.quantity

    fun findById(id: UUID): ProductSale? =
        transaction {
            findByIdInTransaction(id)
        }

    /** In-transaction read (#497) — runs on the caller's transaction for the shared aggregation. */
    fun findNonVoidedSalesByBranchDayInTransaction(branchDayId: UUID): List<ProductSale> =
        ProductSaleTable
            .join(
                SessionTable,
                JoinType.LEFT,
                ProductSaleTable.sessionId,
                SessionTable.id,
                false,
                null,
            ).join(
                ActiveSessionVoidsView,
                JoinType.LEFT,
                SessionTable.id,
                ActiveSessionVoidsView.sessionId,
                false,
                null,
            ).selectAll()
            .where {
                (ProductSaleTable.branchDayId eq branchDayId) and
                    (ActiveSessionVoidsView.sessionId.isNull())
            }.map { it.toProductSale() }

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun findByIdInTransaction(id: UUID): ProductSale? =
        ProductSaleTable
            .selectAll()
            .where { ProductSaleTable.id eq id }
            .singleOrNull()
            ?.let { it.toProductSale() }

    private fun org.jetbrains.exposed.v1.core.ResultRow.toProductSale(): ProductSale =
        ProductSale(
            id = this[ProductSaleTable.id],
            branchDayId = this[ProductSaleTable.branchDayId],
            sessionId = this[ProductSaleTable.sessionId],
            clientId = this[ProductSaleTable.clientId],
            isWalkIn = this[ProductSaleTable.isWalkIn],
            productId = this[ProductSaleTable.productId],
            productName = this[ProductSaleTable.productName],
            handledBy = this[ProductSaleTable.handledBy],
            quantity = this[ProductSaleTable.quantity],
            unitPriceAtTime = this[ProductSaleTable.unitPriceAtTime],
            totalAmountAtTime = this[ProductSaleTable.totalAmountAtTime],
            commissionAmountAtTime = this[ProductSaleTable.commissionAmountAtTime],
            soldAt = this[ProductSaleTable.soldAt],
        )
}
