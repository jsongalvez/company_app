package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditedRow
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

private const val LOW_STOCK_DEFAULT_THRESHOLD = 5

/**
 * Response-ready inventory aggregate: cards plus their 5-value breakdowns,
 * loaded behind one seam so routes never orchestrate two service calls. Single
 * branch existence check and single movement-ledger scan per call.
 */
data class InventoryWithBreakdowns(
    val cards: List<BranchInventoryWithProduct>,
    val breakdowns: Map<UUID, InventoryBreakdown>,
)

/**
 * Inventory feature commands (ADR-0024). Each mutating command owns exactly one
 * transaction: the day gate and guards run inside it, persistence runs via
 * `BranchInventoryRepository.*InTransaction` store operations, and the audit rows are
 * inserted into the same transaction — mutation + audit commit atomically or not at all.
 */
object InventoryService {
    private val logger = KotlinLogging.logger {}

    // 9-param movement command stays whole; the single command-owned transaction
    // (ADR-0024) keeps day gate + card lock + audit atomic — splitting would break atomicity.
    @Suppress("LongParameterList", "LongMethod") // #597
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
        val params =
            RecordMovementParams(
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                reason = movementReason,
                quantityChange = quantityChange,
                notes = notes,
                branchDayId = branchDayId,
                movedBy = callerId,
            )

        val result =
            transaction {
                val existingMovement = BranchInventoryRepository.findMovementInTransaction(movementId)
                if (existingMovement != null) {
                    // Idempotent retries remain safe no-ops even if product was later deactivated.
                    ensureRequestOwnership(existingMovement, params)
                    return@transaction RecordMovementResult(existingMovement, created = false)
                }

                if (BranchService.findByIdOrNull(branchId) == null) throw NotFoundException("Branch not found")
                BranchDayService.requireBranchDayForBranch(branchDayId, branchId)

                // day → product → card lock order (shared with ProductSaleService.sell):
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
                    BranchInventoryRepository.insertMovementInTransaction(params)
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
     * branch-scoped all-history breakdown per product, derived from the movement
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
        before: OffsetDateTime? = null,
    ): List<InventoryMovement> {
        InventoryReadSupport.requireBranchExists(branchId)
        return BranchInventoryRepository.findMovements(branchId, date, before)
    }

    /**
     * Aggregate read seam: one branch lookup + one card scan + one movement
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
     * Low-stock aggregate read seam: one branch lookup + one card scan + one
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

    private fun ensureRequestOwnership(
        existingMovement: InventoryMovement,
        request: RecordMovementParams,
    ) {
        val sameIdentity =
            existingMovement.branchId == request.branchId &&
                existingMovement.productId == request.productId &&
                existingMovement.branchDayId == request.branchDayId &&
                existingMovement.movedBy == request.movedBy
        val samePayload =
            existingMovement.reason == request.reason &&
                existingMovement.quantityChange == request.quantityChange &&
                existingMovement.notes == request.notes
        if (!sameIdentity || !samePayload) {
            throw ConflictException("Movement ID already belongs to another request")
        }
    }

    fun ensureCard(
        callerId: UUID,
        branchId: UUID,
        productId: UUID,
    ): BranchInventory =
        transaction {
            if (BranchService.findByIdOrNull(branchId) == null) throw NotFoundException("Branch not found")
            requireActiveProductInTransaction(productId)

            val result = BranchInventoryRepository.ensureCardInTransaction(branchId, productId)
            if (result.created) {
                BranchInventoryAudit.inserted(AuditContext(callerId, branchId), result.card)
            }
            // No audit on existing card: audit log is immutable mutation history.
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
 * Inventory audit vocabulary (ADR-0024 rule 3). Called by the command inside its own
 * transaction so the audit rows commit atomically with the mutation. Owns the persistence-table
 * imports so the public command surface does not.
 */
internal object BranchInventoryAudit {
    fun updated(
        context: AuditContext,
        before: BranchInventory,
        after: BranchInventory,
    ) = AuditLog.recordUpdate(
        context = context,
        row = AuditedRow(BranchInventoryTable.tableName, after.id),
        before = before,
        after = after,
        auditFields = BranchInventoryTable::auditFields,
    )

    fun inserted(
        context: AuditContext,
        movement: InventoryMovement,
    ) = AuditLog.recordInsert(
        context = context,
        row = AuditedRow(InventoryMovementTable.tableName, movement.id),
        fields = InventoryMovementTable.auditFields(movement),
    )

    /** INSERT vocabulary for the inventory card itself (ensure-card audit gap). */
    fun inserted(
        context: AuditContext,
        card: BranchInventory,
    ) = AuditLog.recordInsert(
        context = context,
        row = AuditedRow(BranchInventoryTable.tableName, card.id),
        fields = BranchInventoryTable.auditFields(card),
    )
}
