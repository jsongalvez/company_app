package com.companyb.companyapp.commerce.stock

import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// #676 — lossless, spatially stable, client-first: filter, price lines, and retained-error
// guidance pinned pure (search-no-match vs low-stock-empty vs no-cards; unit/total with
// commission secondary; authoritative vs ambiguous failures with same-ID Retry).
class InventoryLosslessPolicyTest {
    private fun card(
        id: String = "card-1",
        productId: String = "product-1",
        name: String = "Alcohol",
    ) = BranchInventoryResponse(
        id = id,
        branchId = "branch-1",
        productId = productId,
        productName = name,
        currentStock = 10,
        version = 1,
        unitPrice = "150.50",
        commissionAmount = "10.00",
    )

    @Test
    fun filter_matchesNameCaseInsensitive_andNarrowsLowStock() {
        val cards =
            listOf(
                card("c-1", "p-1", "Alcohol"),
                card("c-2", "p-2", "Betadine"),
                card("c-3", "p-3", "aloe gel"),
            )
        assertEquals(3, filterInventoryCards(cards, "", false, emptySet()).size)
        assertEquals(listOf("aloe gel"), filterInventoryCards(cards, "ALOE", false, emptySet()).map { it.productName })
        assertEquals(
            listOf("Betadine"),
            filterInventoryCards(cards, "  bet  ", false, emptySet()).map { it.productName },
        )
        // Low-stock leg only narrows when enabled; disjoint ids narrow to nothing.
        assertEquals(3, filterInventoryCards(cards, "", false, setOf("p-9")).size)
        assertTrue(filterInventoryCards(cards, "", true, setOf("p-9")).isEmpty())
        assertEquals(
            listOf("p-2"),
            filterInventoryCards(cards, "", true, setOf("p-2")).map { it.productId },
        )
        // Combined: search-no-match among low-stock reads distinctly from low-stock-empty.
        assertTrue(filterInventoryCards(cards, "zzz", true, setOf("p-1", "p-2")).isEmpty())
        assertEquals(
            listOf("Alcohol"),
            filterInventoryCards(cards, "alc", true, setOf("p-1", "p-2")).map { it.productName },
        )
    }

    @Test
    fun salePrice_showsUnitTotalAndCommissionSecondary() {
        val display = salePriceDisplay(card(), 2)
        assertEquals("₱150.50 each", display.unitLine)
        assertEquals("Total ₱301.00 for 2", display.totalLine)
        assertEquals("Includes ₱10.00 commission", display.commissionLine)
    }

    @Test
    fun salePrice_unparseableReadsUnavailable_neverZero() {
        val broken = card().copy(unitPrice = "abc")
        val display = salePriceDisplay(broken, 1)
        assertEquals("Unit charge unavailable", display.unitLine)
        assertEquals("Total unavailable", display.totalLine)
        assertNull(display.commissionLine)
    }

    @Test
    fun salePrice_unparseableCommissionDiscloses_neverDrops() {
        val broken = card().copy(commissionAmount = "abc")
        val display = salePriceDisplay(broken, 1)
        assertEquals("₱150.50 each", display.unitLine)
        assertEquals("Commission unavailable", display.commissionLine)
    }

    @Test
    fun writeErrorHint_mapsKnownCases_ambiguousStaysNull() {
        assertTrue(writeErrorHint("sell failed: 404")!!.contains("no longer available"))
        assertTrue(writeErrorHint("restock failed: 409")!!.contains("Changed elsewhere"))
        assertTrue(writeErrorHint("movement failed: 403")!!.contains("Not authorized"))
        assertNull(writeErrorHint("sell failed: timeout"))
        assertNull(writeErrorHint("Insufficient stock"))
    }

    @Test
    fun ambiguousError_transportSignalsOnly_statusBearingIsAuthoritative() {
        assertFalse(isAmbiguousWriteError("sell failed: 404"))
        assertFalse(isAmbiguousWriteError("restock failed: 409"))
        assertFalse(isAmbiguousWriteError("movement failed: 403"))
        assertFalse(isAmbiguousWriteError("sell failed: 400"))
        assertFalse(isAmbiguousWriteError("Insufficient stock"))
        assertFalse(isAmbiguousWriteError("Branch day is not editable"))
        assertTrue(isAmbiguousWriteError("sell failed: timeout"))
        assertTrue(isAmbiguousWriteError("Unknown error"))
        assertTrue(isAmbiguousWriteError("Unable to resolve host"))
        assertEquals(
            "Could not confirm whether this was saved — Retry to reconcile.",
            AMBIGUOUS_WRITE_MESSAGE,
        )
    }

    @Test
    fun versionReconcile_triggersOnConflictOrShortStock_only() {
        assertTrue(needsVersionReconcile("restock failed: 409"))
        assertTrue(needsVersionReconcile("Insufficient stock"))
        assertFalse(needsVersionReconcile("sell failed: 404"))
        assertFalse(needsVersionReconcile("sell failed: timeout"))
    }

    @Test
    fun builders_reuseStableOperationId() {
        val draft =
            SaleDraft(
                card = card(),
                quantity = 1,
                clientId = "client-1",
                sessionId = null,
                isWalkIn = true,
                reason = null,
                branchDayId = "day-1",
                operationId = "op-stable",
            )
        // Same draft (same dialog instance) rebuilds the same idempotent key.
        assertEquals("op-stable", buildSaleRequest(draft).id)
        assertEquals("op-stable", buildSaleRequest(draft).id)
        val restock =
            buildRestockRequest(RestockDraft(card(), 3, null, "day-1", "op-restocks"))
        assertEquals("op-restocks", restock.id)
        val movement =
            buildMovementRequest(
                MovementDraft(
                    card(),
                    com.companyb.companyapp.contracts.commerce.InventoryMovementReason.TESTER,
                    2,
                    null,
                    null,
                    "day-1",
                    "op-move",
                ),
            )
        assertEquals("op-move", movement.movementId)
    }
}
