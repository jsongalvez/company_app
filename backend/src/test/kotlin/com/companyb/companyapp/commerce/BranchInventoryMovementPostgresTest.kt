package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.InventoryMovementTable
import com.companyb.companyapp.commerce.InventoryService
import com.companyb.companyapp.commerce.MovementType
import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BranchInventoryMovementPostgresTest : BranchInventoryServicePostgresBase() {
    @Test
    fun `recordMovement with TESTER decreases stock and logs movement`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, BranchWorkforceFixtures.createBranchDayForToday(branchId)),
        )

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movement(-2, branchDayId, MovementType.Tester),
            )

        assertEquals(-2, movement.quantityChange)
        assertEquals(InventoryMovementReason.TESTER, movement.reason)

        val cards = InventoryService.getStock(branchId)
        assertEquals(8, cards[0].inventory.currentStock)
        assertEquals(3, cards[0].inventory.version)
    }

    @Test
    fun `recordMovement with SAMPLE decreases stock and logs movement`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, BranchWorkforceFixtures.createBranchDayForToday(branchId)),
        )

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movement(-3, branchDayId, MovementType.Sample),
            )

        assertEquals(-3, movement.quantityChange)
        assertEquals(InventoryMovementReason.SAMPLE, movement.reason)

        val cards = InventoryService.getStock(branchId)
        assertEquals(7, cards[0].inventory.currentStock)
    }

    @Test
    fun `recordMovement with MISSING decreases stock with notes`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, BranchWorkforceFixtures.createBranchDayForToday(branchId)),
        )

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                NewInventoryMovement(
                    movementId = TestFixtures.uuid(),
                    branchId = branchId,
                    productId = productId,
                    movementType = MovementType.Missing,
                    quantityChange = -1,
                    notes = "Lost during inventory count",
                    branchDayId = branchDayId,
                ),
            )

        assertEquals(-1, movement.quantityChange)
        assertEquals("Lost during inventory count", movement.notes)

        val cards = InventoryService.getStock(branchId)
        assertEquals(9, cards[0].inventory.currentStock)
    }

    @Test
    fun `recordMovement with ADJUSTMENT positive increases stock`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, BranchWorkforceFixtures.createBranchDayForToday(branchId)),
        )

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                NewInventoryMovement(
                    movementId = TestFixtures.uuid(),
                    branchId = branchId,
                    productId = productId,
                    movementType = MovementType.Adjustment,
                    quantityChange = 5,
                    notes = "Found extra stock",
                    branchDayId = branchDayId,
                ),
            )

        assertEquals(5, movement.quantityChange)

        val cards = InventoryService.getStock(branchId)
        assertEquals(15, cards[0].inventory.currentStock)
    }

    @Test
    fun `recordMovement over-deducting stock throws without writes`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        restock(productId, branchDayId, 5)
        val movementId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            InventoryService.recordMovement(
                callerId = callerId,
                NewInventoryMovement(
                    movementId = movementId,
                    branchId = branchId,
                    productId = productId,
                    movementType = MovementType.Missing,
                    quantityChange = -10,
                    notes = "Over-counted shrinkage",
                    branchDayId = branchDayId,
                ),
            )
        }

        val cards = InventoryService.getStock(branchId)
        assertEquals(5, cards.single().inventory.currentStock)
        assertEquals(
            0,
            transaction {
                InventoryMovementTable.selectAll().where { InventoryMovementTable.id eq movementId }.count()
            },
        )
    }

    @Test
    fun `recordMovement zero-quantity adjustment throws without writes`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        restock(productId, branchDayId, 5)
        val movementId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            InventoryService.recordMovement(
                callerId = callerId,
                NewInventoryMovement(
                    movementId = movementId,
                    branchId = branchId,
                    productId = productId,
                    movementType = MovementType.Adjustment,
                    quantityChange = 0,
                    notes = "No-op correction",
                    branchDayId = branchDayId,
                ),
            )
        }

        val cards = InventoryService.getStock(branchId)
        assertEquals(5, cards.single().inventory.currentStock)
        assertEquals(
            0,
            transaction {
                InventoryMovementTable.selectAll().where { InventoryMovementTable.id eq movementId }.count()
            },
        )
    }

    @Test
    fun `recordMovement without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, BranchWorkforceFixtures.createBranchDayForToday(branchId)),
        )
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movement(-1, branchDayId, MovementType.Tester),
            )

        assertEquals(-1, movement.quantityChange)
    }

    @Test
    fun `recordMovement with non-existent branch returns not found`() {
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        assertFailsWith<NotFoundException> {
            InventoryService.recordMovement(
                callerId = callerId,
                NewInventoryMovement(
                    movementId = TestFixtures.uuid(),
                    branchId = TestFixtures.uuid(),
                    productId = productId,
                    movementType = MovementType.Tester,
                    quantityChange = -1,
                    notes = null,
                    branchDayId = branchDayId,
                ),
            )
        }
    }

    @Test
    fun `recordMovement writes audit entries`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, BranchWorkforceFixtures.createBranchDayForToday(branchId)),
        )

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movementId = TestFixtures.uuid()

        InventoryService.recordMovement(
            callerId = callerId,
            movement(-2, branchDayId, MovementType.Tester, movementId),
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
}
