package com.companyb.companyapp.commerce

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.InventoryMovementTable
import com.companyb.companyapp.commerce.InventoryService
import com.companyb.companyapp.commerce.MovementType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BranchInventoryRestockPostgresTest : BranchInventoryServicePostgresBase() {
    @Test
    fun `restock increments stock and logs movement`() {
        InventoryService.ensureCard(callerId, branchId, productId)

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movementId = TestFixtures.uuid()

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movement(10, branchDayId, movementId = movementId),
            )

        assertEquals(10, movement.quantityChange)
        assertEquals("RESTOCK", movement.reason.name)

        val cards = InventoryService.getStock(branchId)
        assertEquals(10, cards[0].inventory.currentStock)
        assertEquals(2, cards[0].inventory.version)
    }

    @Test
    fun `restock without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movement(10, branchDayId),
            )

        assertEquals(10, movement.quantityChange)
    }

    @Test
    fun `restock with non-existent branch returns not found`() {
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        assertFailsWith<NotFoundException> {
            InventoryService.recordMovement(
                callerId = callerId,
                NewInventoryMovement(
                    movementId = TestFixtures.uuid(),
                    branchId = TestFixtures.uuid(),
                    productId = productId,
                    movementType = MovementType.Restock,
                    notes = null,
                    quantityChange = 10,
                    branchDayId = branchDayId,
                ),
            )
        }
    }

    @Test
    fun `movement with branch day from another branch returns not found`() {
        val otherBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Inventory Branch $otherBranchId")
        val foreignBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val movementId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            InventoryService.recordMovement(
                callerId = callerId,
                movement(10, foreignBranchDayId, movementId = movementId),
            )
        }

        val movementCount =
            transaction {
                InventoryMovementTable
                    .selectAll()
                    .where { InventoryMovementTable.id eq movementId }
                    .count()
            }
        assertEquals(0, movementCount)
        val card =
            transaction {
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq branchId) and
                            (BranchInventoryTable.productId eq productId)
                    }.single()
            }
        assertEquals(0, card[BranchInventoryTable.currentStock])
        assertEquals(1, card[BranchInventoryTable.version])
    }

    @Test
    fun `repeating movement ID does not apply stock change twice`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movementId = TestFixtures.uuid()

        val first =
            InventoryService.recordMovement(
                callerId = callerId,
                movement(10, branchDayId, movementId = movementId),
            )
        val retry =
            InventoryService.recordMovement(
                callerId = callerId,
                movement(10, branchDayId, movementId = movementId),
            )

        assertEquals(first, retry)
        val card =
            transaction {
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq branchId) and
                            (BranchInventoryTable.productId eq productId)
                    }.single()
            }
        assertEquals(10, card[BranchInventoryTable.currentStock])
        assertEquals(2, card[BranchInventoryTable.version])
    }

    @Test
    fun `inventory movement UUID retry preserves original request`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        val movementId = TestFixtures.uuid()

        val first =
            InventoryService.recordMovement(
                callerId = callerId,
                NewInventoryMovement(
                    movementId = movementId,
                    branchId = branchId,
                    productId = productId,
                    movementType = MovementType.Restock,
                    quantityChange = 10,
                    notes = "initial stock",
                    branchDayId = branchDayId,
                ),
            )
        val retry =
            InventoryService.recordMovement(
                callerId = callerId,
                NewInventoryMovement(
                    movementId = movementId,
                    branchId = branchId,
                    productId = productId,
                    movementType = MovementType.Restock,
                    quantityChange = 10,
                    notes = "initial stock",
                    branchDayId = branchDayId,
                ),
            )

        assertEquals(first, retry)
        assertEquals(1, transaction { InventoryMovementTable.selectAll().count() })
        assertEquals(
            1,
            transaction { AuditLogTable.selectAll().where { AuditLogTable.recordId eq movementId }.count() },
        )
    }

    @Test
    fun `inventory movement UUID retry rejects altered request ownership`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        val movementId = TestFixtures.uuid()
        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, branchDayId, movementId = movementId),
        )

        assertFailsWith<ConflictException> {
            InventoryService.recordMovement(
                callerId = callerId,
                movement(11, branchDayId, movementId = movementId),
            )
        }
        assertEquals(
            10,
            InventoryService
                .getStock(branchId)
                .single()
                .inventory
                .currentStock,
        )
    }

    @Test
    fun `movement ID from another branch does not create target inventory card`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movementId = TestFixtures.uuid()
        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, branchDayId, movementId = movementId),
        )

        val otherBranchId = TestFixtures.uuid()
        val otherProductId = TestFixtures.uuid()
        val otherCategoryId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Collision Branch $otherBranchId")
        CommerceFinanceFixtures.insertTestCategory(otherCategoryId)
        CommerceFinanceFixtures.insertTestProduct(otherProductId, categoryId = otherCategoryId)
        val otherDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)

        assertFailsWith<ConflictException> {
            InventoryService.recordMovement(
                callerId = callerId,
                NewInventoryMovement(
                    movementId = movementId,
                    branchId = otherBranchId,
                    productId = otherProductId,
                    movementType = MovementType.Restock,
                    quantityChange = 5,
                    notes = null,
                    branchDayId = otherDayId,
                ),
            )
        }

        assertEquals(
            0,
            transaction {
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq otherBranchId) and
                            (BranchInventoryTable.productId eq otherProductId)
                    }.count()
            },
        )
    }

    @Test
    fun `restock writes audit entries`() {
        InventoryService.ensureCard(callerId, branchId, productId)

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movementId = TestFixtures.uuid()

        InventoryService.recordMovement(
            callerId = callerId,
            movement(10, branchDayId, movementId = movementId),
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
