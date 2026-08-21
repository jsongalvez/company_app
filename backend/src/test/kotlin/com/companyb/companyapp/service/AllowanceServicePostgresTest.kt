package com.companyb.companyapp.service
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.AllowanceTable
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AllowanceServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val targetUserId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "allowance-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestUser(targetUserId, "allowance-target")
        trackOwned(AppUserTable, AppUserTable.id, targetUserId)
        DatabaseTestHelper.insertTestBranch(branchId, "Test Allowance Branch")
        trackOwned(BranchTable, BranchTable.id, branchId)
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        trackOwned(BranchDayTable, BranchDayTable.id, branchDayId)
        DatabaseTestHelper.grantAssignCompensation(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
    }

    @Test
    fun `create allowance on REMITTED day with reason succeeds and flags audit entry`() {
        val remittedDayId =
            DatabaseTestHelper.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        trackOwned(BranchDayTable, BranchDayTable.id, remittedDayId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val allowanceId = TestFixtures.uuid()

        val allowance =
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = remittedDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
                reason = "Coordinator correction",
            )

        assertNotNull(allowance)
        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq AllowanceTable.tableName) and
                            (AuditLogTable.recordId eq allowanceId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `create allowance succeeds`() {
        val allowanceId = TestFixtures.uuid()

        val allowance =
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )

        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId)

        assertNotNull(allowance)
        assertEquals(allowanceId, allowance.id)
        assertEquals(branchDayId, allowance.branchDayId)
        assertEquals(targetUserId, allowance.userId)
        assertEquals(0, BigDecimal("500.00").compareTo(allowance.amount))
        assertEquals(callerId, allowance.assignedBy)
    }

    @Test
    fun `create idempotent duplicate returns existing`() {
        val allowanceId = TestFixtures.uuid()

        val first =
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )

        val second =
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )

        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId)

        assertEquals(first.id, second.id)
    }

    @Test
    fun `create rejects UUID collision from another branch day without auditing`() {
        val allowanceId = TestFixtures.uuid()
        val otherDayId =
            DatabaseTestHelper.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        trackOwned(BranchDayTable, BranchDayTable.id, otherDayId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        AllowanceService.create(callerId, allowanceId, branchDayId, targetUserId, BigDecimal("500.00"))
        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId)

        assertFailsWith<NotFoundException> {
            AllowanceService.create(
                callerId,
                allowanceId,
                otherDayId,
                targetUserId,
                BigDecimal("500.00"),
                reason = "Coordinator correction",
            )
        }

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq AllowanceTable.tableName) and
                            (AuditLogTable.recordId eq allowanceId)
                    }.count()
            }
        assertEquals(1, auditCount)
    }

    @Test
    fun `concurrent UUID collision from another branch day returns one success and one not found`() {
        val allowanceId = TestFixtures.uuid()
        val otherDayId =
            DatabaseTestHelper.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        trackOwned(BranchDayTable, BranchDayTable.id, otherDayId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<Throwable?>())
        val threads =
            listOf(branchDayId, otherDayId).map { requestedDayId ->
                thread(start = false) {
                    start.await()
                    try {
                        AllowanceService.create(
                            callerId,
                            allowanceId,
                            requestedDayId,
                            targetUserId,
                            BigDecimal("500.00"),
                            reason = if (requestedDayId == otherDayId) "Coordinator correction" else null,
                        )
                        results += null
                    } catch (error: Throwable) {
                        results += error
                    }
                }
            }

        threads.forEach { it.start() }
        start.countDown()
        threads.forEach {
            it.join(30_000)
            if (it.isAlive) {
                it.interrupt()
                it.join(1_000)
            }
            assertFalse(it.isAlive, "allowance test worker did not terminate")
        }

        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId)
        assertEquals(2, results.size)
        assertEquals(1, results.count { it == null })
        assertEquals(1, results.count { it is NotFoundException })
        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq AllowanceTable.tableName) and
                            (AuditLogTable.recordId eq allowanceId)
                    }.count()
            }
        assertEquals(1, auditCount)
    }

    @Test
    fun `create without ASSIGN_COMPENSATION is allowed at service layer`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        val allowanceId = TestFixtures.uuid()
        val allowance =
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )

        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId)
        assertNotNull(allowance)
        assertEquals(targetUserId, allowance.userId)
    }

    @Test
    fun `create with non-existent branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            AllowanceService.create(
                callerId = callerId,
                id = TestFixtures.uuid(),
                branchDayId = TestFixtures.uuid(),
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )
        }
    }

    @Test
    fun `findByBranchDayId returns allowances for branch day`() {
        val allowanceId1 = TestFixtures.uuid()
        val allowanceId2 = TestFixtures.uuid()

        AllowanceService.create(
            callerId = callerId,
            id = allowanceId1,
            branchDayId = branchDayId,
            userId = targetUserId,
            amount = BigDecimal("500.00"),
        )
        AllowanceService.create(
            callerId = callerId,
            id = allowanceId2,
            branchDayId = branchDayId,
            userId = targetUserId,
            amount = BigDecimal("300.00"),
        )

        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId1)
        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId2)

        val results = AllowanceService.findByBranchDayId(branchDayId)

        assertEquals(2, results.size)
        assertTrue(results.any { it.id == allowanceId1 })
        assertTrue(results.any { it.id == allowanceId2 })
    }

    @Test
    fun `findByBranchDayId returns empty list for non-existent branch day`() {
        val results = AllowanceService.findByBranchDayId(TestFixtures.uuid())
        assertEquals(0, results.size)
    }

    @Test
    fun `findByBranchDayId without capability is allowed at service layer`() {
        DatabaseTestHelper.revokeAllCapabilities(callerId)

        val results = AllowanceService.findByBranchDayId(branchDayId)

        assertTrue(results.isEmpty())
    }

    @Test
    fun `create writes audit log entry`() {
        val allowanceId = TestFixtures.uuid()

        AllowanceService.create(
            callerId = callerId,
            id = allowanceId,
            branchDayId = branchDayId,
            userId = targetUserId,
            amount = BigDecimal("500.00"),
        )

        trackOwned(AllowanceTable, AllowanceTable.id, allowanceId)

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq AllowanceTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
    }
}
