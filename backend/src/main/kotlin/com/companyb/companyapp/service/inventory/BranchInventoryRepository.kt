package com.companyb.companyapp.service.inventory

import com.companyb.companyapp.logging.maskUUID
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

internal data class RestockParams(
    val movementId: UUID,
    val branchId: UUID,
    val productId: UUID,
    val quantity: Int,
    val branchDayId: UUID,
    val expectedVersion: Int,
    val movedBy: UUID,
)

internal data class RecordMovementParams(
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

internal data class RestockResult(
    val movement: InventoryMovement,
    val updatedStock: Int,
)

internal data class RestockAuditData(
    val movement: InventoryMovement,
    val oldCard: BranchInventory,
    val newCard: BranchInventory,
    val quantityAdded: Int,
)

internal data class MovementAuditData(
    val movement: InventoryMovement,
    val oldCard: BranchInventory,
    val newCard: BranchInventory,
    val reason: InventoryMovementReason,
    val quantityChange: Int,
    val notes: String?,
)

internal object BranchInventoryRepository {
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

    fun restock(
        params: RestockParams,
        auditFn: (RestockAuditData) -> Unit = {},
    ): RestockResult =
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

            auditFn(
                RestockAuditData(
                    movement = movementRow,
                    oldCard = card,
                    newCard = newCard,
                    quantityAdded = params.quantity,
                ),
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

    fun recordMovement(
        params: RecordMovementParams,
        auditFn: (MovementAuditData) -> Unit = {},
    ): InventoryMovement =
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

            auditFn(
                MovementAuditData(
                    movement = movementRow,
                    oldCard = card,
                    newCard = newCard,
                    reason = params.reason,
                    quantityChange = params.quantityChange,
                    notes = params.notes,
                ),
            )
            movementRow
        }.also {
            logger.info {
                "[RECORD-MOVEMENT] reason=${params.reason} product=${params.productId} " +
                    "branch=${params.branchId} qty=${params.quantityChange}"
            }
        }

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
