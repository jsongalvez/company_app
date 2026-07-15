package com.companyb.companyapp.repository

import com.companyb.companyapp.repository.model.ActiveSessionVoidsView
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.InventoryMovementReason
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.Product
import com.companyb.companyapp.repository.model.ProductSale
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.SessionTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.vendors.ForUpdateOption
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

object ProductSaleRepository {
    @Suppress("LongParameterList", "LongMethod")
    fun sell(
        id: UUID,
        branchDayId: UUID,
        sessionId: UUID?,
        clientId: UUID?,
        isWalkIn: Boolean,
        productId: UUID,
        branchId: UUID,
        quantity: Int,
        expectedVersion: Int,
        handledBy: UUID,
        product: Product,
    ): ProductSale =
        transaction {
            val existing = findByIdInTransaction(id)
            if (existing != null) {
                logger.info { "[PRODUCT-SALE] Sale $id already exists, returning existing (idempotent)" }
                return@transaction existing
            }

            acquireInventoryLock(branchId, productId)

            val card =
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq branchId) and
                            (BranchInventoryTable.productId eq productId)
                    }.singleOrNull()
                    ?: error("inventory card not found for branch=$branchId product=$productId")

            if (card[BranchInventoryTable.currentStock] < quantity) {
                error("insufficient_stock")
            }

            if (card[BranchInventoryTable.version] != expectedVersion) {
                error("version_mismatch")
            }

            val oldStock = card[BranchInventoryTable.currentStock]
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
                error("version_mismatch")
            }

            val totalAmount = product.unitPrice * BigDecimal.valueOf(quantity.toLong())

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

            InventoryMovementTable.insert {
                it[InventoryMovementTable.productId] = productId
                it[InventoryMovementTable.productSaleId] = id
                it[InventoryMovementTable.branchId] = branchId
                it[InventoryMovementTable.branchDayId] = branchDayId
                it[InventoryMovementTable.reason] = InventoryMovementReason.SALE
                it[InventoryMovementTable.quantityChange] = -quantity
                it[InventoryMovementTable.movedBy] = handledBy
                it[InventoryMovementTable.movedAt] = OffsetDateTime.now()
            }

            val sale =
                findByIdInTransaction(id)
                    ?: error("product sale not found after insert for $id")

            AuditLogRepository.record(
                tableName = ProductSaleTable.tableName,
                recordId = sale.id,
                action = AuditAction.INSERT,
                changedBy = handledBy,
                newValue =
                    AuditLogRepository.jsonFields(
                        "id" to sale.id.toString(),
                        "branchDayId" to sale.branchDayId.toString(),
                        "productId" to sale.productId.toString(),
                        "quantity" to sale.quantity.toString(),
                        "totalAmount" to sale.totalAmountAtTime.toPlainString(),
                    ),
            )

            AuditLogRepository.record(
                tableName = BranchInventoryTable.tableName,
                recordId = card[BranchInventoryTable.id],
                action = AuditAction.UPDATE,
                changedBy = handledBy,
                oldValue =
                    AuditLogRepository.jsonFields(
                        "currentStock" to oldStock.toString(),
                        "version" to oldVersion.toString(),
                    ),
                newValue =
                    AuditLogRepository.jsonFields(
                        "currentStock" to newStock.toString(),
                        "version" to (expectedVersion + 1).toString(),
                    ),
            )

            sale
        }.also {
            logger.info {
                "[PRODUCT-SALE] Sale $id created " +
                    "product=$productId branch=$branchId qty=$quantity " +
                    "total=${it.totalAmountAtTime}"
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

    private fun org.jetbrains.exposed.sql.ResultRow.toProductSale(): ProductSale =
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
