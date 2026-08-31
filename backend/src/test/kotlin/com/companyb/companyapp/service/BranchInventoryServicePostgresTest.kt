package com.companyb.companyapp.service
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.inventory.InventoryService
import com.companyb.companyapp.service.inventory.MovementType
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
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

@Suppress("LargeClass")
class BranchInventoryServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "inv-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestBranch(branchId, "Test Inventory Branch ${branchId.toString().take(8)}")
        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestCategory(categoryId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, categoryId)
        DatabaseTestHelper.insertTestProduct(productId, categoryId = categoryId)
        trackOwned(ProductTable, ProductTable.id, productId)
    }

    @Test
    fun `ensureCard creates inventory card with zero stock`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        InventoryService.ensureCard(callerId, branchId, productId)

        val cards = InventoryService.getStock(branchId)
        assertEquals(1, cards.size)
        assertEquals(productId, cards[0].inventory.productId)
        assertEquals(0, cards[0].inventory.currentStock)
        assertEquals(1, cards[0].inventory.version)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `ensureCard on existing card writes update audit row without mutating the card`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

        val ensured = InventoryService.ensureCard(callerId, branchId, productId)

        assertEquals(1, ensured.version)
        val updates =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq BranchInventoryTable.tableName) and
                            (AuditLogTable.recordId eq ensured.id) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.count()
            }
        assertEquals(1, updates)
    }

    @Test
    fun `getStock includes product price and commission from the join`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)

        val cards = InventoryService.getStock(branchId)

        assertEquals(1, cards.size)
        assertEquals(BigDecimal("100.00"), cards[0].unitPrice)
        assertEquals(BigDecimal("10.00"), cards[0].commissionAmount)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `ensureCard without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

        val cards = InventoryService.getStock(branchId)
        assertEquals(1, cards.size)
        assertEquals(productId, cards[0].inventory.productId)
        assertEquals(0, cards[0].inventory.currentStock)
        assertEquals(1, cards[0].inventory.version)
    }

    @Test
    fun `ensureCard with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(callerId, TestFixtures.uuid(), productId)
        }
    }

    @Test
    fun `ensureCard with non-existent product returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(callerId, branchId, TestFixtures.uuid())
        }
    }

    @Suppress("LongMethod")
    @Test
    fun `inactive product is hidden from inventory reads and preserves history`() {
        val activeProductId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestProduct(
            id = activeProductId,
            categoryId = categoryId,
            name = "Active Inventory Product",
        )
        trackOwned(ProductTable, ProductTable.id, activeProductId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, branchId, activeProductId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        val movementId = TestFixtures.uuid()
        val firstMovement =
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
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = movementId,
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 10,
                notes = null,
                branchDayId = branchDayId,
            ),
        )
        assertFailsWith<NotFoundException> {
            InventoryService.recordMovement(
                callerId = callerId,
                movementId = TestFixtures.uuid(),
                branchId = branchId,
                productId = productId,
                movementType = MovementType.Restock,
                quantityChange = 5,
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
        assertEquals(1L, cardCount)
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
        assertEquals(1L, movementCount)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `inactive product rejects inventory card and movement creation`() {
        deactivateProduct()

        assertFailsWith<NotFoundException> {
            InventoryService.ensureCard(callerId, branchId, productId)
        }

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
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
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `restock without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `restock with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        DatabaseTestHelper.insertTestBranch(otherBranchId, "Other Inventory Branch $otherBranchId")
        trackOwned(BranchTable, BranchTable.id, otherBranchId)
        val foreignBranchDayId = DatabaseTestHelper.createBranchDayForToday(otherBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, otherBranchId)
        InventoryService.ensureCard(callerId, branchId, productId)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
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
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
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
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `inventory movement UUID retry rejects altered request ownership`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
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
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)

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
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
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
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)

        val otherBranchId = TestFixtures.uuid()
        val otherProductId = TestFixtures.uuid()
        val otherCategoryId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(otherBranchId, "Collision Branch $otherBranchId")
        DatabaseTestHelper.insertTestCategory(otherCategoryId)
        DatabaseTestHelper.insertTestProduct(otherProductId, categoryId = otherCategoryId)
        trackOwned(BranchTable, BranchTable.id, otherBranchId)
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, otherCategoryId)
        trackOwned(ProductTable, ProductTable.id, otherProductId)
        val otherDayId = DatabaseTestHelper.createBranchDayForToday(otherBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, otherBranchId)

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
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `findByBranch returns empty for branch with no inventory`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
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
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        assertFailsWith<NotFoundException> {
            InventoryService.getStock(TestFixtures.uuid())
        }
    }

    @Test
    fun `getMovementHistory returns all movements for branch ordered by movedAt DESC`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `movement on REMITTED day with reason succeeds and flags audit entries`() {
        val remittedDayId =
            DatabaseTestHelper.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        trackOwned(BranchDayTable, BranchDayTable.id, remittedDayId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getMovementHistory filters by branch day date`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val pastDate = TestFixtures.today.minusDays(3)
        val pastDayId = DatabaseTestHelper.createBranchDayForDate(branchId, pastDate)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        InventoryService.ensureCard(callerId, branchId, productId)
        DatabaseTestHelper.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_PAST_DAY,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getMovementHistory with date of another day returns empty`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getMovementHistory does not leak movements from other branches`() {
        val otherBranchId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(otherBranchId, "Other Branch ${otherBranchId.toString().take(8)}")
        trackOwned(BranchTable, BranchTable.id, otherBranchId)
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, otherBranchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        val otherDayId = DatabaseTestHelper.createBranchDayForToday(otherBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, otherBranchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, otherBranchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
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
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `recordMovement with SAMPLE decreases stock and logs movement`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `recordMovement with MISSING decreases stock with notes`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `recordMovement with ADJUSTMENT positive increases stock`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
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
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `recordMovement with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = productId,
            movementType = MovementType.Restock,
            notes = null,
            quantityChange = 10,
            branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId),
        )

        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts returns products at or below threshold`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts returns empty when no products are low stock`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts with non-existent branch returns not found`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        assertFailsWith<NotFoundException> {
            InventoryService.getLowStockAlerts(TestFixtures.uuid())
        }
    }

    @Test
    fun `getLowStockAlerts without MANAGE_PRODUCTS is allowed at service layer`() {
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Suppress("LongMethod")
    @Test
    fun `getLowStockAlerts uses per-product reorder point`() {
        val highReorderProductId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestProduct(
            id = highReorderProductId,
            categoryId = categoryId,
            name = "High Reorder Product",
            reorderPoint = 10,
        )
        trackOwned(ProductTable, ProductTable.id, highReorderProductId)
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, branchId, highReorderProductId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
            quantityChange = 10,
            branchDayId = branchDayId,
        )

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
        InventoryService.recordMovement(
            callerId = callerId,
            movementId = TestFixtures.uuid(),
            branchId = branchId,
            productId = highReorderProductId,
            movementType = MovementType.Tester,
            quantityChange = -2,
            notes = null,
            branchDayId = branchDayId,
        )

        val lowStock = InventoryService.getLowStockAlerts(branchId)

        assertEquals(1, lowStock.size)
        assertEquals(highReorderProductId, lowStock[0].inventory.productId)
        assertEquals(8, lowStock[0].inventory.currentStock)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts with null reorderPoint falls back to global default`() {
        val noReorderProductId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestProduct(
            id = noReorderProductId,
            categoryId = categoryId,
            name = "No Reorder Product",
        )
        trackOwned(ProductTable, ProductTable.id, noReorderProductId)
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        InventoryService.ensureCard(callerId, branchId, noReorderProductId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts with thresholdOverride overrides stored thresholds`() {
        val highReorderProductId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestProduct(
            id = highReorderProductId,
            categoryId = categoryId,
            name = "High Reorder Product",
            reorderPoint = 10,
        )
        trackOwned(ProductTable, ProductTable.id, highReorderProductId)
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        InventoryService.ensureCard(callerId, branchId, productId)
        InventoryService.ensureCard(callerId, branchId, highReorderProductId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `getLowStockAlerts returns product at exactly the threshold`() {
        DatabaseTestHelper.grantManageProducts(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        InventoryService.ensureCard(callerId, branchId, productId)
        val branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

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
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.movedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    private fun deactivateProduct() {
        transaction {
            ProductTable.update({ ProductTable.id eq productId }) {
                it[ProductTable.isActive] = false
            }
        }
    }
}
