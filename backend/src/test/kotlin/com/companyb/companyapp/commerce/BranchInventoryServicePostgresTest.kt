package com.companyb.companyapp.commerce
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.InventoryMovementTable
import com.companyb.companyapp.commerce.InventoryService
import com.companyb.companyapp.commerce.MovementType
import com.companyb.companyapp.commerce.ProductTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// #597 scenario coverage stays whole (same precedent as SessionServicePostgresTest #593).
@Suppress("LargeClass") // #597
class BranchInventoryServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "inv-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Inventory Branch ${branchId.toString().take(8)}")
        CommerceFinanceFixtures.insertTestCategory(categoryId)
        CommerceFinanceFixtures.insertTestProduct(productId, categoryId = categoryId)
    }

    @Test
    fun `ensureCard creates inventory card with zero stock`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)

        InventoryService.ensureCard(callerId, branchId, productId)

        val cards = InventoryService.getStock(branchId)
        assertEquals(1, cards.size)
        assertEquals(productId, cards[0].inventory.productId)
        assertEquals(0, cards[0].inventory.currentStock)
        assertEquals(1, cards[0].inventory.version)
    }

    @Test
    fun `ensureCard writes insert audit row attributed to caller`() {
        val card = InventoryService.ensureCard(callerId, branchId, productId)

        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq BranchInventoryTable.tableName) and
                            (AuditLogTable.recordId eq card.id)
                    }.single()
            }
        assertEquals(AuditAction.INSERT, audit[AuditLogTable.action])
        assertEquals(callerId, audit[AuditLogTable.changedBy])
        assertEquals(branchId, audit[AuditLogTable.branchId])
    }

    @Test
    fun `ensureCard on existing card writes no audit row without mutating the card`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)

        val ensured = InventoryService.ensureCard(callerId, branchId, productId)

        assertEquals(1, ensured.version)
        val rows =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq BranchInventoryTable.tableName) and
                            (AuditLogTable.recordId eq ensured.id)
                    }.toList()
            }
        assertEquals(1, rows.size)
        assertEquals(AuditAction.INSERT, rows.single()[AuditLogTable.action])
    }

    @Test
    fun `getStock includes product price and commission from the join`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)

        val cards = InventoryService.getStock(branchId)

        assertEquals(1, cards.size)
        assertEquals(BigDecimal("100.00"), cards[0].unitPrice)
        assertEquals(BigDecimal("10.00"), cards[0].commissionAmount)
    }

    @Test
    fun `ensureCard without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(callerId, branchId, productId)

        val cards = InventoryService.getStock(branchId)
        assertEquals(1, cards.size)
        assertEquals(productId, cards[0].inventory.productId)
        assertEquals(0, cards[0].inventory.currentStock)
        assertEquals(1, cards[0].inventory.version)
    }

    @Test
    fun `ensureCard with non-existent branch returns not found`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)

        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(callerId, TestFixtures.uuid(), productId)
        }
    }

    @Test
    fun `ensureCard with non-existent product returns not found`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)

        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(callerId, branchId, TestFixtures.uuid())
        }
    }

    @Test
    fun `inactive product is hidden from inventory reads and preserves history`() {
        val activeProductId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProduct(
            id = activeProductId,
            categoryId = categoryId,
            name = "Active Inventory Product",
        )
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, branchId, activeProductId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movementId = TestFixtures.uuid()
        val firstMovement = restock(productId, branchDayId, 10, movementId)
        deactivateProduct()

        val stock = InventoryService.getStock(branchId)
        assertEquals(1, stock.size)
        assertEquals(activeProductId, stock.single().inventory.productId)
        val lowStock = InventoryService.getLowStockAlerts(branchId)
        assertEquals(1, lowStock.size)
        assertEquals(activeProductId, lowStock.single().inventory.productId)
        assertTrue(InventoryService.getMovementHistory(branchId).isEmpty())
        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(callerId, branchId, productId)
        }
        assertEquals(
            firstMovement,
            restock(productId, branchDayId, 10, movementId),
        )
        assertFailsWith<NotFoundException> {
            restock(productId, branchDayId, 5)
        }

        assertEquals(1L, cardCount(productId))
        val card = readCard(productId)
        assertEquals(10, card[BranchInventoryTable.currentStock])
        assertEquals(2, card[BranchInventoryTable.version])
        assertEquals(1L, movementCount(productId))
    }

    @Test
    fun `inactive product rejects inventory card and movement creation`() {
        deactivateProduct()

        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(callerId, branchId, productId)
        }

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        assertFailsWith<NotFoundException> {
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 10,
                notes = null,
                branchDayId = branchDayId,
            )
        }

        val cardCount =
            transaction {
                BranchInventoryTable
                    .selectAll()
                    .where {
                        (BranchInventoryTable.branchId eq branchId) and
                            (BranchInventoryTable.productId eq productId)
                    }.count()
            }
        val movementCount =
            transaction {
                InventoryMovementTable
                    .selectAll()
                    .where {
                        (InventoryMovementTable.branchId eq branchId) and
                            (InventoryMovementTable.productId eq productId)
                    }.count()
            }
        assertEquals(0L, cardCount)
        assertEquals(0L, movementCount)
    }

    @Test
    fun `restock increments stock and logs movement`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movementId = TestFixtures.uuid()

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 10,
                notes = null,
                branchDayId = branchDayId,
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
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                notes = null,
                quantityChange = 10,
                branchDayId = branchDayId,
            )

        assertEquals(10, movement.quantityChange)
    }

    @Test
    fun `restock with non-existent branch returns not found`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        assertFailsWith<NotFoundException> {
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = TestFixtures.uuid(),
                branchId = TestFixtures.uuid(),
                productId = productId,
                movementType = MovementType.Restock,
                notes = null,
                quantityChange = 10,
                branchDayId = branchDayId,
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
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 10,
                notes = null,
                branchDayId = foreignBranchDayId,
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
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 10,
                notes = null,
                branchDayId = branchDayId,
            )
        val retry =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 10,
                notes = null,
                branchDayId = branchDayId,
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
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val movementId = TestFixtures.uuid()

        val first =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 10,
                notes = "initial stock",
                branchDayId = branchDayId,
            )
        val retry =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 10,
                notes = "initial stock",
                branchDayId = branchDayId,
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
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val movementId = TestFixtures.uuid()
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = movementId,
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            quantityChange = 10,
            notes = null,
            branchDayId = branchDayId,
        )

        assertFailsWith<ConflictException> {
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 11,
                notes = null,
                branchDayId = branchDayId,
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
            movementId = movementId,
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            quantityChange = 10,
            notes = null,
            branchDayId = branchDayId,
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
                movementId = movementId,
                branchId = otherBranchId,
                productId = otherProductId,
                movementType = MovementType.Restock,
                quantityChange = 5,
                notes = null,
                branchDayId = otherDayId,
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
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movementId = TestFixtures.uuid()

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = movementId,
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            quantityChange = 10,
            notes = null,
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
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        val cards = InventoryService.getStock(branchId)
        assertTrue(cards.isEmpty())
    }

    @Test
    fun `findByBranch without MANAGE_PRODUCTS is allowed at service layer`() {
        val cards = InventoryService.getStock(branchId)

        assertTrue(cards.isEmpty())
    }

    @Test
    fun `findByBranch with non-existent branch returns not found`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        assertFailsWith<NotFoundException> {
            InventoryService.getStock(TestFixtures.uuid())
        }
    }

    @Test
    fun `getMovementHistory returns all movements for branch ordered by movedAt DESC`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        val restock =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                notes = null,
                quantityChange = 10,
                branchDayId = branchDayId,
            )
        val tester =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Tester,
                quantityChange = -2,
                notes = null,
                branchDayId = branchDayId,
            )

        val history = InventoryService.getMovementHistory(branchId)

        assertEquals(2, history.size)
        assertEquals(tester.id, history[0].id)
        assertEquals(restock.id, history[1].id)
    }

    @Test
    fun `movement on REMITTED day with reason succeeds and flags audit entries`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val restockId = TestFixtures.uuid()
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = restockId,
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            quantityChange = 10,
            notes = null,
            branchDayId = remittedDayId,
            reason = "Coordinator correction",
        )
        val movementId = TestFixtures.uuid()

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Tester,
                quantityChange = -2,
                notes = null,
                branchDayId = remittedDayId,
                reason = "Coordinator correction",
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
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        val pastDate = TestFixtures.today.minusDays(3)
        val pastDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, pastDate)
        InventoryService.ensureCard(callerId, branchId, productId)
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_PAST_DAY,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = pastDayId,
        )

        val history = InventoryService.getMovementHistory(branchId, pastDate)

        assertEquals(1, history.size)
        assertEquals(pastDayId, history[0].branchDayId)
    }

    @Test
    fun `getMovementHistory with date of another day returns empty`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = branchDayId,
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
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, otherBranchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val otherDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = branchDayId,
        )
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = otherBranchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = otherDayId,
        )
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = otherBranchId,
            productId = productId,
            movementType = MovementType.Tester,
            quantityChange = -1,
            notes = null,
            branchDayId = otherDayId,
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

    @Test
    fun `recordMovement with TESTER decreases stock and logs movement`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId),
        )

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Tester,
                quantityChange = -2,
                notes = null,
                branchDayId = branchDayId,
            )

        assertEquals(-2, movement.quantityChange)
        assertEquals(InventoryMovementReason.TESTER, movement.reason)

        val cards = InventoryService.getStock(branchId)
        assertEquals(8, cards[0].inventory.currentStock)
        assertEquals(3, cards[0].inventory.version)
    }

    @Test
    fun `recordMovement with SAMPLE decreases stock and logs movement`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId),
        )

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Sample,
                quantityChange = -3,
                notes = null,
                branchDayId = branchDayId,
            )

        assertEquals(-3, movement.quantityChange)
        assertEquals(InventoryMovementReason.SAMPLE, movement.reason)

        val cards = InventoryService.getStock(branchId)
        assertEquals(7, cards[0].inventory.currentStock)
    }

    @Test
    fun `recordMovement with MISSING decreases stock with notes`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId),
        )

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Missing,
                quantityChange = -1,
                notes = "Lost during inventory count",
                branchDayId = branchDayId,
            )

        assertEquals(-1, movement.quantityChange)
        assertEquals("Lost during inventory count", movement.notes)

        val cards = InventoryService.getStock(branchId)
        assertEquals(9, cards[0].inventory.currentStock)
    }

    @Test
    fun `recordMovement with ADJUSTMENT positive increases stock`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId),
        )

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Adjustment,
                quantityChange = 5,
                notes = "Found extra stock",
                branchDayId = branchDayId,
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
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Missing,
                quantityChange = -10,
                notes = "Over-counted shrinkage",
                branchDayId = branchDayId,
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
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Adjustment,
                quantityChange = 0,
                notes = "No-op correction",
                branchDayId = branchDayId,
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
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId),
        )
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        val movement =
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Tester,
                quantityChange = -1,
                notes = null,
                branchDayId = branchDayId,
            )

        assertEquals(-1, movement.quantityChange)
    }

    @Test
    fun `recordMovement with non-existent branch returns not found`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        assertFailsWith<NotFoundException> {
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = TestFixtures.uuid(),
                branchId = TestFixtures.uuid(),
                productId = productId,
                movementType = MovementType.Tester,
                quantityChange = -1,
                notes = null,
                branchDayId = branchDayId,
            )
        }
    }

    @Test
    fun `recordMovement writes audit entries`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId),
        )

        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        val movementId = TestFixtures.uuid()

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = movementId,
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Tester,
            quantityChange = -2,
            notes = null,
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
    fun `getLowStockAlerts returns products at or below threshold`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = branchDayId,
        )

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Tester,
            quantityChange = -6,
            notes = null,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(4, lowStock[0].inventory.currentStock)
    }

    @Test
    fun `getLowStockAlerts returns empty when no products are low stock`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertTrue(lowStock.isEmpty())
    }

    @Test
    fun `getLowStockAlerts with non-existent branch returns not found`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)

        assertFailsWith<NotFoundException> {
            InventoryService.getLowStockAlerts(TestFixtures.uuid())
        }
    }

    @Test
    fun `getLowStockAlerts without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 3,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(3, lowStock[0].inventory.currentStock)
    }

    @Test
    fun `getLowStockAlerts uses per-product reorder point`() {
        val highReorderProductId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProduct(
            id = highReorderProductId,
            categoryId = categoryId,
            name = "High Reorder Product",
            reorderPoint = 10,
        )
        IdentityFixtures.grantManageProducts(callerId, sourceId)

        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, branchId, highReorderProductId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        restock(productId, branchDayId, 10)
        restock(highReorderProductId, branchDayId, 10)
        consume(productId, branchDayId, MovementType.Tester, -2)
        consume(highReorderProductId, branchDayId, MovementType.Tester, -2)

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(highReorderProductId, lowStock[0].inventory.productId)
        assertEquals(8, lowStock[0].inventory.currentStock)
    }

    @Test
    fun `getLowStockAlerts with null reorderPoint falls back to global default`() {
        val noReorderProductId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProduct(
            id = noReorderProductId,
            categoryId = categoryId,
            name = "No Reorder Product",
        )
        IdentityFixtures.grantManageProducts(callerId, sourceId)

        InventoryService.ensureCard(callerId, branchId, noReorderProductId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = noReorderProductId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 4,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(noReorderProductId, lowStock[0].inventory.productId)
        assertEquals(4, lowStock[0].inventory.currentStock)
    }

    @Test
    fun `getLowStockAlerts with thresholdOverride overrides stored thresholds`() {
        val highReorderProductId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProduct(
            id = highReorderProductId,
            categoryId = categoryId,
            name = "High Reorder Product",
            reorderPoint = 10,
        )
        IdentityFixtures.grantManageProducts(callerId, sourceId)

        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, branchId, highReorderProductId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = branchDayId,
        )
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = highReorderProductId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 12,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId, thresholdOverride = 15)

        assertEquals(2, lowStock.size)
    }

    @Test
    fun `getLowStockAlerts returns product at exactly the threshold`() {
        IdentityFixtures.grantManageProducts(callerId, sourceId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)

        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 5,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(5, lowStock[0].inventory.currentStock)
    }

    private fun deactivateProduct() {
        transaction {
            ProductTable.update({ ProductTable.id eq productId }) {
                it[ProductTable.isActive] = false
            }
        }
    }

    private fun restock(
        targetProductId: UUID,
        branchDayId: UUID,
        quantity: Int,
        movementId: UUID = TestFixtures.uuid(),
    ): InventoryMovement =
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = movementId,
            branchId = branchId,
            productId = targetProductId,
            movementType = MovementType.Restock,
            quantityChange = quantity,
            notes = null,
            branchDayId = branchDayId,
        )

    private fun consume(
        targetProductId: UUID,
        branchDayId: UUID,
        movementType: MovementType,
        quantityChange: Int,
    ) {
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = targetProductId,
            movementType = movementType,
            quantityChange = quantityChange,
            notes = null,
            branchDayId = branchDayId,
        )
    }

    private fun cardCount(targetProductId: UUID): Long =
        transaction {
            BranchInventoryTable
                .selectAll()
                .where {
                    (BranchInventoryTable.branchId eq branchId) and
                        (BranchInventoryTable.productId eq targetProductId)
                }.count()
        }

    private fun movementCount(targetProductId: UUID): Long =
        transaction {
            InventoryMovementTable
                .selectAll()
                .where {
                    (InventoryMovementTable.branchId eq branchId) and
                        (InventoryMovementTable.productId eq targetProductId)
                }.count()
        }

    private fun readCard(targetProductId: UUID): org.jetbrains.exposed.v1.core.ResultRow =
        transaction {
            BranchInventoryTable
                .selectAll()
                .where {
                    (BranchInventoryTable.branchId eq branchId) and
                        (BranchInventoryTable.productId eq targetProductId)
                }.single()
        }
}
