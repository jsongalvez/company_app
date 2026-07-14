package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.postgresql.util.PGobject
import java.math.BigDecimal
import java.time.LocalDate
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
        insertUser(callerId)
        insertBranch(branchId, "Test Inventory Branch")
        insertCategory(categoryId, "Test Category")
        insertProduct(productId, "Test Product", categoryId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `ensureCard creates inventory card with zero stock`() {
        grantManageProducts(callerId)

        BranchInventoryService.ensureCard(callerId, branchId, productId)

        val cards = BranchInventoryService.findByBranch(branchId)
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
        grantManageProducts(callerId)

        assertFailsWith<NotFoundResponse> {
            BranchInventoryService.ensureCard(callerId, UUID.randomUUID(), productId)
        }
    }

    @Test
    fun `ensureCard with non-existent product returns not found`() {
        grantManageProducts(callerId)

        assertFailsWith<NotFoundResponse> {
            BranchInventoryService.ensureCard(callerId, branchId, UUID.randomUUID())
        }
    }

    @Test
    fun `restock increments stock and logs movement`() {
        grantManageProducts(callerId)
        BranchInventoryService.ensureCard(callerId, branchId, productId)

        val branchDayId = createBranchDay(branchId)
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

        val cards = BranchInventoryService.findByBranch(branchId)
        assertEquals(10, cards[0].inventory.currentStock)
        assertEquals(2, cards[0].inventory.version)
    }

    @Test
    fun `restock without MANAGE_PRODUCTS is forbidden`() {
        val branchDayId = createBranchDay(branchId)

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
        grantManageProducts(callerId)
        val branchDayId = createBranchDay(branchId)

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
        grantManageProducts(callerId)
        val branchDayId = createBranchDay(branchId)

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
        grantManageProducts(callerId)
        BranchInventoryService.ensureCard(callerId, branchId, productId)

        val branchDayId = createBranchDay(branchId)
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
        val cards = BranchInventoryService.findByBranch(branchId)
        assertTrue(cards.isEmpty())
    }

    @Test
    fun `findByBranch with non-existent branch returns not found`() {
        assertFailsWith<NotFoundResponse> {
            BranchInventoryService.findByBranch(UUID.randomUUID())
        }
    }

    private fun insertUser(userId: UUID) {
        DatabaseTestHelper.insertUser(
            id = userId,
            username = "inv-caller-$userId",
            passwordHash = "test-password-hash",
            email = "${userId.toString().take(8)}@t.st",
            displayName = "Inventory Test Caller",
        )
    }

    private fun insertBranch(
        id: UUID,
        name: String,
    ) {
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = id
                it[BranchTable.name] = name
                it[BranchTable.branchType] = BranchType.CLINIC
            }
        }
    }

    private fun insertCategory(
        id: UUID,
        name: String,
    ) {
        transaction {
            ProductCategoryTable.insertIgnore {
                it[ProductCategoryTable.id] = id
                it[ProductCategoryTable.name] = name
            }
        }
    }

    private fun insertProduct(
        id: UUID,
        name: String,
        categoryId: UUID,
    ) {
        transaction {
            ProductTable.insertIgnore {
                it[ProductTable.id] = id
                it[ProductTable.name] = name
                it[ProductTable.productCategoryId] = categoryId
                it[ProductTable.unitPrice] = BigDecimal("100.00")
                it[ProductTable.commissionAmount] = BigDecimal("10.00")
            }
        }
    }

    private fun createBranchDay(branchId: UUID): UUID =
        transaction {
            val today = LocalDate.now(BranchDayService.manilaZone)
            BranchDayTable.insertIgnore {
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = today
            }
            BranchDayTable
                .selectAll()
                .where {
                    (BranchDayTable.branchId eq branchId) and
                        (BranchDayTable.date eq today)
                }.single()[BranchDayTable.id]
        }

    private fun grantManageProducts(userId: UUID) {
        DatabaseTestHelper.grantManageProducts(userId, sourceId)
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
