package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.InventoryMovementTable
import com.companyb.companyapp.commerce.ProductSaleRepository
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** A sale id sent again, sent at once from two places, or rolled back with its transaction. */
class ProductSaleReplayPostgresTest : ProductSaleServicePostgresBase() {
    @Test
    fun `sell rejects existing sale id from another branch day`() {
        val saleId = TestFixtures.uuid()
        val firstSale =
            ProductSaleService.sell(
                callerId = callerId,
                walkInSale(saleId, 1),
                expectedVersion = 1,
            )

        val foreignBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(foreignBranchId, "Foreign Retry Branch")
        val foreignBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(foreignBranchId)

        assertFailsWith<NotFoundException> {
            ProductSaleService.sell(
                callerId = callerId,
                NewProductSale(
                    id = saleId,
                    branchDayId = foreignBranchDayId,
                    sessionId = null,
                    clientId = null,
                    isWalkIn = true,
                    productId = productId,
                    quantity = 1,
                ),
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
    fun `sell idempotent duplicate returns same sale`() {
        val saleId = TestFixtures.uuid()

        val first =
            ProductSaleService.sell(
                callerId = callerId,
                walkInSale(saleId, 2),
                expectedVersion = 1,
            )

        val second =
            ProductSaleService.sell(
                callerId = callerId,
                walkInSale(saleId, 2),
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
            walkInSale(saleId, 1),
            expectedVersion = 1,
        )

        val foreignCallerId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(foreignCallerId, "foreign-user")

        assertFailsWith<ConflictException> {
            ProductSaleService.sell(
                callerId = foreignCallerId,
                walkInSale(saleId, 1),
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
            walkInSale(saleId, 1),
            expectedVersion = 1,
        )

        assertFailsWith<ConflictException> {
            ProductSaleService.sell(
                callerId = callerId,
                walkInSale(saleId, 2),
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
                                    walkInSale(saleId, 1),
                                    expectedVersion = 1,
                                )
                            },
                            Callable {
                                ProductSaleService.sell(
                                    callerId = callerId,
                                    walkInSale(saleId, 1),
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
                    walkInSale(saleId, 2),
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
                    walkInSale(saleId, 1),
                    expectedVersion = 1,
                )
            }
        } finally {
            CommissionService.failAfterReplacementForTests = false
        }

        assertEquals(0, transaction { ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count() })
        assertEquals(20, currentStock())
    }
}
