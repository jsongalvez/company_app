package com.companyb.companyapp.commerce.stock

import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
import kotlin.test.Test
import kotlin.test.assertEquals

// #727 — identity anchor resolution: the retained card id resolves against the currently
// filtered cards; removed or filtered-out anchors fall back to the list start, never a
// blank or impossible scroll state.
class InventoryContextPolicyTest {
    private fun card(id: String) =
        BranchInventoryResponse(
            id = id,
            branchId = "branch-1",
            productId = "product-$id",
            productName = "Product $id",
            currentStock = 10,
            version = 1,
            unitPrice = "150.50",
            commissionAmount = "10.00",
        )

    @Test
    fun anchor_resolvesToMatchingIndex() {
        val cards = listOf(card("c-1"), card("c-2"), card("c-3"))

        assertEquals(2, inventoryAnchorIndex(cards, "c-3"))
        assertEquals(0, inventoryAnchorIndex(cards, "c-1"))
    }

    @Test
    fun anchor_missingFallsBackToStart() {
        val cards = listOf(card("c-1"), card("c-2"))

        assertEquals(0, inventoryAnchorIndex(cards, "c-9"))
    }

    @Test
    fun anchor_nullAndEmptyFallBackToStart() {
        val cards = listOf(card("c-1"), card("c-2"))

        assertEquals(0, inventoryAnchorIndex(cards, null))
        assertEquals(0, inventoryAnchorIndex(emptyList(), "c-1"))
        assertEquals(0, inventoryAnchorIndex(emptyList(), null))
    }
}
