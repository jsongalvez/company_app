package com.companyb.companyapp.service.inventory

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.BranchInventoryRepository
import com.companyb.companyapp.repository.ProductRepository
import com.companyb.companyapp.repository.RecordMovementParams
import com.companyb.companyapp.repository.RecordMovementResult
import com.companyb.companyapp.repository.model.BranchInventory
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchInventoryWithProduct
import com.companyb.companyapp.repository.model.InventoryMovement
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.Product
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID

private const val LOW_STOCK_DEFAULT_THRESHOLD = 5

/**
 * Response-ready inventory aggregate (#454): cards plus their 5-value breakdowns,
 * loaded behind one seam so routes never orchestrate two service calls. Single
 * branch existence check and single movement-ledger scan per call.
 */
data class InventoryWithBreakdowns(
    val cards: List<BranchInventoryWithProduct>,
    val breakdowns: Map<UUID, InventoryBreakdown>,
)

/**
 * Inventory feature commands (#323, ADR-0024). Each mutating command owns exactly one
 * transaction: the day gate and guards run inside it, persistence runs via
 * `BranchInventoryRepository.*InTransaction` store operations, and the audit rows are
 * inserted into the same transaction — mutation + audit commit atomically or not at all.
 */
object InventoryService {
    private val logger = KotlinLogging.logger {}

    @Suppress("LongParameterList", "ThrowsCount", "ReturnCount", "LongMethod")
    fun recordMovement(
        callerId: UUID,
        movementId: UUID,
        branchId: UUID,
        productId: UUID,
        movementType: MovementType,
        quantityChange: Int,
        notes: String?,
        branchDayId: UUID,
        reason: String? = null,
    ): InventoryMovement {
        val movementReason = movementType.toInventoryMovementReason()

        val result =
            transaction {
                val existingMovement = BranchInventoryRepository.findMovementInTransaction(movementId)
                if (existingMovement != null) {
                    // Idempotent retries remain safe no-ops even if product was later deactivated.
                    ensureRequestOwnership(
                        existingMovement,
                        branchId,
                        productId,
                        movementReason,
                        quantityChange,
                        notes,
                        branchDayId,
                        callerId,
                    )
                    return@transaction RecordMovementResult(existingMovement, created = false)
                }

                if (BranchService.findByIdOrNull(branchId) == null) throw NotFoundException("Branch not found")
                BranchDayService.requireBranchDayForBranch(branchDayId, branchId)

                // #518 — day → product → card lock order (shared with ProductSaleService.sell):
                // the day gate locks the day row before the product row so a sale and a
                // movement on the same day serialize instead of deadlocking.
                val isRemitted =
                    StockValidator.validateMovement(
                        callerId,
                        branchDayId,
                        movementType,
                        quantityChange,
                        notes,
                        reason,
                    )

                requireActiveProductInTransaction(productId)

                // The card read that supplies expectedVersion now happens in this same command
                // transaction, fixing the former cross-boundary stale version read.
                val oldCard = BranchInventoryRepository.ensureCardInTransaction(branchId, productId).card

                val inserted =
                    BranchInventoryRepository.insertMovementInTransaction(
                        RecordMovementParams(
                            movementId = movementId,
                            branchId = branchId,
                            productId = productId,
                            reason = movementReason,
                            quantityChange = quantityChange,
                            notes = notes,
                            branchDayId = branchDayId,
                            movedBy = callerId,
                        ),
                    )
                if (!inserted.created) return@transaction inserted

                val newCard =
                    BranchInventoryRepository.requireCardForUpdateInTransaction(
                        oldCard,
                        oldCard.version,
                        quantityChange,
                    )

                val context = AuditContext(callerId, branchId, isRemitted, reason)
                BranchInventoryAudit.updated(context, oldCard, newCard)
                BranchInventoryAudit.inserted(context, inserted.movement)
                inserted
            }

        if (result.created) {
            logger.info {
                "[RECORD-MOVEMENT] $movementType product=$productId branch=$branchId qty=$quantityChange"
            }
        }
        return result.movement
    }

    fun getStock(branchId: UUID): List<BranchInventoryWithProduct> {
        InventoryReadSupport.requireBranchExists(branchId)
        return BranchInventoryRepository.findByBranch(branchId)
    }

    /**
     * #442 — branch-scoped all-history breakdown per product, derived from the movement
     * ledger (legacy rows included). Available stays the persisted live count
     * (`currentStock`, the low-stock/sale-guard authority); the map reconciles it as
     * Stock - Sales - TesterSample - Missing + Adjustment.
     */
    fun getBreakdowns(branchId: UUID): Map<UUID, InventoryBreakdown> {
        InventoryReadSupport.requireBranchExists(branchId)
        return InventoryReadSupport.breakdownsFromLedger(branchId)
    }

    fun getMovementHistory(
        branchId: UUID,
        date: LocalDate? = null,
    ): List<InventoryMovement> {
        InventoryReadSupport.requireBranchExists(branchId)
        return BranchInventoryRepository.findMovements(branchId, date)
    }

    /**
     * Aggregate read seam (#454): one branch lookup + one card scan + one movement
     * scan. Replaces the route-level `getBreakdowns` + `getStock` orchestration.
     */
    fun getInventory(branchId: UUID): InventoryWithBreakdowns {
        InventoryReadSupport.requireBranchExists(branchId)
        val cards = BranchInventoryRepository.findByBranch(branchId)
        val breakdowns = InventoryReadSupport.breakdownsFromLedger(branchId)
        return InventoryWithBreakdowns(cards, breakdowns)
    }

    fun getLowStockAlerts(
        branchId: UUID,
        thresholdOverride: Int? = null,
    ): List<BranchInventoryWithProduct> {
        InventoryReadSupport.requireBranchExists(branchId)
        val allInventory = BranchInventoryRepository.findByBranch(branchId)
        return InventoryReadSupport.filterLowStock(allInventory, thresholdOverride)
    }

    /**
     * Low-stock aggregate read seam (#454): one branch lookup + one card scan + one
     * movement scan. Filtered cards paired with the full breakdown map, matching the
     * former route-level `getBreakdowns` + `getLowStockAlerts` pairing.
     */
    fun getLowStockInventory(
        branchId: UUID,
        thresholdOverride: Int? = null,
    ): InventoryWithBreakdowns {
        InventoryReadSupport.requireBranchExists(branchId)
        val cards =
            InventoryReadSupport.filterLowStock(
                BranchInventoryRepository.findByBranch(branchId),
                thresholdOverride,
            )
        val breakdowns = InventoryReadSupport.breakdownsFromLedger(branchId)
        return InventoryWithBreakdowns(cards, breakdowns)
    }

    @Suppress("ComplexCondition", "LongParameterList")
    private fun ensureRequestOwnership(
        existingMovement: InventoryMovement,
        branchId: UUID,
        productId: UUID,
        movementReason: InventoryMovementReason,
        quantityChange: Int,
        notes: String?,
        branchDayId: UUID,
        callerId: UUID,
    ) {
        if (
            existingMovement.branchId != branchId ||
            existingMovement.productId != productId ||
            existingMovement.reason != movementReason ||
            existingMovement.quantityChange != quantityChange ||
            existingMovement.notes != notes ||
            existingMovement.branchDayId != branchDayId ||
            existingMovement.movedBy != callerId
        ) {
            throw ConflictException("Movement ID already belongs to another request")
        }
    }

    @Suppress("ThrowsCount")
    fun ensureCard(
        callerId: UUID,
        branchId: UUID,
        productId: UUID,
    ): BranchInventory =
        transaction {
            if (BranchService.findByIdOrNull(branchId) == null) throw NotFoundException("Branch not found")
            requireActiveProductInTransaction(productId)

            val result = BranchInventoryRepository.ensureCardInTransaction(branchId, productId)
            val context = AuditContext(callerId, branchId)
            if (result.created) {
                BranchInventoryAudit.inserted(context, result.card)
            } else {
                BranchInventoryAudit.updated(context, result.card, result.card)
            }
            result.card
        }.also {
            logger.info { "[ENSURE-CARD] Inventory card ensured for branch=$branchId product=$productId" }
        }

    private fun requireActiveProductInTransaction(productId: UUID): Product =
        ProductRepository
            .findByIdForUpdateInTransaction(productId)
            ?.takeIf { it.isActive }
            ?: throw NotFoundException("Product not found")
}

/**
 * Read-only inventory helpers (#460 governance: keeps `InventoryService` at 10 functions,
 * under the `TooManyFunctions` 11 budget — the aggregate-read seam stays on the service,
 * the branch lookup + ledger scan + low-stock filter live here, same file).
 */
internal object InventoryReadSupport {
    fun requireBranchExists(branchId: UUID) {
        if (BranchService.findByIdOrNull(branchId) == null) throw NotFoundException("Branch not found")
    }

    fun breakdownsFromLedger(branchId: UUID): Map<UUID, InventoryBreakdown> =
        BranchInventoryRepository
            .findMovements(branchId)
            .groupBy { it.productId }
            .mapValues { (_, movements) -> breakdownFromMovements(movements) }

    @Suppress("ReturnCount")
    fun filterLowStock(
        allInventory: List<BranchInventoryWithProduct>,
        thresholdOverride: Int?,
    ): List<BranchInventoryWithProduct> {
        if (allInventory.isEmpty()) return allInventory

        if (thresholdOverride != null) {
            return allInventory.filter { it.inventory.currentStock <= thresholdOverride }
        }

        val productIds = allInventory.map { it.inventory.productId }
        val products = ProductRepository.findByIds(productIds)
        val productThresholds =
            products.associate { product ->
                product.id to resolveThreshold(product)
            }
        return allInventory.filter { item ->
            val threshold =
                productThresholds[item.inventory.productId]
                    ?: LOW_STOCK_DEFAULT_THRESHOLD
            item.inventory.currentStock <= threshold
        }
    }

    private fun resolveThreshold(product: Product): Int = product.reorderPoint ?: LOW_STOCK_DEFAULT_THRESHOLD
}

/**
 * Inventory audit vocabulary (#323, ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit rows commit atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object BranchInventoryAudit {
    fun updated(
        context: AuditContext,
        before: BranchInventory,
        after: BranchInventory,
    ) = AuditLog.recordUpdate(
        tableName = BranchInventoryTable.tableName,
        recordId = after.id,
        before = before,
        after = after,
        changedBy = context.changedBy,
        branchId = context.branchId,
        isFlagged = context.isFlagged,
        reason = context.reason,
        auditFields = BranchInventoryTable::auditFields,
    )

    fun inserted(
        context: AuditContext,
        movement: InventoryMovement,
    ) = AuditLog.recordInsert(
        tableName = InventoryMovementTable.tableName,
        recordId = movement.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = InventoryMovementTable.auditFields(movement),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )

    /** INSERT vocabulary for the inventory card itself (#393 — ensure-card audit gap). */
    fun inserted(
        context: AuditContext,
        card: BranchInventory,
    ) = AuditLog.recordInsert(
        tableName = BranchInventoryTable.tableName,
        recordId = card.id,
        changedBy = context.changedBy,
        branchId = context.branchId,
        fields = BranchInventoryTable.auditFields(card),
        isFlagged = context.isFlagged,
        reason = context.reason,
    )
}
