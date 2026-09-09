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
        assertEquals("Category unavailable", catalogCategoryName("missing", listOf(category())))
    }

    @Test
    fun filter_nullReturnsAll() {
        val products = listOf(product(), product(id = "p2", categoryId = "c2"))
        assertEquals(2, filterCatalogProducts(products, null).size)
        assertEquals(listOf("p1"), filterCatalogProducts(products, "c1").map { it.id })
    }

    @Test
    fun visibleProducts_searchIsCaseInsensitiveContains() {
        val products =
            listOf(
                product(id = "p1", name = "Big Roll On"),
                product(id = "p2", name = "MagSpray", categoryId = "c1"),
            )
        assertEquals(2, filterCatalogVisibleProducts(products, null, "", CatalogStatusFilter.ALL).size)
        assertEquals(
            listOf("p1"),
            filterCatalogVisibleProducts(products, null, "roll", CatalogStatusFilter.ALL).map { it.id },
        )
        assertEquals(
            listOf("p2"),
            filterCatalogVisibleProducts(products, null, "MAG", CatalogStatusFilter.ALL).map { it.id },
        )
        assertTrue(
            filterCatalogVisibleProducts(products, null, "nope", CatalogStatusFilter.ALL).isEmpty(),
        )
    }

    @Test
    fun visibleProducts_statusFilter() {
        val products =
            listOf(
                product(id = "p1", isActive = true),
                product(id = "p2", isActive = false),
            )
        assertEquals(
            listOf("p1"),
            filterCatalogVisibleProducts(products, null, "", CatalogStatusFilter.ACTIVE).map { it.id },
        )
        assertEquals(
            listOf("p2"),
            filterCatalogVisibleProducts(products, null, "", CatalogStatusFilter.INACTIVE).map { it.id },
        )
        assertEquals(
            2,
            filterCatalogVisibleProducts(products, null, "", CatalogStatusFilter.ALL).size,
        )
    }

    @Test
    fun visibleProducts_combinesCategorySearchStatus() {
        val products =
            listOf(
                product(id = "p1", name = "Big Roll On", categoryId = "c1", isActive = true),
                product(id = "p2", name = "Big Salve", categoryId = "c2", isActive = true),
                product(id = "p3", name = "Big Roll Off", categoryId = "c1", isActive = false),
            )
        assertEquals(
            listOf("p1"),
            filterCatalogVisibleProducts(products, "c1", "roll", CatalogStatusFilter.ACTIVE).map { it.id },
        )
    }

    @Test
    fun customerCharge_unparseableNeverFabricatesZero() {
        assertEquals("₱2500.00", catalogCustomerChargeLine(product()))
        assertEquals(
            "Charge unavailable",
            catalogCustomerChargeLine(product().copy(unitPrice = "abc")),
        )
        assertEquals(
            "Charge unavailable",
            catalogCustomerChargeLine(product().copy(unitPrice = "-5")),
        )
    }

    @Test
    fun stateText_namesBothStates() {
        assertEquals("Active", catalogStateText(true))
        assertEquals("Inactive", catalogStateText(false))
    }

    @Test
    fun totalLine_sumsBaseAndCommission() {
        assertEquals(
            "Total ₱2600.0 (base ₱2500.00 + ₱100.00 commission)",
            catalogProductTotalLine("2500.00", "100.00"),
        )
        assertNull(catalogProductTotalLine("abc", "100.00"))
        assertNull(catalogProductTotalLine("2500.00", "-1"))
    }

    @Test
    fun mutatedRow_staysVisibleWhenFilterHidesIt() {
        val products =
            listOf(
                product(id = "p1", isActive = false),
                product(id = "p2", isActive = true),
            )
        val visible = filterCatalogVisibleProducts(products, null, "", CatalogStatusFilter.ACTIVE)
        assertEquals(listOf("p2"), visible.map { it.id })
        val pinned = ensureMutatedRowVisible(products, visible, "p1")
        assertEquals(listOf("p2", "p1"), pinned.map { it.id })
        assertEquals(visible, ensureMutatedRowVisible(products, visible, null))
    }
}
