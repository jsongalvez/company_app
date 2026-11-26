package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.InventoryMovementTable
import com.companyb.companyapp.commerce.ProductSaleTable
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.workforce.AttendanceTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Selling a product: price, commission, stock, audit and remitted days. */
class ProductSaleServicePostgresTest : ProductSaleServicePostgresBase() {
    @Test
    fun `a sale records the price and commission the branch set for that product`() {
        BranchPriceService.setPrice(callerId, branchId, productId, BigDecimal("300"), BigDecimal("40"))

        val sale =
            ProductSaleService.sell(
                callerId = callerId,
                NewProductSale(
                    id = TestFixtures.uuid(),
                    branchDayId = branchDayId,
                    sessionId = sessionId,
                    clientId = null,
                    isWalkIn = false,
                    productId = productId,
                    quantity = 2,
                ),
                expectedVersion = 1,
            )

        assertEquals(0, BigDecimal("300.00").compareTo(sale.unitPriceAtTime))
        assertEquals(0, BigDecimal("600.00").compareTo(sale.totalAmountAtTime))
        assertEquals(0, BigDecimal("40.00").compareTo(sale.commissionAmountAtTime))
    }

    @Test
    fun `sell on REMITTED day with reason succeeds and flags audit entries`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        val saleId = TestFixtures.uuid()

        val sale =
            ProductSaleService.sell(
                callerId = callerId,
                NewProductSale(
                    id = saleId,
                    branchDayId = remittedDayId,
                    sessionId = null,
                    clientId = null,
                    isWalkIn = true,
                    productId = productId,
                    quantity = 1,
                    reason = "Coordinator correction",
                ),
                expectedVersion = 1,
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
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
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
            NewProductSale(
                id = TestFixtures.uuid(),
                branchDayId = remittedDayId,
                sessionId = null,
                clientId = null,
                isWalkIn = true,
                productId = productId,
                quantity = 1,
                reason = "Coordinator correction",
            ),
            expectedVersion = 1,
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
                NewProductSale(
                    id = saleId,
                    branchDayId = branchDayId,
                    sessionId = sessionId,
                    clientId = null,
                    isWalkIn = false,
                    productId = productId,
                    quantity = 3,
                ),
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
    fun `sales without a version sell from current stock one after another`() {
        repeat(2) {
            ProductSaleService.sell(
                callerId = callerId,
                walkInSale(TestFixtures.uuid(), 2),
                expectedVersion = null,
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
        assertEquals(16, stock)
    }

    @Test
    fun `sell without EDIT_BRANCH_DATA is allowed at service layer`() {
        val sale =
            ProductSaleService.sell(
                callerId = callerId,
                NewProductSale(
                    id = TestFixtures.uuid(),
                    branchDayId = branchDayId,
                    sessionId = sessionId,
                    clientId = null,
                    isWalkIn = false,
                    productId = productId,
                    quantity = 1,
                ),
                expectedVersion = 1,
            )

        assertNotNull(sale)
        assertEquals(1, sale.quantity)
    }

    @Test
    fun `sell with known client succeeds`() {
        val saleId = TestFixtures.uuid()

        val sale =
            ProductSaleService.sell(
                callerId = callerId,
                NewProductSale(
                    id = saleId,
                    branchDayId = branchDayId,
                    sessionId = null,
                    clientId = clientId,
                    isWalkIn = true,
                    productId = productId,
                    quantity = 1,
                ),
                expectedVersion = 1,
            )

        assertNotNull(sale)
        assertEquals(clientId, sale.clientId)
        assertEquals(19, currentStock())
    }

    @Test
    fun `sell writes audit log entry`() {
        val saleId = TestFixtures.uuid()

        ProductSaleService.sell(
            callerId = callerId,
            walkInSale(saleId, 2),
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
                walkInSale(saleId, 1),
                expectedVersion = 1,
            )

        assertNotNull(sale)
        assertTrue(sale.isWalkIn)
    }
}
