package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.authorization.Role
import com.companyb.companyapp.commerce.InventoryMovementTable
import com.companyb.companyapp.commerce.InventoryService
import com.companyb.companyapp.commerce.MovementType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BranchInventoryHistoryPostgresTest : BranchInventoryServicePostgresBase() {
    @Test
    fun `getMovementHistory returns all movements for branch ordered by movedAt DESC`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        val restock =
            InventoryService.recordMovement(
                callerId = callerId,
                movement(10, branchDayId),
            )
        val tester =
            InventoryService.recordMovement(
                callerId = callerId,
                movement(-2, branchDayId, MovementType.Tester),
            )

        val history = InventoryService.getMovementHistory(branchId)

        assertEquals(2, history.size)
        assertEquals(tester.id, history[0].id)
        assertEquals(restock.id, history[1].id)

        // "Show older": only what came before the oldest change already shown.
        val older = InventoryService.getMovementHistory(branchId, before = history[0].movedAt)
        assertEquals(listOf(restock.id), older.map { it.id })
        assertTrue(InventoryService.getMovementHistory(branchId, before = history[1].movedAt).isEmpty())
    }

    @Test
    fun `movement on REMITTED day with reason succeeds and flags audit entries`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val restockId = TestFixtures.uuid()
        InventoryService.recordMovement(
            callerId = callerId,
            NewInventoryMovement(
                movementId = restockId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 10,
                notes = null,
                branchDayId = remittedDayId,
                reason = "Coordinator correction",
            ),
        )
        val movementId = TestFixtures.uuid()

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                NewInventoryMovement(
                    movementId = movementId,
                    branchId = branchId,
                    productId = productId,
                    movementType = MovementType.Tester,
                    quantityChange = -2,
                    notes = null,
                    branchDayId = remittedDayId,
                    reason = "Coordinator correction",
                ),
            )

        assertEquals(movementId, movement.id)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq InventoryMovementTable.tableName) and
                            (AuditLogTable.recordId eq movementId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `getMovementHistory filters by branch day date`() {
        val pastDate = TestFixtures.today.minusDays(3)
        val pastDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, pastDate)
        InventoryService.ensureCard(callerId, branchId, productId)
        IdentityFixtures.member(callerId, branchId, Role.COORDINATOR)

        InventoryService.recordMovement(
            callerId = callerId,
            NewInventoryMovement(
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                notes = null,
                quantityChange = 10,
                branchDayId = pastDayId,
                reason = "Late delivery",
            ),
        )

        val history = InventoryService.getMovementHistory(branchId, pastDate)

        assertEquals(1, history.size)
        assertEquals(pastDayId, history[0].branchDayId)
    }

    @Test
    fun `getMovementHistory with date of another day returns empty`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, branchDayId),
        )

        val history =
            InventoryService.getMovementHistory(
                branchId,
                TestFixtures.today.minusDays(3),
            )

        assertTrue(history.isEmpty())
    }

    @Test
    fun `getMovementHistory does not leak movements from other branches`() {
        val otherBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Branch ${otherBranchId.toString().take(8)}")
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, otherBranchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val otherDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, branchDayId),
        )
        InventoryService.recordMovement(
            callerId = callerId,
            NewInventoryMovement(
                movementId = TestFixtures.uuid(),
                branchId = otherBranchId,
                productId = productId,
                movementType = MovementType.Restock,
                notes = null,
                quantityChange = 10,
                branchDayId = otherDayId,
            ),
        )
        InventoryService.recordMovement(
            callerId = callerId,
            NewInventoryMovement(
                movementId = TestFixtures.uuid(),
                branchId = otherBranchId,
                productId = productId,
                movementType = MovementType.Tester,
                quantityChange = -1,
                notes = null,
                branchDayId = otherDayId,
            ),
        )

        val history = InventoryService.getMovementHistory(branchId)

        assertEquals(1, history.size)
        assertEquals(branchId, history[0].branchId)
        assertEquals(branchDayId, history[0].branchDayId)
    }

    @Test
    fun `getMovementHistory returns empty for branch with no movements`() {
        val history = InventoryService.getMovementHistory(branchId)
        assertTrue(history.isEmpty())
    }

    @Test
    fun `getMovementHistory with non-existent branch returns not found`() {
        assertFailsWith<NotFoundException> {
            InventoryService.getMovementHistory(TestFixtures.uuid())
        }
    }
}
