package com.companyb.companyapp.ui.screen

import com.companyb.companyapp.dto.ProductCategoryResponse
import com.companyb.companyapp.dto.ProductResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductCatalogLogicTest {
    private fun category(
        id: String = "c1",
        name: String = "Oils",
    ) = ProductCategoryResponse(id = id, name = name)

    private fun product(
        id: String = "p1",
        name: String = "Big Roll On",
        categoryId: String = "c1",
        isActive: Boolean = true,
    ) = ProductResponse(
        id = id,
        name = name,
        productCategoryId = categoryId,
        isActive = isActive,
        unitPrice = "2500.00",
        commissionAmount = "100.00",
    )

    @Test
    fun categoryName_blankIsRejected() {
        assertTrue(catalogCategoryNameError("").toString().isNotEmpty())
        assertTrue(catalogCategoryNameError("   ").toString().isNotEmpty())
        assertNull(catalogCategoryNameError("Essential Oil"))
    }

    @Test
    fun productName_blankIsRejected() {
        assertTrue(catalogProductNameError("").toString().isNotEmpty())
        assertNull(catalogProductNameError("MagSpray"))
    }

    @Test
    fun money_blankUnparseableNegativeRejected() {
        assertTrue(catalogMoneyError("", "price").toString().isNotEmpty())
        assertTrue(catalogMoneyError("abc", "price").toString().isNotEmpty())
        assertTrue(catalogMoneyError("-5", "price").toString().isNotEmpty())
        assertTrue(catalogMoneyError("Infinity", "price").toString().isNotEmpty())
    }

    @Test
    fun money_zeroAndPositiveAccepted() {
        assertNull(catalogMoneyError("0", "commission"))
        assertNull(catalogMoneyError("2500.00", "price"))
    }

    @Test
    fun categoryName_unknownIdFailsClosed() {
        assertEquals("Oils", catalogCategoryName("c1", listOf(category())))
        assertEquals("Unknown category", catalogCategoryName("missing", listOf(category())))
    }

    @Test
    fun merge_createdRowFillsGapAndSorts() {
        val merged =
            mergeCatalogProducts(
                loaded = listOf(product(name = "Zed")),
                overlays = mapOf("p2" to product(id = "p2", name = "Alpha")),
            )
        assertEquals(listOf("Alpha", "Zed"), merged.map { it.name })
    }

    @Test
    fun merge_serverRowWinsOverOverlay() {
        // A reload that returns the row supersedes the overlay (freshest truth, no masking).
        val merged =
            mergeCatalogProducts(
                loaded = listOf(product(name = "Server Name")),
                overlays = mapOf("p1" to product(name = "Stale Name")),
            )
        assertEquals(listOf("Server Name"), merged.map { it.name })
    }

    @Test
    fun merge_deactivatedRowStaysVisible() {
        // The collection read is active-only: the update landing keeps the row present as inactive.
        val merged =
            mergeCatalogProducts(
                loaded = emptyList(),
                overlays = mapOf("p1" to product(isActive = false)),
            )
        assertEquals(1, merged.size)
        assertEquals(false, merged.single().isActive)
    }

    @Test
    fun filter_nullReturnsAll() {
        val products = listOf(product(), product(id = "p2", categoryId = "c2"))
        assertEquals(2, filterCatalogProducts(products, null).size)
        assertEquals(listOf("p1"), filterCatalogProducts(products, "c1").map { it.id })
    }
}
