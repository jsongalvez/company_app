package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.repository.CommissionSplitRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CommissionManualInclusionTable
import com.companyb.companyapp.repository.model.CommissionSplitTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class CommissionServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()

    private lateinit var branchDayId: UUID
    private lateinit var productSaleId: UUID

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertTestUser(callerId, "commission-caller")
        DatabaseTestHelper.insertTestUser(targetUserId, "commission-target")
        DatabaseTestHelper.insertTestBranch(branchId, "Test Commission Branch")
        DatabaseTestHelper.insertTestCategory(categoryId, "Test Commission Category")
        DatabaseTestHelper.insertTestProduct(productId, "Commission Product", categoryId)
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        ensureInventoryCard(branchId, productId, 100)
        DatabaseTestHelper.grantEditBranchData(callerId, sourceId)
        productSaleId = createProductSale(branchDayId)
        insertClockIn(targetUserId, branchDayId)
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `create inclusion succeeds with all fields`() {
        val inclusionId = UUID.randomUUID()

        val inclusion =
            CommissionManualInclusionService.create(
                callerId = callerId,
                id = inclusionId,
                productSaleId = productSaleId,
                userId = targetUserId,
                isIncluded = true,
                reason = "Should receive commission",
            )

        assertNotNull(inclusion)
        assertEquals(inclusionId, inclusion.id)
        assertEquals(productSaleId, inclusion.productSaleId)
        assertEquals(targetUserId, inclusion.userId)
        assertEquals(true, inclusion.isIncluded)
        assertEquals("Should receive commission", inclusion.reason)
        assertEquals(callerId, inclusion.assignedBy)
        assertNotNull(inclusion.assignedAt)
    }

    @Test
    fun `create inclusion without reason succeeds`() {
        val inclusionId = UUID.randomUUID()

        val inclusion =
            CommissionManualInclusionService.create(
                callerId = callerId,
                id = inclusionId,
                productSaleId = productSaleId,
                userId = targetUserId,
                isIncluded = true,
                reason = null,
            )

        assertNotNull(inclusion)
        assertTrue(inclusion.isIncluded)
    }

    @Test
    fun `create exclusion succeeds`() {
        val inclusionId = UUID.randomUUID()

        val inclusion =
            CommissionManualInclusionService.create(
                callerId = callerId,
                id = inclusionId,
                productSaleId = productSaleId,
                userId = targetUserId,
                isIncluded = false,
                reason = "Not eligible",
            )

        assertNotNull(inclusion)
        assertEquals(false, inclusion.isIncluded)
    }

    @Test
    fun `upsert updates existing inclusion`() {
        val inclusionId = UUID.randomUUID()

        CommissionManualInclusionService.create(
            callerId = callerId,
            id = inclusionId,
            productSaleId = productSaleId,
            userId = targetUserId,
            isIncluded = true,
            reason = "Initial",
        )

        val updated =
            CommissionManualInclusionService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                productSaleId = productSaleId,
                userId = targetUserId,
                isIncluded = false,
                reason = "Updated reason",
            )

        assertEquals(false, updated.isIncluded)
        assertEquals("Updated reason", updated.reason)

        val count =
            transaction {
                CommissionManualInclusionTable
                    .selectAll()
                    .where { CommissionManualInclusionTable.productSaleId eq productSaleId }
                    .count()
            }
        assertEquals(1, count)
    }

    @Test
    fun `create without ASSIGN_COMPENSATION is forbidden`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        assertFailsWith<ForbiddenResponse> {
            CommissionManualInclusionService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                productSaleId = productSaleId,
                userId = targetUserId,
                isIncluded = true,
                reason = null,
            )
        }
    }

    @Test
    fun `create with non-existent product sale returns not found`() {
        assertFailsWith<NotFoundResponse> {
            CommissionManualInclusionService.create(
                callerId = callerId,
                id = UUID.randomUUID(),
                productSaleId = UUID.randomUUID(),
                userId = targetUserId,
                isIncluded = true,
                reason = null,
            )
        }
    }

    @Test
    fun `create triggers commission recalculation and creates split`() {
        val inclusionId = UUID.randomUUID()

        val (_, duration) =
            measureTimedValue {
                CommissionManualInclusionService.create(
                    callerId = callerId,
                    id = inclusionId,
                    productSaleId = productSaleId,
                    userId = targetUserId,
                    isIncluded = true,
                    reason = null,
                )
            }
        assertTrue(duration < 5.seconds, "commission recalculation regressed: took $duration")

        val splits =
            CommissionSplitRepository.findByBranchDayId(branchDayId)
        assertTrue(splits.isNotEmpty())

        val targetSplit = splits.find { it.userId == targetUserId }
        assertNotNull(targetSplit)
        assertTrue(targetSplit.amount > BigDecimal.ZERO)
    }

    @Test
    fun `get splits returns correct data`() {
        val inclusionId = UUID.randomUUID()

        CommissionManualInclusionService.create(
            callerId = callerId,
            id = inclusionId,
            productSaleId = productSaleId,
            userId = targetUserId,
            isIncluded = true,
            reason = null,
        )

        grantViewBranchData(callerId)

        val splits =
            CommissionSplitService.getByBranchDayId(callerId, branchDayId)
        assertTrue(splits.isNotEmpty())

        val targetSplit = splits.find { it.userId == targetUserId }
        assertNotNull(targetSplit)
        assertEquals(branchDayId, targetSplit.branchDayId)
    }

    @Test
    fun `get splits without VIEW_BRANCH_DATA is forbidden`() {
        assertFailsWith<ForbiddenResponse> {
            CommissionSplitService.getByBranchDayId(callerId, branchDayId)
        }
    }

    @Test
    fun `get splits with non-existent branch day returns not found`() {
        assertFailsWith<NotFoundResponse> {
            CommissionSplitService.getByBranchDayId(callerId, UUID.randomUUID())
        }
    }

    @Test
    fun `create writes audit log entry`() {
        val inclusionId = UUID.randomUUID()

        CommissionManualInclusionService.create(
            callerId = callerId,
            id = inclusionId,
            productSaleId = productSaleId,
            userId = targetUserId,
            isIncluded = true,
            reason = "Audit test",
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq CommissionManualInclusionTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
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

    private fun createProductSale(branchDayId: UUID): UUID {
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
        return saleId
    }

    private fun insertClockIn(
        userId: UUID,
        branchDayId: UUID,
    ) {
        transaction {
            val attendanceId = UUID.randomUUID()
            AttendanceTable.insertIgnore {
                it[AttendanceTable.id] = attendanceId
                it[AttendanceTable.branchDayId] = branchDayId
                it[AttendanceTable.userId] = userId
                it[AttendanceTable.markedBy] = callerId
                it[AttendanceTable.clockIn] = OffsetDateTime.now().minusMinutes(30)
            }
            val assignmentId = UUID.randomUUID()
            BranchDayAssignmentTable.insertIgnore {
                it[BranchDayAssignmentTable.id] = assignmentId
                it[BranchDayAssignmentTable.branchDayId] = branchDayId
                it[BranchDayAssignmentTable.userId] = userId
                it[BranchDayAssignmentTable.isRelief] = false
            }
        }
    }

    private fun grantViewBranchData(userId: UUID) {
        DatabaseTestHelper.grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
    }

    private fun deleteTestRows() {
        transaction {
            AuditLogTable.deleteWhere {
                (AuditLogTable.changedBy eq callerId) or
                    (AuditLogTable.changedBy eq targetUserId)
            }
            UserCapabilityTable.deleteWhere {
                (UserCapabilityTable.userId eq callerId) or
                    (UserCapabilityTable.userId eq targetUserId)
            }
            CommissionSplitTable.deleteAll()
            CommissionManualInclusionTable.deleteAll()
            BranchDayAssignmentTable.deleteWhere {
                (BranchDayAssignmentTable.userId eq callerId) or
                    (BranchDayAssignmentTable.userId eq targetUserId)
            }
            AttendanceTable.deleteWhere {
                (AttendanceTable.userId eq callerId) or
                    (AttendanceTable.userId eq targetUserId)
            }
            InventoryMovementTable.deleteAll()
            ProductSaleTable.deleteAll()
            SessionTable.deleteWhere { SessionTable.branchDayId eq branchDayId }
            ClientTable.deleteWhere { ClientTable.id eq clientId }
            BranchInventoryTable.deleteWhere { BranchInventoryTable.branchId eq branchId }
            BranchDayTable.deleteWhere { BranchDayTable.branchId eq branchId }
            ProductTable.deleteWhere { ProductTable.id eq productId }
            ProductCategoryTable.deleteWhere { ProductCategoryTable.id eq categoryId }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            AppUserTable.deleteWhere {
                (AppUserTable.id eq callerId) or
                    (AppUserTable.id eq targetUserId)
            }
        }
    }
}
