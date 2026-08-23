package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.InventoryMovementRequest
import com.companyb.companyapp.dto.ProductResponse
import com.companyb.companyapp.dto.RestockRequest
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.state.hasCapability
import java.util.UUID

/**
 * #392 — the inventory write flows' pure decision surface, pinned for desktopTest like the
 * #391 presentation mapper. Mirrors the backend's exact gates (#157 branch-scoped only):
 * restock requires MANAGE_PRODUCTS at the branch; Tester/Sample/Missing movements require
 * EDIT_BRANCH_DATA; Adjustment requires MANAGE_PRODUCTS (`BranchInventoryRoutes` route
 * filters are the authority — these predicates must never widen them).
 */
internal val NEGATIVE_MOVEMENT_REASONS: Set<InventoryMovementReason> =
    setOf(InventoryMovementReason.TESTER, InventoryMovementReason.SAMPLE, InventoryMovementReason.MISSING)

internal fun canRestock(
    capabilities: List<UserCapabilityResponse>,
    selectedBranchId: String?,
): Boolean = capabilities.hasCapability(CapabilityCodes.MANAGE_PRODUCTS, CapabilityContextType.BRANCH, selectedBranchId)

/**
 * #395 — the ensure-card route filter (`POST /branches/{id}/inventory` → MANAGE_PRODUCTS,
 * branch-scoped exact-scope) mirrored for the Add-card affordance. Happens to equal
 * [canRestock] today; kept separate so the two routes can diverge without a silent drift.
 * Unlike restock/movement there is NO day-state leg: the backend command opens no branch day.
 */
internal fun canEnsureCard(
    capabilities: List<UserCapabilityResponse>,
    selectedBranchId: String?,
): Boolean = capabilities.hasCapability(CapabilityCodes.MANAGE_PRODUCTS, CapabilityContextType.BRANCH, selectedBranchId)

/** Catalog products that don't yet have a card at this branch, in picker display order (#395). */
internal fun productsWithoutCards(
    products: List<ProductResponse>,
    cards: List<BranchInventoryResponse>,
): List<ProductResponse> {
    val carded = cards.map { it.productId }.toHashSet()
    return products.filter { it.id !in carded }.sortedBy { it.name.lowercase() }
}

/** The reasons this caller may record at the branch, in picker display order. */
internal fun allowedMovementReasons(
    capabilities: List<UserCapabilityResponse>,
    selectedBranchId: String?,
): List<InventoryMovementReason> =
    buildList {
        if (capabilities.hasCapability(
                CapabilityCodes.EDIT_BRANCH_DATA,
                CapabilityContextType.BRANCH,
                selectedBranchId,
            )
        ) {
            add(InventoryMovementReason.TESTER)
            add(InventoryMovementReason.SAMPLE)
            add(InventoryMovementReason.MISSING)
        }
        if (capabilities.hasCapability(
                CapabilityCodes.MANAGE_PRODUCTS,
                CapabilityContextType.BRANCH,
                selectedBranchId,
            )
        ) {
            add(InventoryMovementReason.ADJUSTMENT)
        }
    }

/**
 * Units-field validation mirroring the endpoint's 400s: Tester/Sample/Missing demand a
 * positive count (the client negates before sending); Adjustment takes any non-zero signed
 * quantity; the RESTOCK/SALE enum legs are unreachable through this dialog.
 */
internal fun movementUnitsError(
    reason: InventoryMovementReason,
    unitsText: String,
): String? {
    val units = unitsText.trim().toIntOrNull()
    val valid =
        if (reason == InventoryMovementReason.ADJUSTMENT) {
            units != 0
        } else {
            units != null && units > 0
        }
    return if (valid) {
        null
    } else if (reason == InventoryMovementReason.ADJUSTMENT) {
        "Enter a non-zero signed quantity"
    } else {
        "Enter a positive number of units"
    }
}

/** The endpoint's "Notes are required for MISSING movements" 400, mirrored client-side. */
internal fun movementNotesError(
    reason: InventoryMovementReason,
    notes: String?,
): String? =
    if (reason == InventoryMovementReason.MISSING && notes.isNullOrBlank()) {
        "Notes are required for Missing movements"
    } else {
        null
    }

internal fun restockUnitsError(unitsText: String): String? =
    if ((unitsText.trim().toIntOrNull() ?: 0) > 0) null else "Restock quantity must be positive"

/**
 * Presentation rule surface for the desktopTest packet: sort + display-line mapping (#391).
 * #396 — cards whose product id appears in the low-stock read's response render marked;
 * membership is backend-authoritative (per-product reorderPoint with a server default) and
 * the client mirrors nothing.
 */
internal fun List<BranchInventoryResponse>.toInventoryRows(
    lowStockIds: Set<String> = emptySet(),
): List<InventoryRowModel> =
    sortedBy { it.productName.lowercase() }.map { card ->
        InventoryRowModel(
            id = card.id,
            productName = card.productName,
            stockLine = "In stock: ${card.currentStock}",
            priceLine = "₱${card.unitPrice}",
            isLow = card.productId in lowStockIds,
        )
    }

internal data class InventoryRowModel(
    val id: String,
    val productName: String,
    val stockLine: String,
    val priceLine: String,
    val isLow: Boolean = false,
)

/**
 * #396 — the header count line, in display truth: counted against the cards actually shown
 * (a low-stock row naming a product with no displayed card never inflates the count).
 * null = nothing to surface (no low card, or no cards at all).
 */
internal fun lowStockSummaryLine(
    cards: List<BranchInventoryResponse>,
    lowStockIds: Set<String>,
): String? {
    if (cards.isEmpty()) return null
    val count = cards.count { it.productId in lowStockIds }
    return if (count > 0) "$count of ${cards.size} cards low on stock" else null
}

private fun normalizeOptional(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }

/** Everything [buildRestockRequest] needs; ids are generated fresh inside the builder. */
internal data class RestockDraft(
    val card: BranchInventoryResponse,
    val units: Int,
    val editReason: String?,
    val branchDayId: String,
)

/** Caller supplies validated, parsed units (positive); sent as-is per the restock contract. */
internal fun buildRestockRequest(draft: RestockDraft): RestockRequest =
    RestockRequest(
        id = UUID.randomUUID().toString(),
        quantity = draft.units,
        branchDayId = draft.branchDayId,
        editReason = normalizeOptional(draft.editReason),
    )

/** Everything [buildMovementRequest] needs; ids are generated fresh inside the builder. */
internal data class MovementDraft(
    val card: BranchInventoryResponse,
    val reason: InventoryMovementReason,
    val units: Int,
    val notes: String?,
    val editReason: String?,
    val branchDayId: String,
)

/**
 * Tester/Sample/Missing arrive as negative quantityChanges (the user enters units moved out);
 * Adjustment keeps its sign. [InventoryMovementRequest.expectedVersion] rides along unchanged
 * — the backend is lock-only today and ignores it (out-of-scope note on #392).
 */
internal fun buildMovementRequest(draft: MovementDraft): InventoryMovementRequest =
    InventoryMovementRequest(
        movementId = UUID.randomUUID().toString(),
        reason = draft.reason,
        quantityChange =
            if (draft.reason in NEGATIVE_MOVEMENT_REASONS) -draft.units else draft.units,
        notes = normalizeOptional(draft.notes),
        branchDayId = draft.branchDayId,
        expectedVersion = draft.card.version,
        editReason = normalizeOptional(draft.editReason),
    )
