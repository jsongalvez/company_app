package com.companyb.companyapp.commerce
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.InventoryMovementTable
import com.companyb.companyapp.commerce.ProductSaleRepository
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.commerce.ProductTable
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.AttendanceTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// #597 scenario coverage stays whole (same precedent as SessionServicePostgresTest #593).
@Suppress("LargeClass") // #597
class ProductSaleServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    private val productName = "Sale Product ${TestFixtures.uuid().toString().take(8)}"

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "user")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Sale Branch")
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, productName, categoryId)
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        SessionClientFixtures.insertTestClient(clientId)
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        IdentityFixtures.grantEditBranchData(callerId, sourceId)
        ensureInventoryCard(branchId, productId, 20)
    }

    @Test
    fun `sell on REMITTED day with reason succeeds and flags audit entries`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val saleId = TestFixtures.uuid()

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
    }

    @Test
    fun `sell on REMITTED day recalculates persisted commission splits`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val clockBase = TestFixtures.realNow().atOffset(ZoneOffset.UTC)
        transaction {
            AttendanceTable.insert {
                it[AttendanceTable.id] = TestFixtures.uuid()
                it[AttendanceTable.branchDayId] = remittedDayId
                it[AttendanceTable.userId] = callerId
                it[AttendanceTable.markedBy] = callerId
                it[AttendanceTable.clockIn] = clockBase.minusHours(5)
            }
        }

        ProductSaleService.sell(
            callerId = callerId,
            id = TestFixtures.uuid(),
            branchDayId = remittedDayId,
            sessionId = null,
            clientId = null,
            isWalkIn = true,
            productId = productId,
            quantity = 1,
            expectedVersion = 1,
            reason = "Coordinator correction",
        )

        val persisted = CommissionService.getByBranchDayId(remittedDayId)
        assertEquals(1, persisted.size)
        assertEquals(BigDecimal("10.0000"), persisted.single().amount)
        val live = CommissionService.liveCommissions(remittedDayId)
        assertEquals(persisted.single().amount, live.getValue(callerId).amount)
    }

    @Test
    fun `sell creates product sale with stock decrement and movement`() {
        val saleId = TestFixtures.uuid()

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
        val movementReason =
            transaction {
                InventoryMovementTable
                    .selectAll()
                    .where { InventoryMovementTable.productSaleId eq saleId }
                    .single()[InventoryMovementTable.reason]
            }
        assertEquals(InventoryMovementReason.SALE, movementReason)
    }

    @Test
    fun `sell rejects inactive product without changing inventory`() {
        transaction {
            ProductTable.update({ ProductTable.id eq productId }) {
                it[ProductTable.isActive] = false
            }
        }
        val saleId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
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
            transaction { ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count() },
        )
        assertEquals(
            0,
            transaction {
                InventoryMovementTable
                    .selectAll()
                    .where { InventoryMovementTable.productSaleId eq saleId }
                    .count()
            },
        )
    }

    @Test
    fun `sell rejects session from another branch day`() {
        val foreignBranchId = TestFixtures.uuid()
        val foreignClientId = TestFixtures.uuid()
        val foreignSessionId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(foreignBranchId, "Foreign Sale Branch")
        val foreignBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(foreignBranchId)
        SessionClientFixtures.insertTestClient(foreignClientId)
        SessionClientFixtures.insertTestSession(foreignSessionId, foreignClientId, foreignBranchDayId)
        val saleId = TestFixtures.uuid()

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
        val saleId = TestFixtures.uuid()
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

        val foreignBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(foreignBranchId, "Foreign Retry Branch")
        val foreignBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(foreignBranchId)

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
        IdentityFixtures.revokeAllCapabilities(callerId)

        val sale =
            ProductSaleService.sell(
                callerId = callerId,
                id = TestFixtures.uuid(),
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
    }

    @Test
    fun `sell with non-existent branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            ProductSaleService.sell(
                callerId = callerId,
                id = TestFixtures.uuid(),
                branchDayId = TestFixtures.uuid(),
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
                id = TestFixtures.uuid(),
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = TestFixtures.uuid(),
                quantity = 1,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `sell without inventory card returns not found without writes`() {
        val unstockedProductId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProduct(
            unstockedProductId,
            "Unstocked ${unstockedProductId.toString().take(8)}",
            categoryId,
        )
        val saleId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            ProductSaleService.sell(
                callerId = callerId,
                id = saleId,
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = unstockedProductId,
                quantity = 1,
                expectedVersion = 1,
            )
        }

        assertEquals(
            0,
            transaction { ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count() },
        )
        assertEquals(
            0,
            transaction {
                InventoryMovementTable.selectAll().where { InventoryMovementTable.productSaleId eq saleId }.count()
            },
        )
        assertEquals(
            0,
            transaction { AuditLogTable.selectAll().where { AuditLogTable.recordId eq saleId }.count() },
        )
    }

    @Test
    fun `sell with unknown client returns not found without writes`() {
        val saleId = TestFixtures.uuid()
        val unknownClientId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            ProductSaleService.sell(
                callerId = callerId,
                id = saleId,
                branchDayId = branchDayId,
                sessionId = null,
                clientId = unknownClientId,
                isWalkIn = true,
                productId = productId,
                quantity = 1,
                expectedVersion = 1,
            )
        }

        assertEquals(20, currentStock())
        assertEquals(
            0,
            transaction { ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count() },
        )
        assertEquals(
            0,
            transaction {
                InventoryMovementTable.selectAll().where { InventoryMovementTable.productSaleId eq saleId }.count()
            },
        )
        assertEquals(
            0,
            transaction { AuditLogTable.selectAll().where { AuditLogTable.recordId eq saleId }.count() },
        )
    }

    @Test
    fun `sell with known client succeeds`() {
        val saleId = TestFixtures.uuid()

        val sale =
            ProductSaleService.sell(
                callerId = callerId,
                id = saleId,
                branchDayId = branchDayId,
                sessionId = null,
                clientId = clientId,
                isWalkIn = true,
                productId = productId,
                quantity = 1,
                expectedVersion = 1,
            )

        assertNotNull(sale)
        assertEquals(clientId, sale.clientId)
        assertEquals(19, currentStock())
    }

    @Test
    fun `sell with insufficient stock returns bad request`() {
        assertFailsWith<ValidationException> {
            ProductSaleService.sell(
                callerId = callerId,
                id = TestFixtures.uuid(),
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
                id = TestFixtures.uuid(),
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
        val saleId = TestFixtures.uuid()

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
    fun `sell rejects existing sale id from another creator`() {
        val saleId = TestFixtures.uuid()
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

        val foreignCallerId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(foreignCallerId, "foreign-user")

        assertFailsWith<ConflictException> {
            ProductSaleService.sell(
                callerId = foreignCallerId,
                id = saleId,
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 1,
                expectedVersion = 1,
            )
        }
        assertEquals(
            1,
            transaction {
                InventoryMovementTable
                    .selectAll()
                    .where {
                        InventoryMovementTable.productSaleId eq
                            saleId
                    }.count()
            },
        )
    }

    @Test
    fun `sell rejects existing sale id with altered request`() {
        val saleId = TestFixtures.uuid()
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

        assertFailsWith<ConflictException> {
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
        }
        assertEquals(
            1,
            transaction {
                InventoryMovementTable
                    .selectAll()
                    .where {
                        InventoryMovementTable.productSaleId eq
                            saleId
                    }.count()
            },
        )
    }

    @Test
    fun `sell concurrent same UUID creates one sale and movement`() {
        val saleId = TestFixtures.uuid()
        val executor = Executors.newFixedThreadPool(2)
        val results =
            try {
                executor
                    .invokeAll(
                        listOf(
                            Callable {
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
                            },
                            Callable {
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
                            },
                        ),
                    ).map { it.get() }
            } finally {
                executor.shutdown()
            }

        assertEquals(listOf(saleId, saleId), results.map { it.id })
        assertEquals(1L, transaction { ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count() })
        assertEquals(
            1L,
            transaction {
                InventoryMovementTable
                    .selectAll()
                    .where { InventoryMovementTable.productSaleId eq saleId }
                    .count()
            },
        )
    }

    @Test
    fun `sell trigger rolls back all writes when enclosing transaction fails`() {
        val saleId = TestFixtures.uuid()

        assertFailsWith<IllegalStateException> {
            transaction {
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
                error("injected commission trigger failure")
            }
        }

        assertEquals(0, transaction { ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count() })
        assertEquals(
            0,
            transaction {
                InventoryMovementTable
                    .selectAll()
                    .where { InventoryMovementTable.productSaleId eq saleId }
                    .count()
            },
        )
        assertEquals(0, transaction { AuditLogTable.selectAll().where { AuditLogTable.recordId eq saleId }.count() })
        assertEquals(
            20,
            transaction {
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq branchId) and
                            (BranchInventoryTable.productId eq productId)
                    }.single()[BranchInventoryTable.currentStock]
            },
        )
    }

    @Test
    fun `sell rolls back when commission replacement fails`() {
        val saleId = TestFixtures.uuid()
        CommissionService.failAfterReplacementForTests = true
        try {
            assertFailsWith<IllegalStateException> {
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
            }
        } finally {
            CommissionService.failAfterReplacementForTests = false
        }

        assertEquals(0, transaction { ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count() })
        assertEquals(20, currentStock())
    }

    @Test
    fun `sell writes audit log entry`() {
        val saleId = TestFixtures.uuid()

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
        val saleId = TestFixtures.uuid()

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

    @Test
    fun `sell rejects quantity over smallint range without inserting`() {
        val saleId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            ProductSaleService.sell(
                callerId = callerId,
                id = saleId,
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 40000,
                expectedVersion = 1,
            )
        }

        val saleCount =
            transaction {
                ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count()
            }
        assertEquals(0, saleCount)
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

    private fun currentStock(): Int =
        transaction {
            BranchInventoryTable
                .selectAll()
                .where {
                    (BranchInventoryTable.branchId eq branchId) and
                        (BranchInventoryTable.productId eq productId)
                }.single()[BranchInventoryTable.currentStock]
        }

    // #925 persisted-range pre-gate (the #924 fail-closed class): the sale snapshots
    // unit_price_at_time and the computed total into NUMERIC(10,2) — a max-legal price
    // times quantity 2 overflows the total, so it must 400 before the insert.

    @Test
    fun `sell rejects computed total over NUMERIC(10,2) range with no row written`() {
        val maxProductId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProduct(
            maxProductId,
            "Max Price Product",
            categoryId,
            unitPrice = BigDecimal("99999999.99"),
            commissionAmount = BigDecimal("10.00"),
        )
        ensureInventoryCard(branchId, maxProductId, 20)

        val blockedId = TestFixtures.uuid()
        assertFailsWith<ValidationException> {
            ProductSaleService.sell(
                callerId = callerId,
                id = blockedId,
                branchDayId = branchDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = maxProductId,
                quantity = 2,
                expectedVersion = 1,
            )
        }
        assertNull(ProductSaleRepository.findById(blockedId))
    }
}
