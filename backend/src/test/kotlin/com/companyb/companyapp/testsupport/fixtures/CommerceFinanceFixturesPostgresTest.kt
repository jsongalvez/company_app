package com.companyb.companyapp.testsupport.fixtures

import com.companyb.companyapp.commerce.ProductSaleRepository
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Fixture-production parity for product-sale seeding (#903).
 *
 * The fixture mirrors `ProductSaleRepository.insertSaleInTransaction`: money and
 * identity default to the product card with `total = unitPrice × quantity`, and an
 * explicit incoherent total requires the `allowIncoherentTotal` opt-in.
 */
class CommerceFinanceFixturesPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "sale-fixture-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Sale Fixture Branch")
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(
            id = productId,
            name = "Card Product",
            categoryId = categoryId,
            unitPrice = BigDecimal("50.00"),
            commissionAmount = BigDecimal("7.50"),
        )
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
    }

    @Test
    fun `defaults derive money and identity from the product card`() {
        val saleId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProductSale(
            id = saleId,
            branchDayId = branchDayId,
            productId = productId,
            handledBy = callerId,
            quantity = 3,
        )

        val sale = ProductSaleRepository.findById(saleId) ?: error("sale missing for $saleId")
        assertTrue(sale.unitPriceAtTime.compareTo(BigDecimal("50.00")) == 0)
        assertTrue(sale.totalAmountAtTime.compareTo(BigDecimal("150.00")) == 0)
        assertTrue(sale.commissionAmountAtTime.compareTo(BigDecimal("7.50")) == 0)
        assertEquals("Card Product", sale.productName)
    }

    @Test
    fun `explicit incoherent total fails without the opt-in flag`() {
        assertFailsWith<IllegalArgumentException> {
            CommerceFinanceFixtures.insertTestProductSale(
                id = TestFixtures.uuid(),
                branchDayId = branchDayId,
                productId = productId,
                handledBy = callerId,
                quantity = 3,
                totalAmount = BigDecimal("100.00"),
            )
        }
    }

    @Test
    fun `explicit incoherent total with the opt-in flag is stored`() {
        val saleId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProductSale(
            id = saleId,
            branchDayId = branchDayId,
            productId = productId,
            handledBy = callerId,
            quantity = 3,
            totalAmount = BigDecimal("100.00"),
            allowIncoherentTotal = true,
        )

        val sale = ProductSaleRepository.findById(saleId) ?: error("sale missing for $saleId")
        assertTrue(sale.totalAmountAtTime.compareTo(BigDecimal("100.00")) == 0)
    }
}
