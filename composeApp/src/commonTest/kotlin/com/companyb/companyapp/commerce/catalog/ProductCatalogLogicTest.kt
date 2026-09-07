package com.companyb.companyapp.commerce.catalog

import com.companyb.companyapp.contracts.commerce.ProductCategoryResponse
import com.companyb.companyapp.contracts.commerce.ProductResponse
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
    fun filter_nullReturnsAll() {
        val products = listOf(product(), product(id = "p2", categoryId = "c2"))
        assertEquals(2, filterCatalogProducts(products, null).size)
        assertEquals(listOf("p1"), filterCatalogProducts(products, "c1").map { it.id })
    }
}
