package com.companyb.companyapp.repository

import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.repository.model.BranchInventory
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductTable
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.vendors.ForUpdateOption
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
    val movedBy: UUID,
)

data class RecordMovementResult(
    val movement: InventoryMovement,
    val created: Boolean,
)

data class EnsureCardResult(
    val card: BranchInventory,
    val created: Boolean,
)

@Suppress("TooManyFunctions")
object BranchInventoryRepository {
    fun requireCardForUpdateInTransaction(
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

    /**
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction.
     * Idempotently materializes the inventory card and reports whether this call created it
     * (via `insertedCount`, exact under concurrent ensure races); the audit rows stay the
     * command's job.
     */
    fun ensureCardInTransaction(
        branchId: UUID,
        productId: UUID,
    ): EnsureCardResult {
        findCardInTransaction(branchId, productId)?.let { return EnsureCardResult(it, created = false) }

        val inserted =
            BranchInventoryTable.insertIgnore {
                it[BranchInventoryTable.branchId] = branchId
                it[BranchInventoryTable.productId] = productId
                it[BranchInventoryTable.currentStock] = 0
                it[BranchInventoryTable.version] = 1
            }

        return EnsureCardResult(
            findCardInTransaction(branchId, productId)
                ?: error("inventory card not found after idempotent insert for branch=$branchId product=$productId"),
            created = inserted.insertedCount > 0,
        )
    }

    /**
     * In-transaction store operation (#323, ADR-0024) — idempotent movement-row write with the
     * duplicate-request classification kept verbatim from the retired recordMovement wrapper:
     * a lost insert race re-reads the row and either returns it (same request) or conflicts.
     * The caller performs the stock update and audit writes only when [RecordMovementResult.created].
     */
    fun insertMovementInTransaction(params: RecordMovementParams): RecordMovementResult {
        val inserted =
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
        val wasInserted = inserted.insertedCount > 0

        if (!wasInserted) {
            val existingMovement = findMovementInTransaction(params.movementId) ?: error("movement disappeared")
            if (!sameMovementRequest(existingMovement, params)) {
                throw ConflictException("Movement ID already belongs to another request")
            }
            return RecordMovementResult(existingMovement, created = false)
        }
        return RecordMovementResult(
            findMovementInTransaction(params.movementId) ?: error("movement disappeared"),
            created = true,
        )
    }

    fun findMovementById(movementId: UUID): InventoryMovement? =
        transaction {
            findMovementInTransaction(movementId)
        }

    /** In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction. */
    fun findMovementInTransaction(movementId: UUID): InventoryMovement? =
        InventoryMovementTable
            .selectAll()
            .where { InventoryMovementTable.id eq movementId }
            .singleOrNull()
            ?.toInventoryMovement()

    @Suppress("ComplexCondition")
    private fun sameMovementRequest(
        existing: InventoryMovement,
        params: RecordMovementParams,
    ): Boolean =
        existing.branchId == params.branchId &&
            existing.productId == params.productId &&
            existing.branchDayId == params.branchDayId &&
            existing.reason == params.reason &&
            existing.quantityChange == params.quantityChange &&
            existing.notes == params.notes &&
            existing.movedBy == params.movedBy

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

    /**
     * In-transaction store operation (#323, ADR-0024) — runs on the caller's command transaction.
     * Folds the former acquireInventoryLock + read pair into one locked read: a row-level
     * branch_inventory lock prevents TOCTOU races on concurrent stock checks during sales
     * (CR-018 D2). The terminal op materializes the FOR UPDATE lock (#136).
     */
    fun findCardForUpdateInTransaction(
        branchId: UUID,
        productId: UUID,
    ): BranchInventory? =
        BranchInventoryTable
            .selectAll()
            .where {
                (BranchInventoryTable.branchId eq branchId) and
                    (BranchInventoryTable.productId eq productId)
            }.forUpdate(ForUpdateOption.ForUpdate)
            .singleOrNull()
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
                ).innerJoin(
                    ProductTable,
                    { InventoryMovementTable.productId },
                    { ProductTable.id },
                ).selectAll()
                .where {
                    if (date != null) {
                        (InventoryMovementTable.branchId eq branchId) and
                            (BranchDayTable.date eq date) and
                            (ProductTable.isActive eq true)
                    } else {
                        (InventoryMovementTable.branchId eq branchId) and
                            (ProductTable.isActive eq true)
                    }
                }.orderBy(
                    InventoryMovementTable.movedAt to SortOrder.DESC,
                    InventoryMovementTable.id to SortOrder.DESC,
                ).map { it.toInventoryMovement() }
        }.also {
            logger.info {
                "[FIND-MOVEMENTS] Fetched ${it.size} movement(s) for branch $branchId " +
                    "date=${date?.toString().orEmpty()}"
            }
        }

    fun findByBranch(branchId: UUID): List<BranchInventoryWithProduct> =
        transaction {
            BranchInventoryTable
                .innerJoin(
                    ProductTable,
                    { BranchInventoryTable.productId },
                    { ProductTable.id },
                ).selectAll()
                .where {
                    (BranchInventoryTable.branchId eq branchId) and
                        (ProductTable.isActive eq true)
                }.orderBy(ProductTable.name to SortOrder.ASC)
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
                        (ProductTable.isActive eq true) and
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
