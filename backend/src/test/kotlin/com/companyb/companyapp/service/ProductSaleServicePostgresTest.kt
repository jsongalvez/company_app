package com.companyb.companyapp.service

import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductSaleServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()

    private lateinit var branchDayId: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertTestUser(callerId, "user")
        DatabaseTestHelper.insertTestBranch(branchId, "Test Sale Branch")
        DatabaseTestHelper.insertTestCategory(categoryId, "Test Category")
        DatabaseTestHelper.insertTestProduct(productId, "Test Product", categoryId)
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        DatabaseTestHelper.insertTestClient(clientId)
        DatabaseTestHelper.insertTestSession(sessionId, clientId, branchDayId)
        DatabaseTestHelper.grantEditBranchData(callerId, sourceId)
        ensureInventoryCard(branchId, productId, 20)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `sell creates product sale with stock decrement and movement`() {
        val saleId = UUID.randomUUID()

        val sale =
            ProductSaleService.sell(
                callerId = callerId,
                id = saleId,
                branchDayId = branchDayId,
                sessionId = sessionId,
                clientId = null,
                isWalkIn = false,
                productId = productId,
                quantity = 3,
                expectedVersion = 1,
            )

        assertNotNull(sale)
        assertEquals(saleId, sale.id)
        assertEquals(3, sale.quantity)
        assertEquals("Test Product", sale.productName)
        assertEquals(0, BigDecimal("100.00").compareTo(sale.unitPriceAtTime))
        assertEquals(0, BigDecimal("300.00").compareTo(sale.totalAmountAtTime))
        assertEquals(0, BigDecimal("10.00").compareTo(sale.commissionAmountAtTime))

        val stock =
            transaction {
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq branchId) and
                            (BranchInventoryTable.productId eq productId)
                    }.single()[BranchInventoryTable.currentStock]
            }
        assertEquals(17, stock)

        val movementCount =
            transaction {
                InventoryMovementTable
                    .selectAll()
                    .where { InventoryMovementTable.productSaleId eq saleId }
                    .count()
            }
        assertEquals(1, movementCount)
    }

    @Test
    fun `sell without EDIT_BRANCH_DATA is forbidden`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        assertFailsWith<ForbiddenResponse> {
            ProductSaleService.sell(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
                sessionId = sessionId,
                clientId = null,
                isWalkIn = false,
                productId = productId,
                quantity = 1,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `sell with non-existent branch day returns not found`() {
        assertFailsWith<NotFoundResponse> {
            ProductSaleService.sell(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = UUID.randomUUID(),
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 1,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `sell with non-existent product returns not found`() {
        assertFailsWith<NotFoundResponse> {
            ProductSaleService.sell(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = UUID.randomUUID(),
                quantity = 1,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `sell with insufficient stock returns bad request`() {
        assertFailsWith<BadRequestResponse> {
            ProductSaleService.sell(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 100,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `sell with version mismatch returns conflict`() {
        assertFailsWith<ConflictResponse> {
            ProductSaleService.sell(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 1,
                expectedVersion = 99,
            )
        }
    }

    @Test
    fun `sell with non-existent session returns not found`() {
        assertFailsWith<NotFoundResponse> {
            ProductSaleService.sell(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
                sessionId = UUID.randomUUID(),
                clientId = null,
                isWalkIn = false,
                productId = productId,
                quantity = 1,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `sell rejects session-linked sale with clientId`() {
        assertFailsWith<BadRequestResponse> {
            ProductSaleService.sell(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
                sessionId = sessionId,
                clientId = UUID.randomUUID(),
                isWalkIn = false,
                productId = productId,
                quantity = 1,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `sell rejects quantity less than 1`() {
        assertFailsWith<BadRequestResponse> {
            ProductSaleService.sell(
                callerId = callerId,
                id = UUID.randomUUID(),
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 0,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `sell idempotent duplicate returns same sale`() {
        val saleId = UUID.randomUUID()

        val first =
            ProductSaleService.sell(
                callerId = callerId,
                id = saleId,
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 2,
                expectedVersion = 1,
            )

        val second =
            ProductSaleService.sell(
                callerId = callerId,
                id = saleId,
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 2,
                expectedVersion = 1,
            )

        assertEquals(first.id, second.id)
        assertEquals(first.quantity, second.quantity)

        val stock =
            transaction {
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq branchId) and
                            (BranchInventoryTable.productId eq productId)
                    }.single()[BranchInventoryTable.currentStock]
            }
        assertEquals(18, stock)
    }

    @Test
    fun `sell writes audit log entry`() {
        val saleId = UUID.randomUUID()

        ProductSaleService.sell(
            callerId = callerId,
            id = saleId,
            branchDayId = branchDayId,
            sessionId = null,
            clientId = null,
            isWalkIn = true,
            productId = productId,
            quantity = 2,
            expectedVersion = 1,
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq ProductSaleTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
    }

    @Test
    fun `sell anonymous walk-in without client and session succeeds`() {
        val saleId = UUID.randomUUID()

        val sale =
            ProductSaleService.sell(
                callerId = callerId,
                id = saleId,
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 1,
                expectedVersion = 1,
            )

        assertNotNull(sale)
        assertTrue(sale.isWalkIn)
    }

    private fun ensureInventoryCard(
        branchId: UUID,
        productId: UUID,
        stock: Int,
    ) {
        transaction {
            BranchInventoryTable.insertIgnore {
                it[BranchInventoryTable.branchId] = branchId
                it[BranchInventoryTable.productId] = productId
                it[BranchInventoryTable.currentStock] = stock
                it[BranchInventoryTable.version] = 1
            }
        }
    }

    private fun deleteTestRows() {
        transaction {
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq callerId) or
                    (AuditLogTable.recordId eq productId)
            }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq callerId }
            InventoryMovementTable.deleteAll()
            ProductSaleTable.deleteAll()
            SessionTable.deleteWhere { SessionTable.branchDayId eq branchDayId }
            ClientTable.deleteWhere { ClientTable.id eq clientId }
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
