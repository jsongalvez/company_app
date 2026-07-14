package com.companyb.companyapp.repository

import com.companyb.companyapp.logging.maskUUID
import com.companyb.companyapp.repository.model.AuditAction
import com.companyb.companyapp.repository.model.BranchInventory
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.repository.model.InventoryMovementReason
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.OffsetDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class RestockResult(
    val movement: InventoryMovement,
    val updatedStock: Int,
)

object BranchInventoryRepository {
    fun ensureCard(
        branchId: UUID,
        productId: UUID,
    ): BranchInventory =
        transaction {
            val existing = findCardInTransaction(branchId, productId)
            if (existing != null) {
                return@transaction existing
            }

            BranchInventoryTable.insertIgnore {
                it[BranchInventoryTable.branchId] = branchId
                it[BranchInventoryTable.productId] = productId
                it[BranchInventoryTable.currentStock] = 0
                it[BranchInventoryTable.version] = 1
            }

            findCardInTransaction(branchId, productId)
                ?: error("inventory card not found after idempotent insert for branch=$branchId product=$productId")
        }.also {
            logger.info {
                "[ENSURE-CARD] Inventory card ensured for branch=$branchId product=$productId stock=${it.currentStock}"
            }
        }

    @Suppress("LongParameterList", "LongMethod")
    fun restock(
        movementId: UUID,
        branchId: UUID,
        productId: UUID,
        quantity: Int,
        branchDayId: UUID,
        expectedVersion: Int,
        movedBy: UUID,
    ): RestockResult =
        transaction {
            val card =
                findCardInTransaction(branchId, productId)
                    ?: error("inventory card not found for branch=$branchId product=$productId")

            if (card.version != expectedVersion) {
                error("version_mismatch")
            }

            val updatedCount =
                BranchInventoryTable.update({
                    (BranchInventoryTable.branchId eq branchId) and
                        (BranchInventoryTable.productId eq productId) and
                        (BranchInventoryTable.version eq expectedVersion)
                }) {
                    it[BranchInventoryTable.currentStock] = card.currentStock + quantity
                    it[BranchInventoryTable.version] = expectedVersion + 1
                }

            if (updatedCount == 0) {
                error("version_mismatch")
            }

            val insertedCount =
                InventoryMovementTable
                    .insertIgnore {
                        it[InventoryMovementTable.id] = movementId
                        it[InventoryMovementTable.productId] = productId
                        it[InventoryMovementTable.branchId] = branchId
                        it[InventoryMovementTable.branchDayId] = branchDayId
                        it[InventoryMovementTable.reason] = InventoryMovementReason.RESTOCK
                        it[InventoryMovementTable.quantityChange] = quantity
                        it[InventoryMovementTable.movedBy] = movedBy
                        it[InventoryMovementTable.movedAt] = OffsetDateTime.now()
                    }.insertedCount

            val movementRow =
                InventoryMovementTable
                    .selectAll()
                    .where { InventoryMovementTable.id eq movementId }
                    .single()
                    .toInventoryMovement()

            val newCard =
                findCardInTransaction(branchId, productId)
                    ?: error("inventory card not found after restock")

            if (insertedCount > 0) {
                AuditLogRepository.record(
                    tableName = BranchInventoryTable.tableName,
                    recordId = newCard.id,
                    action = AuditAction.UPDATE,
                    changedBy = movedBy,
                    oldValue =
                        AuditLogRepository.jsonFields(
                            "currentStock" to card.currentStock.toString(),
                            "version" to card.version.toString(),
                        ),
                    newValue =
                        AuditLogRepository.jsonFields(
                            "currentStock" to newCard.currentStock.toString(),
                            "version" to newCard.version.toString(),
                        ),
                )

                AuditLogRepository.record(
                    tableName = InventoryMovementTable.tableName,
                    recordId = movementRow.id,
                    action = AuditAction.INSERT,
                    changedBy = movedBy,
                    newValue =
                        AuditLogRepository.jsonFields(
                            "productId" to productId.toString(),
                            "branchId" to branchId.toString(),
                            "branchDayId" to branchDayId.toString(),
                            "reason" to InventoryMovementReason.RESTOCK.name,
                            "quantityChange" to quantity.toString(),
                        ),
                )
            }

            RestockResult(
                movement = movementRow,
                updatedStock = newCard.currentStock,
            )
        }.also {
            logger.info {
                "[RESTOCK] Restocked product=$productId branch=$branchId qty=$quantity stock=${it.updatedStock}"
            }
        }

    fun findCardInTransaction(
        branchId: UUID,
        productId: UUID,
    ): BranchInventory? =
        BranchInventoryTable
            .selectAll()
            .where {
                (BranchInventoryTable.branchId eq branchId) and
                    (BranchInventoryTable.productId eq productId)
            }.singleOrNull()
            ?.let { it.toBranchInventory() }

    fun findByBranch(branchId: UUID): List<BranchInventoryWithProduct> =
        transaction {
            BranchInventoryTable
                .innerJoin(
                    ProductTable,
                    { BranchInventoryTable.productId },
                    { ProductTable.id },
                ).selectAll()
                .where { BranchInventoryTable.branchId eq branchId }
                .orderBy(ProductTable.name to SortOrder.ASC)
                .map { row ->
                    BranchInventoryWithProduct(
                        inventory = row.toBranchInventory(),
                        productName = row[ProductTable.name],
                    )
                }
        }.also { logger.info { "[FIND-INVENTORY] Fetched ${it.size} inventory card(s) for branch $branchId" } }

    private fun org.jetbrains.exposed.sql.ResultRow.toBranchInventory(): BranchInventory =
        BranchInventory(
            id = this[BranchInventoryTable.id],
            branchId = this[BranchInventoryTable.branchId],
            productId = this[BranchInventoryTable.productId],
            currentStock = this[BranchInventoryTable.currentStock],
            version = this[BranchInventoryTable.version],
        )

    private fun org.jetbrains.exposed.sql.ResultRow.toInventoryMovement(): InventoryMovement =
        InventoryMovement(
            id = this[InventoryMovementTable.id],
            productId = this[InventoryMovementTable.productId],
            branchId = this[InventoryMovementTable.branchId],
            branchDayId = this[InventoryMovementTable.branchDayId],
            reason = this[InventoryMovementTable.reason],
            quantityChange = this[InventoryMovementTable.quantityChange],
            movedBy = this[InventoryMovementTable.movedBy],
            movedAt = this[InventoryMovementTable.movedAt],
            notes = this[InventoryMovementTable.notes],
        )
}
