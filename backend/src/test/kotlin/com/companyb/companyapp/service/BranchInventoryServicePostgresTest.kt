package com.companyb.companyapp.service

import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.InventoryMovementReason
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.inventory.InventoryService
import com.companyb.companyapp.service.inventory.MovementType
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Suppress("LargeClass")
class BranchInventoryServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "inv-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestBranch(branchId, "Test Inventory Branch ${branchId.toString().take(8)}")
        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestCategory(categoryId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, categoryId)
        DatabaseTestHelper.insertTestProduct(productId, categoryId = categoryId)
        trackOwned(ProductTable, ProductTable.id, productId)
    }

    @Test
    fun `ensureCard creates inventory card with zero stock`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        InventoryService.ensureCard(branchId, productId)

        val cards = InventoryService.getStock(branchId)
        assertEquals(1, cards.size)
        assertEquals(productId, cards[0].inventory.productId)
        assertEquals(0, cards[0].inventory.currentStock)
        assertEquals(1, cards[0].inventory.version)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getStock includes product price and commission from the join`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(branchId, productId)

        val cards = InventoryService.getStock(branchId)

        assertEquals(1, cards.size)
        assertEquals(BigDecimal("100.00"), cards[0].unitPrice)
        assertEquals(BigDecimal("10.00"), cards[0].commissionAmount)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `ensureCard without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(branchId, productId)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

        val cards = InventoryService.getStock(branchId)
        assertEquals(1, cards.size)
        assertEquals(productId, cards[0].inventory.productId)
        assertEquals(0, cards[0].inventory.currentStock)
        assertEquals(1, cards[0].inventory.version)
    }

    @Test
    fun `ensureCard with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(UUID.randomUUID(), productId)
        }
    }

    @Test
    fun `ensureCard with non-existent product returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(branchId, UUID.randomUUID())
        }
    }

    @Test
    fun `restock increments stock and logs movement`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(branchId, productId)

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        val movementId = UUID.randomUUID()

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 10,
                notes = null,
                branchDayId = branchDayId,
            )

        assertEquals(10, movement.quantityChange)
        assertEquals("RESTOCK", movement.reason.name)

        val cards = InventoryService.getStock(branchId)
        assertEquals(10, cards[0].inventory.currentStock)
        assertEquals(2, cards[0].inventory.version)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `restock without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                notes = null,
                quantityChange = 10,
                branchDayId = branchDayId,
            )

        assertEquals(10, movement.quantityChange)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `restock with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        assertFailsWith<NotFoundException> {
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = UUID.randomUUID(),
                productId = productId,
                movementType = MovementType.Restock,
                notes = null,
                quantityChange = 10,
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `restock writes audit entries`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(branchId, productId)

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        val movementId = UUID.randomUUID()

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = movementId,
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            quantityChange = 10,
            notes = null,
            branchDayId = branchDayId,
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq BranchInventoryTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `findByBranch returns empty for branch with no inventory`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
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
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        assertFailsWith<NotFoundException> {
            InventoryService.getStock(UUID.randomUUID())
        }
    }

    @Test
    fun `recordMovement with TESTER decreases stock and logs movement`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Tester,
                quantityChange = -2,
                notes = null,
                branchDayId = branchDayId,
            )

        assertEquals(-2, movement.quantityChange)
        assertEquals(InventoryMovementReason.TESTER, movement.reason)

        val cards = InventoryService.getStock(branchId)
        assertEquals(8, cards[0].inventory.currentStock)
        assertEquals(3, cards[0].inventory.version)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `recordMovement with SAMPLE decreases stock and logs movement`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Sample,
                quantityChange = -3,
                notes = null,
                branchDayId = branchDayId,
            )

        assertEquals(-3, movement.quantityChange)
        assertEquals(InventoryMovementReason.SAMPLE, movement.reason)

        val cards = InventoryService.getStock(branchId)
        assertEquals(7, cards[0].inventory.currentStock)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `recordMovement with MISSING decreases stock with notes`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Missing,
                quantityChange = -1,
                notes = "Lost during inventory count",
                branchDayId = branchDayId,
            )

        assertEquals(-1, movement.quantityChange)
        assertEquals("Lost during inventory count", movement.notes)

        val cards = InventoryService.getStock(branchId)
        assertEquals(9, cards[0].inventory.currentStock)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `recordMovement with ADJUSTMENT positive increases stock`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Adjustment,
                quantityChange = 5,
                notes = "Found extra stock",
                branchDayId = branchDayId,
            )

        assertEquals(5, movement.quantityChange)

        val cards = InventoryService.getStock(branchId)
        assertEquals(15, cards[0].inventory.currentStock)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `recordMovement without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Tester,
                quantityChange = -1,
                notes = null,
                branchDayId = branchDayId,
            )

        assertEquals(-1, movement.quantityChange)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `recordMovement with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        assertFailsWith<NotFoundException> {
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = UUID.randomUUID(),
                productId = productId,
                movementType = MovementType.Tester,
                quantityChange = -1,
                notes = null,
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `recordMovement writes audit entries`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        val movementId = UUID.randomUUID()

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = movementId,
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Tester,
            quantityChange = -2,
            notes = null,
            branchDayId = branchDayId,
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq BranchInventoryTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts returns products at or below threshold`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = branchDayId,
        )

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Tester,
            quantityChange = -6,
            notes = null,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(4, lowStock[0].inventory.currentStock)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts returns empty when no products are low stock`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertTrue(lowStock.isEmpty())
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        assertFailsWith<NotFoundException> {
            InventoryService.getLowStockAlerts(UUID.randomUUID())
        }
    }

    @Test
    fun `getLowStockAlerts without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 3,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(3, lowStock[0].inventory.currentStock)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Suppress("LongMethod")
    @Test
    fun `getLowStockAlerts uses per-product reorder point`() {
        val highReorderProductId = UUID.randomUUID()
        DatabaseTestHelper.insertTestProduct(
            id = highReorderProductId,
            categoryId = categoryId,
            name = "High Reorder Product",
            reorderPoint = 10,
        )
        trackOwned(ProductTable, ProductTable.id, highReorderProductId)
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        InventoryService.ensureCard(branchId, productId)
        InventoryService.ensureCard(branchId, highReorderProductId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = branchDayId,
        )
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = highReorderProductId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = branchDayId,
        )

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Tester,
            quantityChange = -2,
            notes = null,
            branchDayId = branchDayId,
        )
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = highReorderProductId,
            movementType = MovementType.Tester,
            quantityChange = -2,
            notes = null,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(highReorderProductId, lowStock[0].inventory.productId)
        assertEquals(8, lowStock[0].inventory.currentStock)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts with null reorderPoint falls back to global default`() {
        val noReorderProductId = UUID.randomUUID()
        DatabaseTestHelper.insertTestProduct(
            id = noReorderProductId,
            categoryId = categoryId,
            name = "No Reorder Product",
        )
        trackOwned(ProductTable, ProductTable.id, noReorderProductId)
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        InventoryService.ensureCard(branchId, noReorderProductId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = noReorderProductId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 4,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(noReorderProductId, lowStock[0].inventory.productId)
        assertEquals(4, lowStock[0].inventory.currentStock)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts with thresholdOverride overrides stored thresholds`() {
        val highReorderProductId = UUID.randomUUID()
        DatabaseTestHelper.insertTestProduct(
            id = highReorderProductId,
            categoryId = categoryId,
            name = "High Reorder Product",
            reorderPoint = 10,
        )
        trackOwned(ProductTable, ProductTable.id, highReorderProductId)
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        InventoryService.ensureCard(branchId, productId)
        InventoryService.ensureCard(branchId, highReorderProductId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = branchDayId,
        )
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = highReorderProductId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 12,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId, thresholdOverride = 15)

        assertEquals(2, lowStock.size)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts returns product at exactly the threshold`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 5,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(5, lowStock[0].inventory.currentStock)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }
}
