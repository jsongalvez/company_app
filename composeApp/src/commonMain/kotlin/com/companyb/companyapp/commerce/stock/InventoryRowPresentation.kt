package com.companyb.companyapp.commerce.stock

import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
import com.companyb.companyapp.contracts.commerce.InventoryMovementResponse

/**
 * #391 — the Inventory presentation rule surface, pinned by the desktopTest packet: sort +
 * display-line mapping. #396 — cards whose product id appears in the low-stock read's response
 * render marked; membership is backend-authoritative (per-product reorderPoint with a server
 * default) and the client mirrors nothing.
 * #442 — 5-value sheet (owner decision 2026-09-03): Available (live sellable = currentStock,
 * the low-stock authority) · Stock (restock baseline) · Sales · Tester/Sample combined
 * (movements stay separate) · Missing. Adjustment rides the ledger so Available reconciles;
 * appended only when non-zero to keep the default 5-value display.
 */
internal fun List<BranchInventoryResponse>.toInventoryRows(
    lowStockIds: Set<String> = emptySet(),
): List<InventoryRowModel> =
    sortedBy { it.productName.lowercase() }.map { card ->
        InventoryRowModel(
            id = card.id,
            productName = card.productName,
            stockLine = inventoryBreakdownLine(card),
            priceLine = "₱${card.unitPrice}",
            isLow = card.productId in lowStockIds,
        )
    }

internal fun inventoryBreakdownLine(card: BranchInventoryResponse): String {
    val base =
        "Available: ${card.currentStock} · Stock: ${card.stock}" +
            " · Sales: ${card.sales} · Tester/Sample: ${card.testerSample} · Missing: ${card.missing}"
    if (card.adjustment == 0) return base
    val signed = if (card.adjustment > 0) "+${card.adjustment}" else card.adjustment.toString()
    return "$base · Adjustment: $signed"
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

/** Signed display form: restocks/adjustments gain a "+", draws keep the backend's negative. */
internal fun signedQuantityLine(quantityChange: Int): String =
    if (quantityChange > 0) "+$quantityChange" else quantityChange.toString()

/**
 * #397 — movements-history presentation rows. Order is preserved (the backend answers
 * `movedAt DESC`); product names resolve from the loaded inventory cards keyed by productId,
 * falling back to the raw id when no displayed card carries it.
 */
internal fun List<InventoryMovementResponse>.toMovementRows(
    cards: List<BranchInventoryResponse>,
): List<MovementRowModel> {
    val names = cards.associate { it.productId to it.productName }
    return map { movement ->
        MovementRowModel(
            id = movement.id,
            productName = names[movement.productId] ?: movement.productId,
            reasonLine = "${reasonLabel(movement.reason)} · ${signedQuantityLine(movement.quantityChange)}",
            movedAt = movement.movedAt,
            notes = normalizeOptional(movement.notes),
        )
    }
}

internal data class MovementRowModel(
    val id: String,
    val productName: String,
    val reasonLine: String,
    val movedAt: String,
    val notes: String? = null,
)
