package com.companyb.companyapp.commerce

import com.companyb.companyapp.commerce.InventoryService
import com.companyb.companyapp.commerce.MovementType
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import kotlin.test.Test
import kotlin.test.assertEquals

// #442 — branch-scoped all-history breakdown: Restock baseline, Sales aggregate,
// Tester/Sample combined, Missing own column, Adjustment corrections; Available stays
// the persisted live count and reconciles as Stock - Sales - TesterSample - Missing + Adjustment.
class BranchInventoryBreakdownPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "breakdown-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Breakdown Branch ${branchId.toString().take(8)}")
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
    }

    @Test
    fun `breakdown aggregates five values and reconciles available`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            quantityChange = 20,
            notes = null,
            branchDayId = branchDayId,
        )
        // Sales write SALE movements only through the sale command (sale_id_logic requires
        // product_sale_id); the breakdown aggregates them like any other ledger row.
        ProductSaleService.sell(
            callerId = callerId,
            id = TestFixtures.uuid(),
            branchDayId = branchDayId,
            sessionId = null,
            clientId = null,
            isWalkIn = true,
            productId = productId,
            quantity = 4,
            expectedVersion = 2,
        )

        fun move(
            type: MovementType,
            qty: Int,
            notes: String? = null,
        ) = InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = type,
            quantityChange = qty,
            notes = notes,
            branchDayId = branchDayId,
        )

        move(MovementType.Tester, -2)
        move(MovementType.Sample, -3)
        move(MovementType.Missing, -1, notes = "Lost during count")
        move(MovementType.Adjustment, 2, notes = "Recount correction")

        val breakdowns = InventoryService.getBreakdowns(branchId)
        val breakdown = breakdowns[productId] ?: error("missing breakdown")
        assertEquals(20, breakdown.stock)
        assertEquals(4, breakdown.sales)
        assertEquals(5, breakdown.testerSample)
        assertEquals(1, breakdown.missing)
        assertEquals(2, breakdown.adjustment)

        val card = InventoryService.getStock(branchId).single()
        assertEquals(12, card.inventory.currentStock)
        assertEquals(
            card.inventory.currentStock,
            breakdown.stock - breakdown.sales - breakdown.testerSample - breakdown.missing + breakdown.adjustment,
        )
    }

    @Test
    fun `empty ledger yields no breakdown row`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        assertEquals(null, InventoryService.getBreakdowns(branchId)[productId])
    }
}
