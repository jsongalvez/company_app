package com.companyb.companyapp.remittance

import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.CommerceReads
import com.companyb.companyapp.commerce.InventoryService
import com.companyb.companyapp.commerce.ProductSaleService
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.reporting.DailySalesSummaryService
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * #858 — voided-session product-sale coherence: a voided session is excluded
 * from all financial calculations (business-requirements.md, Session Voiding).
 * Commission input already excluded voided-session sales
 * (findNonVoidedSalesByBranchDayInTransaction); daily product totals, the
 * PRODUCT picker, and PRODUCT line validation now agree. Unvoid restores all
 * three, and null-session (walk-in) sales are unaffected by an unrelated void.
 */
class VoidedProductSaleCoherencePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "void-product-coherence")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Void Product Branch")
        SessionClientFixtures.insertTestClient(clientId)
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
    }

    @Test
    fun `void excludes session-linked sale from daily total picker lines and commission input`() {
        val saleId = insertSessionLinkedSale()
        assertEquals(0, BigDecimal("500.00").compareTo(dailyProductTotal()))
        assertTrue(pickerIds().contains(saleId))
        assertTrue(commissionInputIds().contains(saleId))

        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")

        assertEquals(0, BigDecimal.ZERO.compareTo(dailyProductTotal()))
        assertTrue(pickerIds().none { it == saleId })
        assertTrue(commissionInputIds().none { it == saleId })

        val remittance = createDraftRemittance()
        val error =
            assertFailsWith<ValidationException> {
                RemittanceService.addLine(
                    callerId = callerId,
                    remittanceId = remittance.id,
                    id = TestFixtures.uuid(),
                    type = RemittanceLineType.PRODUCT_SALE,
                    sessionId = null,
                    productSaleId = saleId,
                    amount = BigDecimal("500.00"),
                )
            }
        assertTrue(checkNotNull(error.message).contains("voided", ignoreCase = true))
    }

    @Test
    fun `unvoid restores session-linked sale to daily total picker and lines`() {
        val saleId = insertSessionLinkedSale()
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")
        SessionService.unvoidSession(callerId, sessionId, "Resolved in error")

        assertEquals(0, BigDecimal("500.00").compareTo(dailyProductTotal()))
        assertTrue(pickerIds().contains(saleId))
        assertTrue(commissionInputIds().contains(saleId))

        val remittance = createDraftRemittance()
        val line =
            RemittanceService.addLine(
                callerId = callerId,
                remittanceId = remittance.id,
                id = TestFixtures.uuid(),
                type = RemittanceLineType.PRODUCT_SALE,
                sessionId = null,
                productSaleId = saleId,
                amount = BigDecimal("500.00"),
            )
        assertEquals(saleId, line.productSaleId)
    }

    @Test
    fun `sell to voided session rejects with explicit 400`() {
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")
        ensureStockForSell()

        val error =
            assertFailsWith<ValidationException> {
                ProductSaleService.sell(
                    callerId = callerId,
                    id = TestFixtures.uuid(),
                    branchDayId = branchDayId,
                    sessionId = sessionId,
                    clientId = null,
                    isWalkIn = false,
                    productId = productId,
                    quantity = 1,
                    expectedVersion = 2,
                )
            }
        assertTrue(checkNotNull(error.message).contains("voided", ignoreCase = true))
    }

    @Test
    fun `void leaves null-session sale counted while linked sale drops out`() {
        val linkedSaleId = insertSessionLinkedSale()
        val walkInSaleId = insertWalkInSale(BigDecimal("200.00"))

        assertEquals(0, BigDecimal("700.00").compareTo(dailyProductTotal()))

        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")

        assertEquals(0, BigDecimal("200.00").compareTo(dailyProductTotal()))
        val picker = pickerIds()
        assertTrue(picker.contains(walkInSaleId))
        assertTrue(picker.none { it == linkedSaleId })
        val commissionInput = commissionInputIds()
        assertTrue(commissionInput.contains(walkInSaleId))
        assertTrue(commissionInput.none { it == linkedSaleId })
    }

    private fun insertSessionLinkedSale(): UUID {
        val saleId = TestFixtures.uuid()
        // Exposed insert-lambda trap: the lambda receiver is the table, so an
        // unqualified class property matching a column name (branchDayId,
        // sessionId, productId) resolves to the COLUMN (self-reference).
        // Locals shadow the receiver, so alias through them first.
        val dayId = branchDayId
        val linkedSessionId = sessionId
        val saleProductId = productId
        transaction {
            ProductSaleTable.insert {
                it[ProductSaleTable.id] = saleId
                it[ProductSaleTable.branchDayId] = dayId
                it[ProductSaleTable.sessionId] = linkedSessionId
                it[ProductSaleTable.productId] = saleProductId
                it[ProductSaleTable.productName] = "Test Product"
                it[ProductSaleTable.handledBy] = callerId
                it[ProductSaleTable.quantity] = 1
                it[ProductSaleTable.unitPriceAtTime] = BigDecimal("500.00")
                it[ProductSaleTable.totalAmountAtTime] = BigDecimal("500.00")
                it[ProductSaleTable.commissionAmountAtTime] = BigDecimal("50.00")
                it[ProductSaleTable.isWalkIn] = false
            }
        }
        return saleId
    }

    private fun insertWalkInSale(total: BigDecimal): UUID {
        val saleId = TestFixtures.uuid()
        // Same insert-lambda aliasing as insertSessionLinkedSale above.
        val dayId = branchDayId
        val saleProductId = productId
        transaction {
            ProductSaleTable.insert {
                it[ProductSaleTable.id] = saleId
                it[ProductSaleTable.branchDayId] = dayId
                it[ProductSaleTable.productId] = saleProductId
                it[ProductSaleTable.productName] = "Test Product"
                it[ProductSaleTable.handledBy] = callerId
                it[ProductSaleTable.quantity] = 1
                it[ProductSaleTable.unitPriceAtTime] = total
                it[ProductSaleTable.totalAmountAtTime] = total
                it[ProductSaleTable.commissionAmountAtTime] = BigDecimal.ZERO
                it[ProductSaleTable.isWalkIn] = true
            }
        }
        return saleId
    }

    private fun dailyProductTotal(): BigDecimal =
        DailySalesSummaryService.getDailySummary(branchId, TestFixtures.today).totalProductSales

    private fun pickerIds(): List<UUID> =
        RemittanceService
            .findProductSalesInRange(branchId, TestFixtures.today, TestFixtures.today)
            .map { it.id }

    private fun commissionInputIds(): List<UUID> =
        transaction {
            CommerceReads.findNonVoidedSalesByBranchDayInTransaction(branchDayId).map { it.id }
        }

    private fun createDraftRemittance() =
        RemittanceService.createDraft(
            callerId = callerId,
            id = TestFixtures.uuid(),
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = TestFixtures.today,
            dateRangeEnd = TestFixtures.today,
        )

    /**
     * Stock card for the sell-gate test via the production seam (same shape as
     * RemittanceLineServicePostgresTest.ensureBranchInventory): ensureCard opens
     * the card, then the stock bump + version step makes expectedVersion
     * deterministic on a fresh schema.
     */
    private fun ensureStockForSell() {
        val card = InventoryService.ensureCard(callerId, branchId, productId)
        transaction {
            BranchInventoryTable.update({
                (BranchInventoryTable.branchId eq branchId) and
                    (BranchInventoryTable.productId eq productId)
            }) {
                it[BranchInventoryTable.currentStock] = 20
                it[BranchInventoryTable.version] = card.version + 1
            }
        }
    }
}
