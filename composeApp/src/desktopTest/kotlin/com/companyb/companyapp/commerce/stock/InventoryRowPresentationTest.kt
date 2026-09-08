package com.companyb.companyapp.commerce.stock

import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
import kotlin.test.Test
import kotlin.test.assertEquals

// #391 — the read-only inventory list's presentation rules, pinned pure: rows sort by
// product name case-insensitively and render stock + peso price lines from the card DTO.
// #442 — 5-value sheet: Available (live) · Stock · Sales · Tester/Sample · Missing.
class InventoryRowPresentationTest {
    // #597: 9-param card builder mirrors BranchInventoryResponse 1:1 for pure presentation pins; stays whole.
    @Suppress("LongParameterList") // #597
    private fun card(
        id: String,
        productName: String,
        currentStock: Int,
        unitPrice: String,
        stock: Int = 0,
        sales: Int = 0,
        testerSample: Int = 0,
        missing: Int = 0,
        adjustment: Int = 0,
    ) = BranchInventoryResponse(
        id = id,
        branchId = "branch-1",
        productId = "product-$id",
        productName = productName,
        currentStock = currentStock,
        version = 1,
        unitPrice = unitPrice,
        commissionAmount = "0",
        available = currentStock,
        stock = stock,
        sales = sales,
        testerSample = testerSample,
        missing = missing,
        adjustment = adjustment,
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
    fun rowLines_renderBreakdownAndPesoPrice() {
        val rows =
            listOf(
                card("1", "Alcohol", 10, "150.50", stock = 12, sales = 1, testerSample = 1, missing = 0),
            ).toInventoryRows()
        assertEquals(1, rows.size)
        assertEquals(
            "Available: 10 · Stock: 12 · Sales: 1 · Tester/Sample: 1 · Missing: 0",
            rows[0].stockLine,
        )
        assertEquals("₱150.50", rows[0].priceLine)
    }

    @Test
    fun rowLines_appendAdjustmentOnlyWhenNonZero() {
        val withoutAdjustment = listOf(card("1", "Alcohol", 10, "1.00")).toInventoryRows()
        assertEquals(
            "Available: 10 · Stock: 0 · Sales: 0 · Tester/Sample: 0 · Missing: 0",
            withoutAdjustment[0].stockLine,
        )
        val withAdjustment =
            listOf(
                card("1", "Alcohol", 12, "1.00", stock = 10, adjustment = 2),
            ).toInventoryRows()
        assertEquals(
            "Available: 12 · Stock: 10 · Sales: 0 · Tester/Sample: 0 · Missing: 0 · Adjustment: +2",
            withAdjustment[0].stockLine,
        )
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
