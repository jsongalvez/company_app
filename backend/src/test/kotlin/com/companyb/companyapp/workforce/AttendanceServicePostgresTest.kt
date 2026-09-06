package com.companyb.companyapp.workforce
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.commission.CommissionService
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.AttendanceRepository
import com.companyb.companyapp.workforce.AttendanceService
import com.companyb.companyapp.workforce.AttendanceServiceResult
import com.companyb.companyapp.workforce.AttendanceTable
import com.companyb.companyapp.workforce.BranchDayAssignmentTable
import com.companyb.companyapp.workforce.ClockInParams
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class AttendanceServicePostgresTest : BasePostgresTest() {
    private val userId = TestFixtures.uuid()
    private val otherUserId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(userId, "user")
        IdentityFixtures.insertTestUser(otherUserId, "other-user")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Branch")
        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Branch")
    }

    @Test
    fun `clockIn creates attendance and branch day assignment and writes audit`() {
        val attendanceId = TestFixtures.uuid()

        val (result, duration) =
            measureTimedValue {
                AttendanceService.clockIn(attendanceId, branchId, userId)
            }
        assertTrue(duration < 5.seconds, "clockIn regressed: took $duration")

        assertTrue(result.created)
        assertEquals(attendanceId, result.id)
        assertEquals(userId, result.userId)
        assertNotNull(result.clockIn)
        assertNull(result.clockOut)
        assertEquals(1L, auditEntryCount(attendanceId))
        assertNotNull(branchDayAssignmentExists(attendanceId))
    }

    @Test
    fun `clockIn trigger rolls back attendance assignment and audit when transaction fails`() {
        val attendanceId = TestFixtures.uuid()

        assertFailsWith<IllegalStateException> {
            transaction {
                AttendanceService.clockIn(attendanceId, branchId, userId)
                error("injected commission trigger failure")
            }
        }

        assertEquals(
            0,
            transaction { AttendanceTable.selectAll().where { AttendanceTable.id eq attendanceId }.count() },
        )
        assertEquals(0, auditEntryCount(attendanceId))
        assertEquals(
            0,
            transaction {
                BranchDayAssignmentTable
                    .selectAll()
                    .where { BranchDayAssignmentTable.userId eq userId }
                    .count()
            },
        )
    }

    @Test
    fun `clockIn with same id returns existing attendance`() {
        val attendanceId = TestFixtures.uuid()
        val first = AttendanceService.clockIn(attendanceId, branchId, userId)

        val second = AttendanceService.clockIn(attendanceId, branchId, userId)

        assertEquals(first.id, second.id)
        assertTrue(second.created.not())
    }

    @Test
    fun `clockIn rejects same id from another caller without duplicate side effects`() {
        val attendanceId = TestFixtures.uuid()
        AttendanceService.clockIn(attendanceId, branchId, userId)

        assertFailsWith<ConflictException> {
            AttendanceService.clockIn(attendanceId, branchId, otherUserId)
        }

        assertEquals(1L, auditEntryCount(attendanceId))
        assertEquals(1L, branchDayAssignmentCount(userId))
    }

    @Test
    fun `clockIn rejects same id for another branch without duplicate side effects`() {
        val attendanceId = TestFixtures.uuid()
        AttendanceService.clockIn(attendanceId, branchId, userId)

        assertFailsWith<ConflictException> {
            AttendanceService.clockIn(attendanceId, otherBranchId, userId)
        }

        assertEquals(1L, auditEntryCount(attendanceId))
        assertEquals(1L, branchDayAssignmentCount(userId))
    }

    @Test
    fun `clockIn throws Conflict when user already has active clock-in`() {
        val firstId = TestFixtures.uuid()
        AttendanceService.clockIn(firstId, branchId, userId)

        val secondId = TestFixtures.uuid()
        assertFailsWith<ConflictException> {
            AttendanceService.clockIn(secondId, branchId, userId)
        }
    }

    @Test
    fun `clockIn sets isRelief true when no branch assignment exists`() {
        val attendanceId = TestFixtures.uuid()

        val result = AttendanceService.clockIn(attendanceId, branchId, userId)

        assertTrue(result.isRelief)
    }

    @Test
    fun `clockIn sets isRelief false when branch assignment exists`() {
        IdentityFixtures.grantManageUsers(userId, sourceId)
        val assignmentId = TestFixtures.uuid()
        UserBranchAssignmentService.create(userId, assignmentId, branchId, userId, 1)

        val attendanceId = TestFixtures.uuid()
        val result = AttendanceService.clockIn(attendanceId, branchId, userId)

        assertTrue(result.isRelief.not())
    }

    @Test
    fun `clockIn resolves the operational day through the central Branch Day authority`() {
        val expectedDate = BranchDayService.currentOperationalDate()
        val attendanceId = TestFixtures.uuid()

        val result = AttendanceService.clockIn(attendanceId, branchId, userId)

        val expectedBranchDay = BranchDayService.findByBranchAndDate(branchId, expectedDate)
        assertNotNull(expectedBranchDay)
        assertEquals(expectedBranchDay.id, result.branchDayId)
        assertEquals(TestFixtures.today, expectedDate)
    }

    @Test
    fun `clockOut sets clockOut and writes audit`() {
        val attendanceId = TestFixtures.uuid()
        AttendanceService.clockIn(attendanceId, branchId, userId)

        val result = AttendanceService.clockOut(attendanceId, userId)

        assertTrue(result.created.not())
        assertNotNull(result.clockOut)
        assertEquals(2L, auditEntryCount(attendanceId))
        assertNotNull(auditNewClockOut(attendanceId))
    }

    @Test
    fun `clockOut on already clocked out record returns existing`() {
        val attendanceId = TestFixtures.uuid()
        AttendanceService.clockIn(attendanceId, branchId, userId)
        val first = AttendanceService.clockOut(attendanceId, userId)

        val second = AttendanceService.clockOut(attendanceId, userId)

        assertTrue(first.created.not())
        assertTrue(second.created.not())
        assertEquals(first.clockOut, second.clockOut)
        assertEquals(2L, auditEntryCount(attendanceId))
    }

    // ===== #321 contract proofs — command-owned attendance mutations (ADR-0024) =====

    @Test
    fun `audit failure inside the clock-in composition rolls attendance assignment and audit back`() {
        val attendanceId = TestFixtures.uuid()

        // Exercises the exact composition the migrated clock-in command runs — day resolution
        // through the Branch Day boundary, the store writes on the command transaction, then
        // the audit insert into that same transaction. A failing audit statement must abort all.
        val error =
            runCatching {
                transaction {
                    val branchDay =
                        BranchDayService.resolveOrCreate(branchId, BranchDayService.currentOperationalDate())
                    AttendanceRepository.clockInInTransaction(
                        ClockInParams(
                            attendanceId = attendanceId,
                            branchDayId = branchDay.id,
                            userId = userId,
                            markedBy = userId,
                            branchDayAssignmentId = TestFixtures.uuid(),
                            isRelief = true,
                            branchId = branchId,
                        ),
                    )
                    AuditLog.record(
                        tableName = AttendanceTable.tableName,
                        recordId = attendanceId,
                        action = AuditAction.INSERT,
                        changedBy = userId,
                        oldValue = "{not-valid-json",
                    )
                }
            }.exceptionOrNull()

        assertNotNull(error, "malformed jsonb audit payload must fail the statement")

        assertEquals(0L, attendanceCount(attendanceId), "mutation rolled back with the failed audit")
        assertEquals(0L, auditEntryCount(attendanceId), "no partial audit row survived")
        assertEquals(0L, branchDayAssignmentCount(userId), "assignment rolled back with the failed audit")
    }

    @Test
    fun `audit failure inside the clock-out composition rolls the clock-out back`() {
        val attendanceId = TestFixtures.uuid()
        AttendanceService.clockIn(attendanceId, branchId, userId)

        val error =
            runCatching {
                transaction {
                    checkNotNull(AttendanceRepository.findByIdInTransaction(attendanceId))
                    AttendanceRepository.clockOutInTransaction(attendanceId)
                    AuditLog.record(
                        tableName = AttendanceTable.tableName,
                        recordId = attendanceId,
                        action = AuditAction.UPDATE,
                        changedBy = userId,
                        oldValue = "{not-valid-json",
                    )
                }
            }.exceptionOrNull()

        assertNotNull(error, "malformed jsonb audit payload must fail the statement")

        assertEquals(1L, auditEntryCount(attendanceId), "only the clock-in audit survives")
        assertNull(
            transaction {
                AttendanceTable
                    .selectAll()
                    .where { AttendanceTable.id eq attendanceId }
                    .single()[AttendanceTable.clockOut]
            },
            "clock-out rolled back with the failed audit",
        )
    }

    @Test
    fun `commission failure inside the clock-in command rolls the attendance back`() {
        val attendanceId = TestFixtures.uuid()
        CommissionService.failAfterReplacementForTests = true
        try {
            assertFailsWith<IllegalStateException> {
                AttendanceService.clockIn(attendanceId, branchId, userId)
            }
        } finally {
            CommissionService.failAfterReplacementForTests = false
        }

        assertEquals(
            0L,
            attendanceCount(attendanceId),
            "attendance rolled back with the failed commission recalculation",
        )
        assertEquals(0L, auditEntryCount(attendanceId), "no audit row survived the failed recalculation")
        assertEquals(0L, branchDayAssignmentCount(userId), "assignment rolled back with the failed recalculation")
    }

    @Test
    fun `clockIn resolves today through the authority even when a stale earlier day exists`() {
        val yesterday = TestFixtures.today.minusDays(1)
        BranchDayService.resolveOrCreate(branchId, yesterday)
        val attendanceId = TestFixtures.uuid()

        val result = AttendanceService.clockIn(attendanceId, branchId, userId)

        val expectedBranchDay =
            BranchDayService.findByBranchAndDate(branchId, BranchDayService.currentOperationalDate())
        assertNotNull(expectedBranchDay)
        assertEquals(expectedBranchDay.id, result.branchDayId, "stale day rows must not capture clock-in")
    }

    @Test
    fun `concurrent clock-in retries with the same id create exactly once`() {
        val attendanceId = TestFixtures.uuid()
        val threads = 2
        val executor = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val futures =
            (1..threads).map {
                executor.submit<Result<AttendanceServiceResult>> {
                    ready.countDown()
                    start.await()
                    runCatching { AttendanceService.clockIn(attendanceId, branchId, userId) }
                }
            }

        try {
            assertTrue(ready.await(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            val outcomes = futures.map { it.get() }
            assertTrue(
                outcomes.all { it.isSuccess },
                "every same-owner retry succeeds idempotently, even when serialized behind the winner",
            )
            assertEquals(
                1,
                outcomes.count { it.getOrNull()?.created == true },
                "exactly one retry creates, whichever interleaving wins",
            )
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
        }

        assertEquals(1L, attendanceCount(attendanceId), "exactly one attendance row for the retries")
        assertEquals(1L, auditEntryCount(attendanceId), "exactly one audit row for the retries")
        assertEquals(1L, branchDayAssignmentCount(userId), "exactly one day assignment for the retries")
    }

    @Test
    fun `clockOut rejects another user's attendance`() {
        val attendanceId = TestFixtures.uuid()
        AttendanceService.clockIn(attendanceId, branchId, userId)

        assertFailsWith<ForbiddenException> {
            AttendanceService.clockOut(attendanceId, otherUserId)
        }

        assertNull(
            transaction {
                AttendanceTable
                    .selectAll()
                    .where { AttendanceTable.id eq attendanceId }
                    .single()[AttendanceTable.clockOut]
            },
        )
        assertEquals(1L, auditEntryCount(attendanceId))
    }

    @Test
    fun `clockOut on non-existent attendance throws NotFound`() {
        val unknownId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            AttendanceService.clockOut(unknownId, userId)
        }
    }

    private fun branchDayAssignmentExists(attendanceId: UUID): UUID? =
        transaction {
            val branchDayId =
                AttendanceTable
                    .selectAll()
                    .where { AttendanceTable.id eq attendanceId }
                    .single()[AttendanceTable.branchDayId]

            BranchDayAssignmentTable
                .selectAll()
                .where {
                    (BranchDayAssignmentTable.branchDayId eq branchDayId) and
                        (BranchDayAssignmentTable.userId eq userId)
                }.singleOrNull()
                ?.let { it[BranchDayAssignmentTable.id] }
        }

    private fun auditEntryCount(attendanceId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq "attendance") and
                        (AuditLogTable.recordId eq attendanceId)
                }.count()
        }

    private fun auditNewClockOut(attendanceId: UUID): String =
        transaction {
            val row =
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq "attendance") and
                            (AuditLogTable.recordId eq attendanceId)
                    }.orderBy(AuditLogTable.changedAt to SortOrder.DESC)
                    .limit(1)
                    .single()
            TestFixtures.extractJsonField(row[AuditLogTable.newValue] ?: "{}", "clockOut")
        }

    private fun branchDayAssignmentCount(userId: UUID): Long =
        transaction {
            BranchDayAssignmentTable
                .selectAll()
                .where { BranchDayAssignmentTable.userId eq userId }
                .count()
        }

    private fun attendanceCount(attendanceId: UUID): Long =
        transaction {
            AttendanceTable
                .selectAll()
                .where { AttendanceTable.id eq attendanceId }
                .count()
        }

    private companion object {
        const val EXECUTOR_TERMINATION_SECONDS = 30L
    }
}
