package com.companyb.companyapp.finance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompensationServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val targetUserId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    private lateinit var workBranchDayId: UUID
    private lateinit var payingBranchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "comp-caller")
        IdentityFixtures.insertTestUser(targetUserId, "comp-target")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Compensation Branch")
        workBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        payingBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        IdentityFixtures.grantAssignCompensation(callerId, sourceId)
    }

    @Test
    fun `create compensation on REMITTED paying day with reason succeeds and flags audit entry`() {
        val remittedDayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val compId = TestFixtures.uuid()

        val comp =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = remittedDayId,
                payingBranchDayId = remittedDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = "Daily compensation",
                reason = "Coordinator correction",
            )

        assertNotNull(comp)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq CompensationTable.tableName) and
                            (AuditLogTable.recordId eq compId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `create compensation succeeds with all fields`() {
        val compId = TestFixtures.uuid()

        val comp =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = "Daily compensation",
            )

        assertNotNull(comp)
        assertEquals(compId, comp.id)
        assertEquals(workBranchDayId, comp.workBranchDayId)
        assertEquals(payingBranchDayId, comp.payingBranchDayId)
        assertEquals(targetUserId, comp.userId)
        assertEquals(0, BigDecimal("1500.00").compareTo(comp.amount))
        assertEquals(callerId, comp.assignedBy)
        assertEquals("Daily compensation", comp.note)
    }

    @Test
    fun `create compensation without note succeeds`() {
        val compId = TestFixtures.uuid()

        val comp =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1000.00"),
                note = null,
            )

        assertNotNull(comp)
        assertNull(comp.note)
    }

    @Test
    fun `create idempotent duplicate returns existing`() {
        val compId = TestFixtures.uuid()

        val first =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = "First",
            )

        val second =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = "First",
            )

        assertEquals(first.id, second.id)
    }

    @Test
    fun `create rejects duplicate user and paying day`() {
        val firstId = TestFixtures.uuid()
        CompensationService.create(
            callerId = callerId,
            id = firstId,
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )

        val secondId = TestFixtures.uuid()
        assertFailsWith<ConflictException> {
            CompensationService.create(
                callerId = callerId,
                id = secondId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("2000.00"),
                note = null,
            )
        }
    }

    @Test
    fun `concurrent creates with different ids return one conflict`() {
        val start = CountDownLatch(1)
        val results = Collections.synchronizedList(mutableListOf<Throwable?>())
        val threads =
            listOf(
                TestFixtures.uuid(),
                TestFixtures.uuid(),
            ).map { id ->
                thread(start = false) {
                    start.await()
                    try {
                        CompensationService.create(
                            callerId = callerId,
                            id = id,
                            workBranchDayId = workBranchDayId,
                            payingBranchDayId = payingBranchDayId,
                            userId = targetUserId,
                            amount = BigDecimal("1500.00"),
                            note = null,
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
            if (it.isAlive) it.interrupt()
        }

        assertEquals(2, results.size)
        assertEquals(1, results.count { it == null })
        assertEquals(1, results.count { it is ConflictException })
    }

    @Test
    fun `create without ASSIGN_COMPENSATION is allowed at service layer`() {
        IdentityFixtures.revokeAllCapabilities(callerId)

        val compId = TestFixtures.uuid()
        val comp =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )

        assertNotNull(comp)
    }

    @Test
    fun `create with non-existent work branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            CompensationService.create(
                callerId = callerId,
                id = TestFixtures.uuid(),
                workBranchDayId = TestFixtures.uuid(),
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
    }

    @Test
    fun `create with non-existent paying branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            CompensationService.create(
                callerId = callerId,
                id = TestFixtures.uuid(),
                workBranchDayId = workBranchDayId,
                payingBranchDayId = TestFixtures.uuid(),
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
    }

    @Test
    fun `create compensation throws 404 for unknown user with no row written`() {
        val unknownUserId = TestFixtures.uuid()
        val blockedId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            CompensationService.create(
                callerId = callerId,
                id = blockedId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = unknownUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
        val stored = transaction { CompensationRepository.findByIdInTransaction(blockedId) }
        assertNull(stored)
        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq CompensationTable.tableName) and
                            (AuditLogTable.recordId eq blockedId)
                    }.count()
            }
        assertEquals(0, auditCount)
    }

    @Test
    fun `update compensation succeeds`() {
        val compId = TestFixtures.uuid()
        val created =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = "Original note",
            )

        val updated =
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("2000.00"),
                note = "Updated note",
                expectedVersion = created.version,
            )

        assertEquals(0, BigDecimal("2000.00").compareTo(updated.amount))
        assertEquals("Updated note", updated.note)
        assertEquals(created.version + 1, updated.version)
    }

    @Test
    fun `update with wrong version returns conflict`() {
        val compId = TestFixtures.uuid()
        CompensationService.create(
            callerId = callerId,
            id = compId,
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )

        assertFailsWith<ConflictException> {
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("2000.00"),
                note = null,
                expectedVersion = 999,
            )
        }
    }

    @Test
    fun `create with paying day at another branch returns validation error`() {
        val otherBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Cross Comp Branch")
        val otherBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)

        assertFailsWith<ValidationException> {
            CompensationService.create(
                callerId = callerId,
                id = TestFixtures.uuid(),
                workBranchDayId = workBranchDayId,
                payingBranchDayId = otherBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
    }

    @Test
    fun `create with work day at another branch returns validation error`() {
        val otherBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Cross Work Comp Branch")
        val otherWorkDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)

        assertFailsWith<ValidationException> {
            CompensationService.create(
                callerId = callerId,
                id = TestFixtures.uuid(),
                workBranchDayId = otherWorkDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
    }

    @Test
    fun `update rejects stored record whose days span branches`() {
        val otherBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Legacy Comp Branch")
        val legacyPayingDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)

        val compId = TestFixtures.uuid()
        transaction {
            CompensationTable.insert {
                it[CompensationTable.id] = compId
                it[CompensationTable.workBranchDayId] = this@CompensationServicePostgresTest.workBranchDayId
                it[CompensationTable.payingBranchDayId] = legacyPayingDayId
                it[userId] = targetUserId
                it[amount] = BigDecimal("500.00")
                it[assignedBy] = callerId
            }
        }

        assertFailsWith<ValidationException> {
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("900.00"),
                note = null,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `update non-existent compensation returns not found`() {
        assertFailsWith<NotFoundException> {
            CompensationService.update(
                callerId = callerId,
                compensationId = TestFixtures.uuid(),
                amount = BigDecimal("2000.00"),
                note = null,
                expectedVersion = 1,
            )
        }
    }

    @Test
    fun `update without ASSIGN_COMPENSATION is allowed at service layer`() {
        val compId = TestFixtures.uuid()
        CompensationService.create(
            callerId = callerId,
            id = compId,
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )

        IdentityFixtures.revokeAllCapabilities(callerId)

        val updated =
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("2000.00"),
                note = null,
                expectedVersion = 1,
            )

        assertEquals(0, BigDecimal("2000.00").compareTo(updated.amount))
    }

    @Test
    fun `list compensations for paying branch day returns rows with user names`() {
        val secondUser = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(secondUser, "comp-second")

        CompensationService.create(
            callerId = callerId,
            id = TestFixtures.uuid(),
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )
        CompensationService.create(
            callerId = callerId,
            id = TestFixtures.uuid(),
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = secondUser,
            amount = BigDecimal("1200.00"),
            note = null,
        )

        val rows = CompensationService.findByPayingBranchDayId(payingBranchDayId)

        assertEquals(2, rows.size)
        assertTrue(rows.any { it.compensation.userId == targetUserId && it.userName == "Test comp-target" })
        assertTrue(rows.any { it.compensation.userId == secondUser && it.userName == "Test comp-second" })
    }

    @Test
    fun `list compensations excludes other paying days`() {
        val otherBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Comp Branch")
        val otherBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(otherBranchId)

        CompensationService.create(
            callerId = callerId,
            id = TestFixtures.uuid(),
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )
        CompensationService.create(
            callerId = callerId,
            id = TestFixtures.uuid(),
            workBranchDayId = otherBranchDayId,
            payingBranchDayId = otherBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("800.00"),
            note = null,
        )

        val rows = CompensationService.findByPayingBranchDayId(payingBranchDayId)

        assertEquals(1, rows.size)
        assertEquals(0, BigDecimal("1500.00").compareTo(rows[0].compensation.amount))
    }

    @Test
    fun `list compensations returns empty for day with no rows`() {
        val emptyBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(emptyBranchId, "Empty Comp Branch")
        val emptyBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(emptyBranchId)

        val rows = CompensationService.findByPayingBranchDayId(emptyBranchDayId)

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `list compensations for non-existent branch day returns not found`() {
        assertFailsWith<NotFoundException> {
            CompensationService.findByPayingBranchDayId(TestFixtures.uuid())
        }
    }

    @Test
    fun `create writes audit log entry`() {
        val compId = TestFixtures.uuid()

        CompensationService.create(
            callerId = callerId,
            id = compId,
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq CompensationTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
    }

    @Test
    fun `update writes audit log entry`() {
        val compId = TestFixtures.uuid()
        val created =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )

        CompensationService.update(
            callerId = callerId,
            compensationId = compId,
            amount = BigDecimal("2000.00"),
            note = "Updated",
            expectedVersion = created.version,
        )

        val updateAuditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq CompensationTable.tableName) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.count()
            }
        assertTrue(updateAuditCount > 0)
    }
}
