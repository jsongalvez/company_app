package com.companyb.companyapp.service

import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayAssignmentTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchInventoryTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CommissionManualInclusionTable
import com.companyb.companyapp.repository.model.CommissionSplitTable
import com.companyb.companyapp.repository.model.InventoryMovementTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.finance.commission.CommissionService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class CommissionServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val targetUserId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val categoryId = UUID.randomUUID()
    private val productId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()

    private lateinit var branchDayId: UUID
    private lateinit var productSaleId: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "commission-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestUser(targetUserId, "commission-target")
        trackOwned(AppUserTable, AppUserTable.id, targetUserId)
        DatabaseTestHelper.insertTestBranch(branchId, "Test Commission Branch")
        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestCategory(categoryId, "Test Commission Category")
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, categoryId)
        DatabaseTestHelper.insertTestProduct(productId, "Commission Product", categoryId)
        trackOwned(ProductTable, ProductTable.id, productId)
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        ensureInventoryCard(branchId, productId, 100)
        trackOwned(BranchInventoryTable, BranchInventoryTable.branchId, branchId)
        DatabaseTestHelper.grantEditBranchData(callerId, sourceId)
        productSaleId = createProductSale(branchDayId)
        trackOwned(ProductSaleTable, ProductSaleTable.id, productSaleId)
        trackOwned(InventoryMovementTable, InventoryMovementTable.branchId, branchId)
        insertClockIn(targetUserId, branchDayId)
        trackOwned(AttendanceTable, AttendanceTable.userId, targetUserId)
        trackOwned(BranchDayAssignmentTable, BranchDayAssignmentTable.userId, targetUserId)
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, targetUserId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, targetUserId)
        trackOwned(CommissionSplitTable, CommissionSplitTable.branchDayId, branchDayId)
        trackOwned(CommissionManualInclusionTable, CommissionManualInclusionTable.userId, targetUserId)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(SessionTable, SessionTable.branchDayId, branchDayId)
    }

    @Test
    fun `create inclusion succeeds with all fields`() {
        val inclusionId = UUID.randomUUID()

        val inclusion =
            CommissionService.createManualInclusion(
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
            CommissionService.createManualInclusion(
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
            CommissionService.createManualInclusion(
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

        CommissionService.createManualInclusion(
            callerId = callerId,
            id = inclusionId,
            productSaleId = productSaleId,
            userId = targetUserId,
            isIncluded = true,
            reason = "Initial",
        )

        val updated =
            CommissionService.createManualInclusion(
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
    fun `create without ASSIGN_COMPENSATION is allowed at service layer`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        val inclusionId = UUID.randomUUID()
        val inclusion =
            CommissionService.createManualInclusion(
                callerId = callerId,
                id = inclusionId,
                productSaleId = productSaleId,
                userId = targetUserId,
                isIncluded = true,
                reason = null,
            )

        trackOwned(CommissionManualInclusionTable, CommissionManualInclusionTable.userId, targetUserId)
        assertNotNull(inclusion)
        assertTrue(inclusion.isIncluded)
    }

    @Test
    fun `create with non-existent product sale returns not found`() {
        assertFailsWith<NotFoundException> {
            CommissionService.createManualInclusion(
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
                CommissionService.createManualInclusion(
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
            CommissionService.getByBranchDayId(branchDayId)
        assertTrue(splits.isNotEmpty())

        val targetSplit = splits.find { it.userId == targetUserId }
        assertNotNull(targetSplit)
        assertTrue(targetSplit.amount > BigDecimal.ZERO)
    }

    @Test
    fun `get splits returns correct data`() {
        val inclusionId = UUID.randomUUID()

        CommissionService.createManualInclusion(
            callerId = callerId,
            id = inclusionId,
            productSaleId = productSaleId,
            userId = targetUserId,
            isIncluded = true,
            reason = null,
        )

        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        val splits =
            CommissionService.getByBranchDayId(branchDayId)
        assertTrue(splits.isNotEmpty())

        val targetSplit = splits.find { it.userId == targetUserId }
        assertNotNull(targetSplit)
        assertEquals(branchDayId, targetSplit.branchDayId)
    }

    @Test
    fun `get splits without VIEW_BRANCH_DATA is allowed at service layer`() {
        val splits =
            CommissionService.getByBranchDayId(branchDayId)

        assertTrue(splits.isEmpty())
    }

    @Test
    fun `get splits with non-existent branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            CommissionService.getByBranchDayId(UUID.randomUUID())
        }
    }

    @Test
    fun `create writes audit log entry`() {
        val inclusionId = UUID.randomUUID()

        CommissionService.createManualInclusion(
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
}
