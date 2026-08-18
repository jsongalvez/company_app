package com.companyb.companyapp.service

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.ProductSaleRepository
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
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProductSaleServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()

    private lateinit var branchDayId: UUID

    private val productName = "Sale Product ${UUID.randomUUID().toString().take(8)}"

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "user")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestBranch(branchId, "Test Sale Branch")
        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestCategory(categoryId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, categoryId)
        DatabaseTestHelper.insertTestProduct(productId, productName, categoryId)
        trackOwned(ProductTable, ProductTable.id, productId)
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        DatabaseTestHelper.insertTestClient(clientId)
        trackOwned(ClientTable, ClientTable.id, clientId)
        DatabaseTestHelper.insertTestSession(sessionId, clientId, branchDayId)
        trackOwned(SessionTable, SessionTable.clientId, clientId)
        DatabaseTestHelper.grantEditBranchData(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        ensureInventoryCard(branchId, productId, 20)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(ProductSaleTable, ProductSaleTable.handledBy, callerId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.productId, productId)
    }

    @Test
    fun `sell on REMITTED day with reason succeeds and flags audit entries`() {
        val remittedDayId =
            DatabaseTestHelper.createRemittedBranchDay(
                branchId,
                LocalDate.now(BranchDayService.manilaZone).minusDays(3),
            )
        trackOwned(BranchDayTable, BranchDayTable.id, remittedDayId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val saleId = UUID.randomUUID()

        val sale =
            ProductSaleService.sell(
                callerId = callerId,
                id = saleId,
                branchDayId = remittedDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 1,
                expectedVersion = 1,
                reason = "Coordinator correction",
            )

        assertNotNull(sale)
        val saleAudit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq ProductSaleTable.tableName) and
                            (AuditLogTable.recordId eq saleId)
                    }.single()
            }
        assertEquals(true, saleAudit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", saleAudit[AuditLogTable.reason])
        trackOwned(ProductSaleTable, ProductSaleTable.handledBy, callerId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.productId, productId)
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

        trackOwned(ProductSaleTable, ProductSaleTable.handledBy, callerId)

        assertNotNull(sale)
        assertEquals(saleId, sale.id)
        assertEquals(3, sale.quantity)
        assertEquals(productName, sale.productName)
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
    fun `sell rejects session from another branch day`() {
        val foreignBranchId = UUID.randomUUID()
        val foreignClientId = UUID.randomUUID()
        val foreignSessionId = UUID.randomUUID()
        DatabaseTestHelper.insertTestBranch(foreignBranchId, "Foreign Sale Branch")
        trackOwned(BranchTable, BranchTable.id, foreignBranchId)
        val foreignBranchDayId = DatabaseTestHelper.createBranchDayForToday(foreignBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, foreignBranchId)
        DatabaseTestHelper.insertTestClient(foreignClientId)
        trackOwned(ClientTable, ClientTable.id, foreignClientId)
        DatabaseTestHelper.insertTestSession(foreignSessionId, foreignClientId, foreignBranchDayId)
        trackOwned(SessionTable, SessionTable.clientId, foreignClientId)
        val saleId = UUID.randomUUID()

        assertFailsWith<NotFoundException> {
            ProductSaleService.sell(
                callerId = callerId,
                id = saleId,
                branchDayId = branchDayId,
                sessionId = foreignSessionId,
                clientId = null,
                isWalkIn = false,
                productId = productId,
                quantity = 1,
                expectedVersion = 1,
            )
        }

        val stock =
            transaction {
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq branchId) and
                            (BranchInventoryTable.productId eq productId)
                    }.single()[BranchInventoryTable.currentStock]
            }
        assertEquals(20, stock)
        assertEquals(
            0,
            transaction {
                ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count()
            },
        )
        assertEquals(
            0,
            transaction {
                InventoryMovementTable.selectAll().where { InventoryMovementTable.productSaleId eq saleId }.count()
            },
        )
        assertEquals(
            0,
            transaction {
                AuditLogTable.selectAll().where { AuditLogTable.recordId eq saleId }.count()
            },
        )
    }

    @Test
    fun `sell rejects existing sale id from another branch day`() {
        val saleId = UUID.randomUUID()
        val firstSale =
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
        trackOwned(ProductSaleTable, ProductSaleTable.handledBy, callerId)

        val foreignBranchId = UUID.randomUUID()
        DatabaseTestHelper.insertTestBranch(foreignBranchId, "Foreign Retry Branch")
        trackOwned(BranchTable, BranchTable.id, foreignBranchId)
        val foreignBranchDayId = DatabaseTestHelper.createBranchDayForToday(foreignBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, foreignBranchId)

        assertFailsWith<NotFoundException> {
            ProductSaleService.sell(
                callerId = callerId,
                id = saleId,
                branchDayId = foreignBranchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 1,
                expectedVersion = 1,
            )
        }

        assertEquals(firstSale.id, ProductSaleRepository.findById(saleId)?.id)
        assertEquals(
            1,
            transaction {
                InventoryMovementTable.selectAll().where { InventoryMovementTable.productSaleId eq saleId }.count()
            },
        )
    }

    @Test
    fun `sell without EDIT_BRANCH_DATA is allowed at service layer`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        val sale =
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

        assertNotNull(sale)
        assertEquals(1, sale.quantity)
        trackOwned(ProductSaleTable, ProductSaleTable.handledBy, callerId)
    }

    @Test
    fun `sell with non-existent branch day returns not found`() {
        assertFailsWith<NotFoundException> {
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
        assertFailsWith<NotFoundException> {
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
        assertFailsWith<ValidationException> {
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
        assertFailsWith<ConflictException> {
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

        trackOwned(ProductSaleTable, ProductSaleTable.handledBy, callerId)

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

        trackOwned(ProductSaleTable, ProductSaleTable.handledBy, callerId)

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

        trackOwned(ProductSaleTable, ProductSaleTable.handledBy, callerId)

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
}
