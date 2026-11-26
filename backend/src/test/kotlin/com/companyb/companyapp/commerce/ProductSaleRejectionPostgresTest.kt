package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.InventoryMovementTable
import com.companyb.companyapp.commerce.ProductSaleRepository
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.commerce.ProductTable
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Sales the service refuses, and that a refusal writes nothing. */
class ProductSaleRejectionPostgresTest : ProductSaleServicePostgresBase() {
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
                walkInSale(saleId, 1),
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
                NewProductSale(
                    id = saleId,
                    branchDayId = branchDayId,
                    sessionId = foreignSessionId,
                    clientId = null,
                    isWalkIn = false,
                    productId = productId,
                    quantity = 1,
                ),
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
    fun `sell with non-existent branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            ProductSaleService.sell(
                callerId = callerId,
                NewProductSale(
                    id = TestFixtures.uuid(),
                    branchDayId = TestFixtures.uuid(),
                    sessionId = null,
                    clientId = null,
                    isWalkIn = true,
                    productId = productId,
                    quantity = 1,
                ),
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `sell with non-existent product returns not found`() {
        assertFailsWith<NotFoundException> {
            ProductSaleService.sell(
                callerId = callerId,
                NewProductSale(
                    id = TestFixtures.uuid(),
                    branchDayId = branchDayId,
                    sessionId = null,
                    clientId = null,
                    isWalkIn = true,
                    productId = TestFixtures.uuid(),
                    quantity = 1,
                ),
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
                NewProductSale(
                    id = saleId,
                    branchDayId = branchDayId,
                    sessionId = null,
                    clientId = null,
                    isWalkIn = true,
                    productId = unstockedProductId,
                    quantity = 1,
                ),
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
                NewProductSale(
                    id = saleId,
                    branchDayId = branchDayId,
                    sessionId = null,
                    clientId = unknownClientId,
                    isWalkIn = true,
                    productId = productId,
                    quantity = 1,
                ),
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
    fun `sell with insufficient stock returns bad request`() {
        assertFailsWith<ValidationException> {
            ProductSaleService.sell(
                callerId = callerId,
                walkInSale(TestFixtures.uuid(), 100),
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `sell with version mismatch returns conflict`() {
        assertFailsWith<ConflictException> {
            ProductSaleService.sell(
                callerId = callerId,
                walkInSale(TestFixtures.uuid(), 1),
                expectedVersion = 99,
            )
        }
    }

    @Test
    fun `sell rejects quantity over smallint range without inserting`() {
        val saleId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            ProductSaleService.sell(
                callerId = callerId,
                walkInSale(saleId, 40000),
                expectedVersion = 1,
            )
        }

        val saleCount =
            transaction {
                ProductSaleTable.selectAll().where { ProductSaleTable.id eq saleId }.count()
            }
        assertEquals(0, saleCount)
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
                NewProductSale(
                    id = blockedId,
                    branchDayId = branchDayId,
                    sessionId = null,
                    clientId = null,
                    isWalkIn = true,
                    productId = maxProductId,
                    quantity = 2,
                ),
                expectedVersion = 1,
            )
        }
        assertNull(ProductSaleRepository.findById(blockedId))
    }
}
