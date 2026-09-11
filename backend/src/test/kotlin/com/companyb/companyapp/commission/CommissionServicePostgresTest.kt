package com.companyb.companyapp.commission
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.commerce.BranchInventoryTable
import com.companyb.companyapp.commerce.ProductSaleService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.AttendanceTable
import com.companyb.companyapp.workforce.BranchDayAssignmentTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class CommissionServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val targetUserId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID
    private lateinit var productSaleId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "commission-caller")
        IdentityFixtures.insertTestUser(targetUserId, "commission-target")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Commission Branch")
        CommerceFinanceFixtures.insertTestCategory(categoryId, "Test Commission Category")
        CommerceFinanceFixtures.insertTestProduct(productId, "Commission Product", categoryId)
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        ensureInventoryCard(branchId, productId, 100)
        IdentityFixtures.grantEditBranchData(callerId, sourceId)
        productSaleId = createProductSale(branchDayId)
        insertClockIn(targetUserId, branchDayId)
        IdentityFixtures.grantAssignCompensation(callerId, sourceId)
    }

    @Test
    fun `create inclusion succeeds with all fields`() {
        val inclusionId = TestFixtures.uuid()

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
        val inclusionId = TestFixtures.uuid()

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
        val inclusionId = TestFixtures.uuid()

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
        val inclusionId = TestFixtures.uuid()

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
                id = TestFixtures.uuid(),
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
    fun `create with id reused on different sale and user conflicts`() {
        val reusedId = TestFixtures.uuid()
        CommissionService.createManualInclusion(
            callerId = callerId,
            id = reusedId,
            productSaleId = productSaleId,
            userId = targetUserId,
            isIncluded = true,
            reason = null,
        )

        val otherSaleId = createProductSale(branchDayId, expectedVersion = 2)
        val otherUserId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherUserId, "commission-other")

        // #894 (#865 precedent) — an id owned by another (sale, user) pair is a
        // client conflict (409), never a 500.
        assertFailsWith<ConflictException> {
            CommissionService.createManualInclusion(
                callerId = callerId,
                id = reusedId,
                productSaleId = otherSaleId,
                userId = otherUserId,
                isIncluded = true,
                reason = null,
            )
        }
    }

    @Test
    fun `create without ASSIGN_COMPENSATION is allowed at service layer`() {
        IdentityFixtures.revokeAllCapabilities(callerId)

        val inclusionId = TestFixtures.uuid()
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
    fun `create with non-existent product sale returns not found`() {
        assertFailsWith<NotFoundException> {
            CommissionService.createManualInclusion(
                callerId = callerId,
                id = TestFixtures.uuid(),
                productSaleId = TestFixtures.uuid(),
                userId = targetUserId,
                isIncluded = true,
                reason = null,
            )
        }
    }

    @Test
    fun `create inclusion throws 404 for unknown user with no row written`() {
        val unknownUserId = TestFixtures.uuid()
        val blockedId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            CommissionService.createManualInclusion(
                callerId = callerId,
                id = blockedId,
                productSaleId = productSaleId,
                userId = unknownUserId,
                isIncluded = true,
                reason = null,
            )
        }
        val stored =
            transaction {
                CommissionManualInclusionTable
                    .selectAll()
                    .where { CommissionManualInclusionTable.id eq blockedId }
                    .singleOrNull()
            }
        assertNull(stored)
        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq CommissionManualInclusionTable.tableName) and
                            (AuditLogTable.recordId eq blockedId)
                    }.count()
            }
        assertEquals(0, auditCount)
    }

    @Test
    fun `create on lazily-PAST day persists splits without manual recalculate`() {
        val pastDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today.minusDays(1))
        val pastSaleId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestProductSale(
            id = pastSaleId,
            branchDayId = pastDayId,
            productId = productId,
            handledBy = callerId,
        )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        CommissionService.createManualInclusion(
            callerId = callerId,
            id = TestFixtures.uuid(),
            productSaleId = pastSaleId,
            userId = targetUserId,
            isIncluded = true,
            reason = null,
        )

        val splits = CommissionService.getByBranchDayId(pastDayId)
        assertTrue(splits.isNotEmpty(), "PAST-day inclusion must persist splits, not wait for manual recalc")
        val targetSplit = splits.find { it.userId == targetUserId }
        assertNotNull(targetSplit)
        assertTrue(targetSplit.amount > BigDecimal.ZERO)
        val live = CommissionService.liveCommissions(pastDayId)
        assertEquals(live[targetUserId]?.amount, targetSplit.amount)
    }

    @Test
    fun `create triggers commission recalculation and creates split`() {
        val inclusionId = TestFixtures.uuid()

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
        val inclusionId = TestFixtures.uuid()

        CommissionService.createManualInclusion(
            callerId = callerId,
            id = inclusionId,
            productSaleId = productSaleId,
            userId = targetUserId,
            isIncluded = true,
            reason = null,
        )

        grantViewBranchData(callerId)

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
            CommissionService.getByBranchDayId(TestFixtures.uuid())
        }
    }

    @Test
    fun `create writes audit log entry`() {
        val inclusionId = TestFixtures.uuid()

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

    @Test
    fun `manual inclusion trigger rolls back inclusion audit and splits when transaction fails`() {
        val inclusionId = TestFixtures.uuid()

        assertFailsWith<IllegalStateException> {
            transaction {
                CommissionService.createManualInclusion(
                    callerId = callerId,
                    id = inclusionId,
                    productSaleId = productSaleId,
                    userId = targetUserId,
                    isIncluded = true,
                    reason = null,
                )
                error("injected commission trigger failure")
            }
        }

        assertEquals(
            0,
            transaction {
                CommissionManualInclusionTable
                    .selectAll()
                    .where {
                        CommissionManualInclusionTable.id eq
                            inclusionId
                    }.count()
            },
        )
        assertEquals(
            0,
            transaction { AuditLogTable.selectAll().where { AuditLogTable.recordId eq inclusionId }.count() },
        )
        assertTrue(CommissionService.getByBranchDayId(branchDayId).isEmpty())
    }

    @Test
    fun `concurrent repeated recalculation converges without losing splits`() {
        CommissionService.createManualInclusion(
            callerId = callerId,
            id = TestFixtures.uuid(),
            productSaleId = productSaleId,
            userId = targetUserId,
            isIncluded = true,
            reason = null,
        )

        val executor = Executors.newFixedThreadPool(2)
        try {
            val first = executor.submit<Unit> { CommissionService.recalculate(branchDayId) }
            val second = executor.submit<Unit> { CommissionService.recalculate(branchDayId) }
            first.get()
            second.get()
        } finally {
            executor.shutdownNow()
        }

        val splits = CommissionService.getByBranchDayId(branchDayId)
        assertEquals(1, splits.size)
        assertEquals(targetUserId, splits.single().userId)
        assertEquals(BigDecimal("20.0000"), splits.single().amount)
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

    private fun createProductSale(
        branchDayId: UUID,
        expectedVersion: Int = 1,
    ): UUID {
        val saleId = TestFixtures.uuid()
        ProductSaleService.sell(
            callerId = callerId,
            id = saleId,
            branchDayId = branchDayId,
            sessionId = null,
            clientId = null,
            isWalkIn = true,
            productId = productId,
            quantity = 2,
            expectedVersion = expectedVersion,
        )
        return saleId
    }

    private fun insertClockIn(
        userId: UUID,
        branchDayId: UUID,
    ) {
        transaction {
            val attendanceId = TestFixtures.uuid()
            AttendanceTable.insertIgnore {
                it[AttendanceTable.id] = attendanceId
                it[AttendanceTable.branchDayId] = branchDayId
                it[AttendanceTable.userId] = userId
                it[AttendanceTable.markedBy] = callerId
                it[AttendanceTable.clockIn] = TestFixtures.now.minusMinutes(30)
            }
            val assignmentId = TestFixtures.uuid()
            BranchDayAssignmentTable.insertIgnore {
                it[BranchDayAssignmentTable.id] = assignmentId
                it[BranchDayAssignmentTable.branchDayId] = branchDayId
                it[BranchDayAssignmentTable.userId] = userId
                it[BranchDayAssignmentTable.isRelief] = false
            }
        }
    }

    private fun grantViewBranchData(userId: UUID) {
        IdentityFixtures.grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
    }
}
