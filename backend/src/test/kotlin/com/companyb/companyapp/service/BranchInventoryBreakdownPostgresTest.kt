package com.companyb.companyapp.service

import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.service.inventory.InventoryService
import com.companyb.companyapp.service.inventory.MovementType
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
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
        DatabaseTestHelper.insertTestUser(callerId, "breakdown-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestBranch(branchId, "Breakdown Branch ${branchId.toString().take(8)}")
        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestCategory(categoryId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, categoryId)
        DatabaseTestHelper.insertTestProduct(productId, categoryId = categoryId)
        trackOwned(ProductTable, ProductTable.id, productId)
    }

    @Test
    fun `breakdown aggregates five values and reconciles available`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(ProductSaleTable, ProductSaleTable.handledBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `empty ledger yields no breakdown row`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        assertEquals(null, InventoryService.getBreakdowns(branchId)[productId])
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }
}
