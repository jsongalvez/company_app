package com.companyb.companyapp.service

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
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BranchInventoryServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertTestUser(callerId, "inv-caller")
        DatabaseTestHelper.insertTestBranch(branchId, "Test Inventory Branch")
        DatabaseTestHelper.insertTestCategory(categoryId, "Test Category")
        DatabaseTestHelper.insertTestProduct(productId, "Test Product", categoryId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `ensureCard creates inventory card with zero stock`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        BranchInventoryService.ensureCard(callerId, branchId, productId)

        val cards = BranchInventoryService.findByBranch(callerId, branchId)
        assertEquals(1, cards.size)
        assertEquals(productId, cards[0].inventory.productId)
        assertEquals(0, cards[0].inventory.currentStock)
        assertEquals(1, cards[0].inventory.version)
    }

    @Test
    fun `ensureCard without MANAGE_PRODUCTS is forbidden`() {
        assertFailsWith<ForbiddenResponse> {
            BranchInventoryService.ensureCard(callerId, branchId, productId)
        }
    }

    @Test
    fun `ensureCard with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        assertFailsWith<NotFoundResponse> {
            BranchInventoryService.ensureCard(callerId, UUID.randomUUID(), productId)
        }
    }

    @Test
    fun `ensureCard with non-existent product returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)

        assertFailsWith<NotFoundResponse> {
            BranchInventoryService.ensureCard(callerId, branchId, UUID.randomUUID())
        }
    }

    @Test
    fun `restock increments stock and logs movement`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        BranchInventoryService.ensureCard(callerId, branchId, productId)

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        val movementId = UUID.randomUUID()

        val movement =
            BranchInventoryService.restock(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                quantity = 10,
                branchDayId = branchDayId,
            )

        assertEquals(10, movement.quantityChange)
        assertEquals("RESTOCK", movement.reason.name)

        val cards = BranchInventoryService.findByBranch(callerId, branchId)
        assertEquals(10, cards[0].inventory.currentStock)
        assertEquals(2, cards[0].inventory.version)
    }

    @Test
    fun `restock without MANAGE_PRODUCTS is forbidden`() {
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)

        assertFailsWith<ForbiddenResponse> {
            BranchInventoryService.restock(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                quantity = 10,
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `restock with negative quantity returns bad request`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)

        assertFailsWith<BadRequestResponse> {
            BranchInventoryService.restock(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                quantity = -5,
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `restock with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)

        assertFailsWith<NotFoundResponse> {
            BranchInventoryService.restock(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = UUID.randomUUID(),
                productId = productId,
                quantity = 10,
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `restock writes audit entries`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        BranchInventoryService.ensureCard(callerId, branchId, productId)

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        val movementId = UUID.randomUUID()

        BranchInventoryService.restock(
            callerId = callerId,
            movementId = movementId,
            branchId = branchId,
            productId = productId,
            quantity = 10,
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
    }

    @Test
    fun `findByBranch returns empty for branch with no inventory`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        val cards = BranchInventoryService.findByBranch(callerId, branchId)
        assertTrue(cards.isEmpty())
    }

    @Test
    fun `findByBranch without MANAGE_PRODUCTS is forbidden`() {
        assertFailsWith<ForbiddenResponse> {
            BranchInventoryService.findByBranch(callerId, branchId)
        }
    }

    @Test
    fun `findByBranch with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        assertFailsWith<NotFoundResponse> {
            BranchInventoryService.findByBranch(callerId, UUID.randomUUID())
        }
    }

    @Test
    fun `recordMovement with TESTER decreases stock and logs movement`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        BranchInventoryService.ensureCard(callerId, branchId, productId)
        BranchInventoryService.restock(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            quantity = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        val movement =
            BranchInventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                reason = InventoryMovementReason.TESTER,
                quantityChange = -2,
                notes = null,
                branchDayId = branchDayId,
            )

        assertEquals(-2, movement.quantityChange)
        assertEquals(InventoryMovementReason.TESTER, movement.reason)

        val cards = BranchInventoryService.findByBranch(callerId, branchId)
        assertEquals(8, cards[0].inventory.currentStock)
        assertEquals(3, cards[0].inventory.version)
    }

    @Test
    fun `recordMovement with SAMPLE decreases stock and logs movement`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        BranchInventoryService.ensureCard(callerId, branchId, productId)
        BranchInventoryService.restock(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            quantity = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        val movement =
            BranchInventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                reason = InventoryMovementReason.SAMPLE,
                quantityChange = -3,
                notes = null,
                branchDayId = branchDayId,
            )

        assertEquals(-3, movement.quantityChange)
        assertEquals(InventoryMovementReason.SAMPLE, movement.reason)

        val cards = BranchInventoryService.findByBranch(callerId, branchId)
        assertEquals(7, cards[0].inventory.currentStock)
    }

    @Test
    fun `recordMovement with MISSING decreases stock with notes`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        BranchInventoryService.ensureCard(callerId, branchId, productId)
        BranchInventoryService.restock(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            quantity = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        val movement =
            BranchInventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                reason = InventoryMovementReason.MISSING,
                quantityChange = -1,
                notes = "Lost during inventory count",
                branchDayId = branchDayId,
            )

        assertEquals(-1, movement.quantityChange)
        assertEquals("Lost during inventory count", movement.notes)

        val cards = BranchInventoryService.findByBranch(callerId, branchId)
        assertEquals(9, cards[0].inventory.currentStock)
    }

    @Test
    fun `recordMovement with ADJUSTMENT positive increases stock`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        BranchInventoryService.ensureCard(callerId, branchId, productId)
        BranchInventoryService.restock(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            quantity = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        val movement =
            BranchInventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                reason = InventoryMovementReason.ADJUSTMENT,
                quantityChange = 5,
                notes = "Found extra stock",
                branchDayId = branchDayId,
            )

        assertEquals(5, movement.quantityChange)

        val cards = BranchInventoryService.findByBranch(callerId, branchId)
        assertEquals(15, cards[0].inventory.currentStock)
    }

    @Test
    fun `recordMovement with TESTER positive quantity returns bad request`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        BranchInventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)

        assertFailsWith<BadRequestResponse> {
            BranchInventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                reason = InventoryMovementReason.TESTER,
                quantityChange = 1,
                notes = null,
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `recordMovement with MISSING blank notes returns bad request`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        BranchInventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)

        assertFailsWith<BadRequestResponse> {
            BranchInventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                reason = InventoryMovementReason.MISSING,
                quantityChange = -1,
                notes = "",
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `recordMovement without MANAGE_PRODUCTS is forbidden`() {
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)

        assertFailsWith<ForbiddenResponse> {
            BranchInventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = branchId,
                productId = productId,
                reason = InventoryMovementReason.TESTER,
                quantityChange = -1,
                notes = null,
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `recordMovement with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)

        assertFailsWith<NotFoundResponse> {
            BranchInventoryService.recordMovement(
                callerId = callerId,
                movementId = UUID.randomUUID(),
                branchId = UUID.randomUUID(),
                productId = productId,
                reason = InventoryMovementReason.TESTER,
                quantityChange = -1,
                notes = null,
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `recordMovement writes audit entries`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        BranchInventoryService.ensureCard(callerId, branchId, productId)
        BranchInventoryService.restock(
            callerId = callerId,
            movementId = UUID.randomUUID(),
            branchId = branchId,
            productId = productId,
            quantity = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        val movementId = UUID.randomUUID()

        BranchInventoryService.recordMovement(
            callerId = callerId,
            movementId = movementId,
            branchId = branchId,
            productId = productId,
            reason = InventoryMovementReason.TESTER,
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
    }

    private fun deleteTestRows() {
        transaction {
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq callerId) or
                    (AuditLogTable.recordId eq productId)
            }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq callerId }
            InventoryMovementTable.deleteWhere { InventoryMovementTable.movedBy eq callerId }
            BranchInventoryTable.deleteWhere {
                (BranchInventoryTable.branchId eq branchId)
            }
            BranchDayTable.deleteWhere { BranchDayTable.branchId eq branchId }
            ProductTable.deleteWhere { ProductTable.id eq productId }
            ProductCategoryTable.deleteWhere { ProductCategoryTable.id eq categoryId }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            AppUserTable.deleteWhere { AppUserTable.id eq callerId }
        }
    }
}
