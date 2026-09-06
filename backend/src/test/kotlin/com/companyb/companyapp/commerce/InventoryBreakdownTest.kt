package com.companyb.companyapp.commerce

import com.companyb.companyapp.commerce.InventoryMovement
import com.companyb.companyapp.domain.InventoryMovementReason
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

// #442 — pure breakdown derivation: Restock baseline, Sale aggregate, Tester+Sample combined,
// Missing own column, Adjustment signed net. Available reconciles as
// Stock - Sales - TesterSample - Missing + Adjustment.
class InventoryBreakdownTest {
    private fun movement(
        reason: InventoryMovementReason,
        quantityChange: Int,
    ) = InventoryMovement(
        id = UUID.randomUUID(),
        productId = UUID.randomUUID(),
        branchId = UUID.randomUUID(),
        branchDayId = UUID.randomUUID(),
        reason = reason,
        quantityChange = quantityChange,
        movedBy = UUID.randomUUID(),
        movedAt = OffsetDateTime.now(),
        notes = null,
    )

    @Test
    fun combinesTesterAndSampleSeparatelyFromMissing() {
        val breakdown =
            breakdownFromMovements(
                listOf(
                    movement(InventoryMovementReason.RESTOCK, 20),
                    movement(InventoryMovementReason.SALE, -4),
                    movement(InventoryMovementReason.TESTER, -2),
                    movement(InventoryMovementReason.SAMPLE, -3),
                    movement(InventoryMovementReason.MISSING, -1),
                ),
            )
        assertEquals(20, breakdown.stock)
        assertEquals(4, breakdown.sales)
        assertEquals(5, breakdown.testerSample)
        assertEquals(1, breakdown.missing)
        assertEquals(0, breakdown.adjustment)
    }

    @Test
    fun adjustmentNetReconcilesAvailable() {
        val breakdown =
            breakdownFromMovements(
                listOf(
                    movement(InventoryMovementReason.RESTOCK, 10),
                    movement(InventoryMovementReason.ADJUSTMENT, 3),
                    movement(InventoryMovementReason.ADJUSTMENT, -1),
                ),
            )
        assertEquals(10, breakdown.stock)
        assertEquals(2, breakdown.adjustment)
        val available =
            breakdown.stock - breakdown.sales - breakdown.testerSample - breakdown.missing + breakdown.adjustment
        assertEquals(12, available)
    }

    @Test
    fun emptyLedgerYieldsZeros() {
        assertEquals(InventoryBreakdown(), breakdownFromMovements(emptyList()))
    }
}
