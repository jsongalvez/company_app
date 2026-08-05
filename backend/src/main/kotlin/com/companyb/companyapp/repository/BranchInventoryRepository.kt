package com.companyb.companyapp.repository

import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventory
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.repository.model.InventoryMovementReason
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.util.UUID

private val logger = KotlinLogging.logger {}

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

data class MovementAuditData(
    val movement: InventoryMovement,
    val oldCard: BranchInventory,
    val newCard: BranchInventory,
    val reason: InventoryMovementReason,
    val quantityChange: Int,
    val notes: String?,
)

object BranchInventoryRepository {
    fun requireCardForUpdate(
        oldCard: BranchInventory,
        expectedVersion: Int,
        delta: Int,
    ): BranchInventory {
        if (oldCard.version != expectedVersion) {
            throw VersionMismatchException(BranchInventoryTable.tableName, oldCard.id)
        }

        val newStock = oldCard.currentStock + delta
        val updatedCount =
            BranchInventoryTable.update({
                (BranchInventoryTable.branchId eq oldCard.branchId) and
                    (BranchInventoryTable.productId eq oldCard.productId) and
                    (BranchInventoryTable.version eq expectedVersion)
            }) {
                it[BranchInventoryTable.currentStock] = newStock
                it[BranchInventoryTable.version] = expectedVersion + 1
            }

        if (updatedCount == 0) {
            throw VersionMismatchException(BranchInventoryTable.tableName, oldCard.id)
        }

        return oldCard.copy(currentStock = newStock, version = expectedVersion + 1)
    }

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

    fun recordMovement(
        params: RecordMovementParams,
        auditFn: (MovementAuditData) -> Unit = {},
    ): InventoryMovement =
        transaction {
            val oldCard =
                findCardInTransaction(params.branchId, params.productId)
                    ?: error("inventory card not found for branch=${params.branchId} product=${params.productId}")

            val newCard =
                requireCardForUpdate(
                    oldCard,
                    params.expectedVersion,
                    params.quantityChange,
                )

            InventoryMovementTable.insertIgnore {
                it[InventoryMovementTable.id] = params.movementId
                it[InventoryMovementTable.productId] = params.productId
                it[InventoryMovementTable.branchId] = params.branchId
                it[InventoryMovementTable.branchDayId] = params.branchDayId
                it[InventoryMovementTable.reason] = params.reason
                it[InventoryMovementTable.quantityChange] = params.quantityChange
                it[InventoryMovementTable.movedBy] = params.movedBy
                it[InventoryMovementTable.movedAt] = CurrentTimestampWithTimeZone
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

            auditFn(
                MovementAuditData(
                    movement = movementRow,
                    oldCard = oldCard,
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

    fun findMovements(
        branchId: UUID,
        date: LocalDate? = null,
    ): List<InventoryMovement> =
        transaction {
            InventoryMovementTable
                .innerJoin(
                    BranchDayTable,
                    { InventoryMovementTable.branchDayId },
                    { BranchDayTable.id },
                ).selectAll()
                .where {
                    if (date != null) {
                        (InventoryMovementTable.branchId eq branchId) and
                            (BranchDayTable.date eq date)
                    } else {
                        InventoryMovementTable.branchId eq branchId
                    }
                }.orderBy(
                    InventoryMovementTable.movedAt to SortOrder.DESC,
                    InventoryMovementTable.id to SortOrder.DESC,
                ).map { it.toInventoryMovement() }
        }.also { logger.info { "[FIND-MOVEMENTS] Fetched ${it.size} movement(s) for branch $branchId date=$date" } }

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
                        unitPrice = row[ProductTable.unitPrice],
                        commissionAmount = row[ProductTable.commissionAmount],
                    )
                }
        }.also { logger.info { "[FIND-INVENTORY] Fetched ${it.size} inventory card(s) for branch $branchId" } }

    fun findByBranchLowStock(
        branchId: UUID,
        threshold: Int,
    ): List<BranchInventoryWithProduct> =
        transaction {
            BranchInventoryTable
                .innerJoin(
                    ProductTable,
                    { BranchInventoryTable.productId },
                    { ProductTable.id },
                ).selectAll()
                .where {
                    (BranchInventoryTable.branchId eq branchId) and
                        (BranchInventoryTable.currentStock lessEq threshold)
                }.orderBy(ProductTable.name to SortOrder.ASC)
                .map { row ->
                    BranchInventoryWithProduct(
                        inventory = row.toBranchInventory(),
                        productName = row[ProductTable.name],
                        unitPrice = row[ProductTable.unitPrice],
                        commissionAmount = row[ProductTable.commissionAmount],
                    )
                }
        }.also {
            logger.info {
                "[FIND-LOW-STOCK] Fetched ${it.size} low-stock card(s) for branch $branchId (threshold=$threshold)"
            }
        }

    private fun ResultRow.toBranchInventory(): BranchInventory =
        BranchInventory(
            id = this[BranchInventoryTable.id],
            branchId = this[BranchInventoryTable.branchId],
            productId = this[BranchInventoryTable.productId],
            currentStock = this[BranchInventoryTable.currentStock],
            version = this[BranchInventoryTable.version],
        )

    private fun ResultRow.toInventoryMovement(): InventoryMovement =
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
