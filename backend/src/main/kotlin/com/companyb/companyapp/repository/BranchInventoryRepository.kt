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
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class RestockParams(
    val movementId: UUID,
    val branchId: UUID,
    val productId: UUID,
    val quantity: Int,
    val branchDayId: UUID,
    val expectedVersion: Int,
    val movedBy: UUID,
)

data class RecordMovementParams(
    val movementId: UUID,
    val branchId: UUID,
    val productId: UUID,
    val reason: InventoryMovementReason,
    val quantityChange: Int,
    val notes: String?,
    val branchDayId: UUID,
    val expectedVersion: Int,
    val movedBy: UUID,
)

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

    fun restock(params: RestockParams): RestockResult =
        transaction {
            val card = requireCardForUpdate(params.branchId, params.productId, params.expectedVersion)

            val updatedCount =
                BranchInventoryTable.update({
                    (BranchInventoryTable.branchId eq params.branchId) and
                        (BranchInventoryTable.productId eq params.productId) and
                        (BranchInventoryTable.version eq params.expectedVersion)
                }) {
                    it[BranchInventoryTable.currentStock] = card.currentStock + params.quantity
                    it[BranchInventoryTable.version] = params.expectedVersion + 1
                }

            if (updatedCount == 0) {
                error("version_mismatch")
            }

            val insertedCount =
                InventoryMovementTable
                    .insertIgnore {
                        it[InventoryMovementTable.id] = params.movementId
                        it[InventoryMovementTable.productId] = params.productId
                        it[InventoryMovementTable.branchId] = params.branchId
                        it[InventoryMovementTable.branchDayId] = params.branchDayId
                        it[InventoryMovementTable.reason] = InventoryMovementReason.RESTOCK
                        it[InventoryMovementTable.quantityChange] = params.quantity
                        it[InventoryMovementTable.movedBy] = params.movedBy
                        it[InventoryMovementTable.movedAt] =
                            CurrentTimestampWithTimeZone
                    }.insertedCount

            val movementRow =
                InventoryMovementTable
                    .selectAll()
                    .where { InventoryMovementTable.id eq params.movementId }
                    .single()
                    .toInventoryMovement()

            val newCard =
                findCardInTransaction(params.branchId, params.productId)
                    ?: error("inventory card not found after restock")

            writeRestockAuditLogs(
                insertedCount,
                card,
                newCard,
                params.movedBy,
                params.productId,
                params.branchId,
                params.branchDayId,
                params.quantity,
                movementRow,
            )

            RestockResult(
                movement = movementRow,
                updatedStock = newCard.currentStock,
            )
        }.also {
            logger.info {
                "[RESTOCK] Restocked product=${params.productId} " +
                    "branch=${params.branchId} qty=${params.quantity} stock=${it.updatedStock}"
            }
        }

    @Suppress("LongParameterList")
    private fun writeRestockAuditLogs(
        insertedCount: Int,
        oldCard: BranchInventory,
        newCard: BranchInventory,
        movedBy: UUID,
        productId: UUID,
        branchId: UUID,
        branchDayId: UUID,
        quantity: Int,
        movementRow: InventoryMovement,
    ) {
        if (insertedCount > 0) {
            AuditLogRepository.record(
                tableName = BranchInventoryTable.tableName,
                recordId = newCard.id,
                action = AuditAction.UPDATE,
                changedBy = movedBy,
                oldValue =
                    AuditLogRepository.jsonFields(
                        "currentStock" to oldCard.currentStock.toString(),
                        "version" to oldCard.version.toString(),
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
    }

    fun recordMovement(params: RecordMovementParams): InventoryMovement =
        transaction {
            val card = requireCardForUpdate(params.branchId, params.productId, params.expectedVersion)

            val updatedCount =
                BranchInventoryTable.update({
                    (BranchInventoryTable.branchId eq params.branchId) and
                        (BranchInventoryTable.productId eq params.productId) and
                        (BranchInventoryTable.version eq params.expectedVersion)
                }) {
                    it[BranchInventoryTable.currentStock] = card.currentStock + params.quantityChange
                    it[BranchInventoryTable.version] = params.expectedVersion + 1
                }

            if (updatedCount == 0) {
                error("version_mismatch")
            }

            InventoryMovementTable.insertIgnore {
                it[InventoryMovementTable.id] = params.movementId
                it[InventoryMovementTable.productId] = params.productId
                it[InventoryMovementTable.branchId] = params.branchId
                it[InventoryMovementTable.branchDayId] = params.branchDayId
                it[InventoryMovementTable.reason] = params.reason
                it[InventoryMovementTable.quantityChange] = params.quantityChange
                it[InventoryMovementTable.movedBy] = params.movedBy
                it[InventoryMovementTable.movedAt] =
                    CurrentTimestampWithTimeZone
                if (params.notes != null) {
                    it[InventoryMovementTable.notes] = params.notes
                }
            }

            val movementRow =
                InventoryMovementTable
                    .selectAll()
                    .where { InventoryMovementTable.id eq params.movementId }
                    .single()
                    .toInventoryMovement()

            val newCard =
                findCardInTransaction(params.branchId, params.productId)
                    ?: error("inventory card not found after movement")

            writeMovementAuditLogs(
                card,
                newCard,
                params.movedBy,
                params.productId,
                params.branchId,
                params.branchDayId,
                params.reason,
                params.quantityChange,
                params.notes,
                movementRow,
            )
            movementRow
        }.also {
            logger.info {
                "[RECORD-MOVEMENT] reason=${params.reason} product=${params.productId} " +
                    "branch=${params.branchId} qty=${params.quantityChange}"
            }
        }

    @Suppress("LongParameterList")
    private fun writeMovementAuditLogs(
        oldCard: BranchInventory,
        newCard: BranchInventory,
        movedBy: UUID,
        productId: UUID,
        branchId: UUID,
        branchDayId: UUID,
        reason: InventoryMovementReason,
        quantityChange: Int,
        notes: String?,
        movementRow: InventoryMovement,
    ) {
        AuditLogRepository.record(
            tableName = BranchInventoryTable.tableName,
            recordId = newCard.id,
            action = AuditAction.UPDATE,
            changedBy = movedBy,
            oldValue =
                AuditLogRepository.jsonFields(
                    "currentStock" to oldCard.currentStock.toString(),
                    "version" to oldCard.version.toString(),
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
                    "reason" to reason.name,
                    "quantityChange" to quantityChange.toString(),
                    "notes" to (notes ?: ""),
                ),
        )
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

    private fun requireCardForUpdate(
        branchId: UUID,
        productId: UUID,
        expectedVersion: Int,
    ): BranchInventory {
        val card =
            findCardInTransaction(branchId, productId)
                ?: error("inventory card not found for branch=$branchId product=$productId")
        if (card.version != expectedVersion) error("version_mismatch")
        return card
    }

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

    private fun org.jetbrains.exposed.v1.core.ResultRow.toBranchInventory(): BranchInventory =
        BranchInventory(
            id = this[BranchInventoryTable.id],
            branchId = this[BranchInventoryTable.branchId],
            productId = this[BranchInventoryTable.productId],
            currentStock = this[BranchInventoryTable.currentStock],
            version = this[BranchInventoryTable.version],
        )

    private fun org.jetbrains.exposed.v1.core.ResultRow.toInventoryMovement(): InventoryMovement =
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
