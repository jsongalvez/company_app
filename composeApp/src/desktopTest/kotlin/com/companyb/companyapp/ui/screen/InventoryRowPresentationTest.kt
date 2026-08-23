package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.dto.BranchInventoryResponse
import kotlin.test.Test
import kotlin.test.assertEquals

// #391 — the read-only inventory list's presentation rules, pinned pure: rows sort by
// product name case-insensitively and render stock + peso price lines from the card DTO.
class InventoryRowPresentationTest {
    private fun card(
        id: String,
        productName: String,
        currentStock: Int,
        unitPrice: String,
    ) = BranchInventoryResponse(
        id = id,
        branchId = "branch-1",
        productId = "product-$id",
        productName = productName,
        currentStock = currentStock,
        version = 1,
        unitPrice = unitPrice,
        commissionAmount = "0",
    )

    @Test
    fun rowsSortByProductName_caseInsensitive() {
        val rows =
            listOf(
                card("3", "aloe gel", 5, "120.00"),
                card("2", "Betadine", 2, "80.00"),
                card("1", "Alcohol", 10, "150.50"),
            ).toInventoryRows()
        assertEquals(listOf("Alcohol", "aloe gel", "Betadine"), rows.map { it.productName })
    }

    @Test
    fun rowLines_renderStockAndPesoPrice() {
        val rows = listOf(card("1", "Alcohol", 10, "150.50")).toInventoryRows()
        assertEquals(1, rows.size)
        assertEquals("In stock: 10", rows[0].stockLine)
        assertEquals("₱150.50", rows[0].priceLine)
    }

    @Test
    fun rowIds_stayStableForLazyColumnKeys() {
        val rows =
            listOf(
                card("a", "X", 1, "1.00"),
                card("b", "Y", 2, "2.00"),
            ).toInventoryRows()
        assertEquals(listOf("a", "b"), rows.map { it.id })
    }
}
