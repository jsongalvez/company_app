package com.companyb.companyapp.service.inventory

import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.repository.model.InventoryMovement

/**
 * #442 — the 5-value inventory sheet breakdown (owner decision 2026-09-03).
 * Branch-scoped all-history; legacy rows included, no rewrite. Corrections ride
 * ADJUSTMENT with reason; the sheet reconciles as
 * Available (= live currentStock) = Stock - Sales - TesterSample - Missing + Adjustment.
 */
data class InventoryBreakdown(
    val stock: Int = 0,
    val sales: Int = 0,
    val testerSample: Int = 0,
    val missing: Int = 0,
    val adjustment: Int = 0,
)

internal fun breakdownFromMovements(movements: List<InventoryMovement>): InventoryBreakdown {
    var stock = 0
    var sales = 0
    var testerSample = 0
    var missing = 0
    var adjustment = 0
    movements.forEach { movement ->
        when (movement.reason) {
            InventoryMovementReason.RESTOCK -> stock += movement.quantityChange

            InventoryMovementReason.SALE -> sales += -movement.quantityChange

            InventoryMovementReason.TESTER,
            InventoryMovementReason.SAMPLE,
            -> testerSample += -movement.quantityChange

            InventoryMovementReason.MISSING -> missing += -movement.quantityChange

            InventoryMovementReason.ADJUSTMENT -> adjustment += movement.quantityChange
        }
    }
    return InventoryBreakdown(
        stock = stock,
        sales = sales,
        testerSample = testerSample,
        missing = missing,
        adjustment = adjustment,
    )
}
