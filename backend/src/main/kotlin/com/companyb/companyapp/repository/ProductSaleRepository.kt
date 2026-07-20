package com.companyb.companyapp.repository

import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.repository.model.ActiveSessionVoidsView
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.InventoryMovementReason
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.Product
import com.companyb.companyapp.repository.model.ProductSale
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.SessionTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

data class SaleAuditData(
    val sale: ProductSale,
    val inventoryCardId: UUID,
    val oldStock: Int,
    val oldVersion: Int,
    val newStock: Int,
    val newVersion: Int,
)

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

private val logger = KotlinLogging.logger {}

object ProductSaleRepository {
    @Suppress("LongMethod")
    fun sell(
        params: SellProductParams,
        auditFn: (SaleAuditData) -> Unit = {},
    ): ProductSale =
        transaction {
            val existing = findByIdInTransaction(params.id)
            if (existing != null) {
                logger.info { "[PRODUCT-SALE] Sale ${params.id} already exists, returning existing (idempotent)" }
                return@transaction existing
            }

            acquireInventoryLock(params.branchId, params.productId)

            val card =
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq params.branchId) and
                            (BranchInventoryTable.productId eq params.productId)
                    }.singleOrNull()
                    ?: error("inventory card not found for branch=${params.branchId} product=${params.productId}")

            val (oldStock, oldVersion, newStock) =
                decrementInventoryStock(
                    card,
                    params.branchId,
                    params.productId,
                    params.quantity,
                    params.expectedVersion,
                )

            val totalAmount = params.product.unitPrice * BigDecimal.valueOf(params.quantity.toLong())

            insertProductSaleRow(
                params.id,
                params.branchDayId,
                params.sessionId,
                params.clientId,
                params.isWalkIn,
                params.productId,
                params.product,
                params.handledBy,
                params.quantity,
                totalAmount,
            )

            insertSaleInventoryMovement(
                params.id,
                params.productId,
                params.branchId,
                params.branchDayId,
                params.quantity,
                params.handledBy,
            )

            val sale =
                findByIdInTransaction(params.id)
                    ?: error("product sale not found after insert for ${params.id}")

            auditFn(
                SaleAuditData(
                    sale = sale,
                    inventoryCardId = card[BranchInventoryTable.id],
                    oldStock = oldStock,
                    oldVersion = oldVersion,
                    newStock = newStock,
                    newVersion = params.expectedVersion + 1,
                ),
            )
            sale
        }.also {
            logger.info {
                "[PRODUCT-SALE] Sale ${params.id} created " +
                    "product=${params.productId} branch=${params.branchId} qty=${params.quantity} " +
                    "total=${it.totalAmountAtTime}"
            }
        }

    @Suppress("ThrowsCount")
    private fun decrementInventoryStock(
        card: ResultRow,
        branchId: UUID,
        productId: UUID,
        quantity: Int,
        expectedVersion: Int,
    ): Triple<Int, Int, Int> {
        val currentStock = card[BranchInventoryTable.currentStock]
        if (currentStock < quantity) throw ValidationException("Insufficient stock")
        if (card[BranchInventoryTable.version] != expectedVersion) {
            throw VersionMismatchException(BranchInventoryTable.tableName, card[BranchInventoryTable.id])
        }

        val oldStock = currentStock
        val oldVersion = card[BranchInventoryTable.version]
        val newStock = oldStock - quantity

        val updatedCount =
            BranchInventoryTable.update({
                (BranchInventoryTable.branchId eq branchId) and
                    (BranchInventoryTable.productId eq productId) and
                    (BranchInventoryTable.version eq expectedVersion)
            }) {
                it[BranchInventoryTable.currentStock] = newStock
                it[BranchInventoryTable.version] = expectedVersion + 1
            }

        if (updatedCount == 0) {
            throw VersionMismatchException(BranchInventoryTable.tableName, card[BranchInventoryTable.id])
        }
        return Triple(oldStock, oldVersion, newStock)
    }

    @Suppress("LongParameterList")
    private fun insertProductSaleRow(
        id: UUID,
        branchDayId: UUID,
        sessionId: UUID?,
        clientId: UUID?,
        isWalkIn: Boolean,
        productId: UUID,
        product: Product,
        handledBy: UUID,
        quantity: Int,
        totalAmount: BigDecimal,
    ) {
        ProductSaleTable.insert {
            it[ProductSaleTable.id] = id
            it[ProductSaleTable.branchDayId] = branchDayId
            if (sessionId != null) it[ProductSaleTable.sessionId] = sessionId
            if (clientId != null) it[ProductSaleTable.clientId] = clientId
            it[ProductSaleTable.isWalkIn] = isWalkIn
            it[ProductSaleTable.productId] = productId
            it[ProductSaleTable.productName] = product.name
            it[ProductSaleTable.handledBy] = handledBy
            it[ProductSaleTable.quantity] = quantity
            it[ProductSaleTable.unitPriceAtTime] = product.unitPrice
            it[ProductSaleTable.totalAmountAtTime] = totalAmount
            it[ProductSaleTable.commissionAmountAtTime] = product.commissionAmount
        }
    }

    @Suppress("LongParameterList")
    private fun insertSaleInventoryMovement(
        saleId: UUID,
        productId: UUID,
        branchId: UUID,
        branchDayId: UUID,
        quantity: Int,
        movedBy: UUID,
    ) {
        InventoryMovementTable.insert {
            it[InventoryMovementTable.productId] = productId
            it[InventoryMovementTable.productSaleId] = saleId
            it[InventoryMovementTable.branchId] = branchId
            it[InventoryMovementTable.branchDayId] = branchDayId
            it[InventoryMovementTable.reason] = InventoryMovementReason.SALE
            it[InventoryMovementTable.quantityChange] = -quantity
            it[InventoryMovementTable.movedBy] = movedBy
            it[InventoryMovementTable.movedAt] = CurrentTimestampWithTimeZone
        }
    }

    fun findById(id: UUID): ProductSale? =
        transaction {
            findByIdInTransaction(id)
        }

    fun findNonVoidedSalesByBranchDay(branchDayId: UUID): List<ProductSale> =
        transaction {
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
        }

    private fun acquireInventoryLock(
        branchId: UUID,
        productId: UUID,
    ) {
        // Row-level lock on branch_inventory to prevent TOCTOU races
        // on concurrent stock checks during sales (CR-018 D2).
        BranchInventoryTable
            .selectAll()
            .where {
                (BranchInventoryTable.branchId eq branchId) and
                    (BranchInventoryTable.productId eq productId)
            }.forUpdate(ForUpdateOption.ForUpdate)
    }

    private fun findByIdInTransaction(id: UUID): ProductSale? =
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
