package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.InventoryMovementTable
import com.companyb.companyapp.commerce.InventoryService
import com.companyb.companyapp.commerce.MovementType
import com.companyb.companyapp.commerce.ProductTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BranchInventoryServicePostgresTest : BranchInventoryServicePostgresBase() {
    @Test
    fun `ensureCard creates inventory card with zero stock`() {
        InventoryService.ensureCard(callerId, branchId, productId)

        val cards = InventoryService.getStock(branchId)
        assertEquals(1, cards.size)
        assertEquals(productId, cards[0].inventory.productId)
        assertEquals(0, cards[0].inventory.currentStock)
        assertEquals(1, cards[0].inventory.version)
    }

    @Test
    fun `ensureCard writes insert audit row attributed to caller`() {
        val card = InventoryService.ensureCard(callerId, branchId, productId)

        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq BranchInventoryTable.tableName) and
                            (AuditLogTable.recordId eq card.id)
                    }.single()
            }
        assertEquals(AuditAction.INSERT, audit[AuditLogTable.action])
        assertEquals(callerId, audit[AuditLogTable.changedBy])
        assertEquals(branchId, audit[AuditLogTable.branchId])
    }

    @Test
    fun `ensureCard on existing card writes no audit row without mutating the card`() {
        InventoryService.ensureCard(callerId, branchId, productId)

        val ensured = InventoryService.ensureCard(callerId, branchId, productId)

        assertEquals(1, ensured.version)
        val rows =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq BranchInventoryTable.tableName) and
                            (AuditLogTable.recordId eq ensured.id)
                    }.toList()
            }
        assertEquals(1, rows.size)
        assertEquals(AuditAction.INSERT, rows.single()[AuditLogTable.action])
    }

    @Test
    fun `getStock includes product price and commission from the join`() {
        InventoryService.ensureCard(callerId, branchId, productId)

        val cards = InventoryService.getStock(branchId)

        assertEquals(1, cards.size)
        assertEquals(BigDecimal("100.00"), cards[0].unitPrice)
        assertEquals(BigDecimal("10.00"), cards[0].commissionAmount)
    }

    @Test
    fun `ensureCard without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(callerId, branchId, productId)

        val cards = InventoryService.getStock(branchId)
        assertEquals(1, cards.size)
        assertEquals(productId, cards[0].inventory.productId)
        assertEquals(0, cards[0].inventory.currentStock)
        assertEquals(1, cards[0].inventory.version)
    }

    @Test
    fun `ensureCard with non-existent branch returns not found`() {
        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(callerId, TestFixtures.uuid(), productId)
        }
    }

    @Test
    fun `ensureCard with non-existent product returns not found`() {
        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(callerId, branchId, TestFixtures.uuid())
        }
    }

    @Test
    fun `inactive product is hidden from inventory reads and preserves history`() {
        val activeProductId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProduct(
            id = activeProductId,
            categoryId = categoryId,
            name = "Active Inventory Product",
        )
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, branchId, activeProductId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movementId = TestFixtures.uuid()
        val firstMovement = restock(productId, branchDayId, 10, movementId)
        deactivateProduct()

        val stock = InventoryService.getStock(branchId)
        assertEquals(1, stock.size)
        assertEquals(activeProductId, stock.single().inventory.productId)
        val lowStock = InventoryService.getLowStockAlerts(branchId)
        assertEquals(1, lowStock.size)
        assertEquals(activeProductId, lowStock.single().inventory.productId)
        assertTrue(InventoryService.getMovementHistory(branchId).isEmpty())
        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(callerId, branchId, productId)
        }
        assertEquals(
            firstMovement,
            restock(productId, branchDayId, 10, movementId),
        )
        assertFailsWith<NotFoundException> {
            restock(productId, branchDayId, 5)
        }

        assertEquals(1L, cardCount(productId))
        val card = readCard(productId)
        assertEquals(10, card[BranchInventoryTable.currentStock])
        assertEquals(2, card[BranchInventoryTable.version])
        assertEquals(1L, movementCount(productId))
    }

    @Test
    fun `inactive product rejects inventory card and movement creation`() {
        deactivateProduct()

        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(callerId, branchId, productId)
        }

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        assertFailsWith<NotFoundException> {
            InventoryService.recordMovement(
                callerId = callerId,
                movement(10, branchDayId),
            )
        }

        val cardCount =
            transaction {
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq branchId) and
                            (BranchInventoryTable.productId eq productId)
                    }.count()
            }
        val movementCount =
            transaction {
                InventoryMovementTable
                    .selectAll()
                    .where {
                        (InventoryMovementTable.branchId eq branchId) and
                            (InventoryMovementTable.productId eq productId)
                    }.count()
            }
        assertEquals(0L, cardCount)
        assertEquals(0L, movementCount)
    }

    @Test
    fun `findByBranch returns empty for branch with no inventory`() {
        val cards = InventoryService.getStock(branchId)
        assertTrue(cards.isEmpty())
    }

    @Test
    fun `findByBranch without MANAGE_PRODUCTS is allowed at service layer`() {
        val cards = InventoryService.getStock(branchId)

        assertTrue(cards.isEmpty())
    }

    @Test
    fun `findByBranch with non-existent branch returns not found`() {
        assertFailsWith<NotFoundException> {
            InventoryService.getStock(TestFixtures.uuid())
        }
    }

    @Test
    fun `getLowStockAlerts returns products at or below threshold`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, branchDayId),
        )

        InventoryService.recordMovement(
            callerId = callerId,
            movement(-6, branchDayId, MovementType.Tester),
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(4, lowStock[0].inventory.currentStock)
    }

    @Test
    fun `getLowStockAlerts returns empty when no products are low stock`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, branchDayId),
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertTrue(lowStock.isEmpty())
    }

    @Test
    fun `getLowStockAlerts with non-existent branch returns not found`() {
        assertFailsWith<NotFoundException> {
            InventoryService.getLowStockAlerts(TestFixtures.uuid())
        }
    }

    @Test
    fun `getLowStockAlerts without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movement(3, branchDayId),
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(3, lowStock[0].inventory.currentStock)
    }

    @Test
    fun `getLowStockAlerts uses per-product reorder point`() {
        val highReorderProductId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProduct(
            id = highReorderProductId,
            categoryId = categoryId,
            name = "High Reorder Product",
            reorderPoint = 10,
        )

        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, branchId, highReorderProductId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        restock(productId, branchDayId, 10)
        restock(highReorderProductId, branchDayId, 10)
        consume(productId, branchDayId, MovementType.Tester, -2)
        consume(highReorderProductId, branchDayId, MovementType.Tester, -2)

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(highReorderProductId, lowStock[0].inventory.productId)
        assertEquals(8, lowStock[0].inventory.currentStock)
    }

    @Test
    fun `getLowStockAlerts with null reorderPoint falls back to global default`() {
        val noReorderProductId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProduct(
            id = noReorderProductId,
            categoryId = categoryId,
            name = "No Reorder Product",
        )

        InventoryService.ensureCard(callerId, branchId, noReorderProductId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            NewInventoryMovement(
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = noReorderProductId,
                movementType = MovementType.Restock,
                notes = null,
                quantityChange = 4,
                branchDayId = branchDayId,
            ),
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(noReorderProductId, lowStock[0].inventory.productId)
        assertEquals(4, lowStock[0].inventory.currentStock)
    }

    @Test
    fun `getLowStockAlerts with thresholdOverride overrides stored thresholds`() {
        val highReorderProductId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProduct(
            id = highReorderProductId,
            categoryId = categoryId,
            name = "High Reorder Product",
            reorderPoint = 10,
        )

        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, branchId, highReorderProductId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, branchDayId),
        )
        InventoryService.recordMovement(
            callerId = callerId,
            NewInventoryMovement(
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = highReorderProductId,
                movementType = MovementType.Restock,
                notes = null,
                quantityChange = 12,
                branchDayId = branchDayId,
            ),
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId, thresholdOverride = 15)

        assertEquals(2, lowStock.size)
    }

    @Test
    fun `getLowStockAlerts returns product at exactly the threshold`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movement(5, branchDayId),
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(5, lowStock[0].inventory.currentStock)
    }

    private fun deactivateProduct() {
        transaction {
            ProductTable.update({ ProductTable.id eq productId }) {
                it[ProductTable.isActive] = false
            }
        }
    }

    private fun consume(
        targetProductId: UUID,
        branchDayId: UUID,
        movementType: MovementType,
        quantityChange: Int,
    ) {
        InventoryService.recordMovement(
            callerId = callerId,
            NewInventoryMovement(
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = targetProductId,
                movementType = movementType,
                quantityChange = quantityChange,
                notes = null,
                branchDayId = branchDayId,
            ),
        )
    }

    private fun cardCount(targetProductId: UUID): Long =
        transaction {
            BranchInventoryTable
                .selectAll()
                .where {
                    (BranchInventoryTable.branchId eq branchId) and
                        (BranchInventoryTable.productId eq targetProductId)
                }.count()
        }

    private fun readCard(targetProductId: UUID): org.jetbrains.exposed.v1.core.ResultRow =
        transaction {
            BranchInventoryTable
                .selectAll()
                .where {
                    (BranchInventoryTable.branchId eq branchId) and
                        (BranchInventoryTable.productId eq targetProductId)
                }.single()
        }
}
