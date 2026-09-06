package com.companyb.companyapp.service

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.repository.model.AttendanceTable
import com.companyb.companyapp.service.attendance.AttendanceRepository
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * #519 — reciprocal attendance marks share one participant lock order (stable user-ID
 * order), so opposite-direction marks on the same pair cannot cross-deadlock.
 *
 * A holder keeps the first-ordered membership lock while the actual
 * [AttendanceService.mark] path runs on a second connection with the caller set to the
 * second-ordered participant, forcing the interleaving: pre-fix mark already holds the
 * second participant's rows while waiting on the first, so the two sides cross-deadlock
 * and the detector aborts one. Post-fix mark waits on the first participant's rows
 * holding nothing, so the holder commits and mark proceeds. The verdict is
 * lock-determined, not timing-determined: the readiness margin only lets mark reach its
 * blocked state, with an alive-check proving it is blocked rather than finished.
 *
 * Exposed retries top-level transactions ([TransactionManager.defaultMaxAttempts] is
 * 3), which silently heals a deadlock victim and would mask the cycle — so the gate
 * probes run with a single attempt and restore the default afterwards. Both present
 * and absent legs enter the same gate, and each probe asserts its leg's composed
 * effects afterwards. A final serial test covers the self-mark single lock and proves
 * a concurrent assignment removal serializes against the gate instead of slipping
 * behind the authority check.
 */
class AttendanceMarkLockOrderPostgresTest : BasePostgresTest() {
    private val userA = TestFixtures.uuid()
    private val userB = TestFixtures.uuid()
    private val managerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private lateinit var assignmentA: UUID
    private lateinit var assignmentB: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(userA, "mark-lock-a")
        IdentityFixtures.insertTestUser(userB, "mark-lock-b")
        IdentityFixtures.insertTestUser(managerId, "mark-lock-manager")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Mark Lock Branch")
        assignmentA =
            BranchWorkforceFixtures.insertTestAssignment(
                userId = userA,
                branchId = branchId,
                slot = 1,
                assignedBy = managerId,
            )
        assignmentB =
            BranchWorkforceFixtures.insertTestAssignment(
                userId = userB,
                branchId = branchId,
                slot = 2,
                assignedBy = managerId,
            )
        IdentityFixtures.grantManageUsers(managerId, managerId)
    }

    @Test
    fun `present mark against held first-ordered lock waits without deadlock`() {
        val (lower, upper) = orderedPair()
        val attendanceId = TestFixtures.uuid()

        val (holderFailure, markFailure) =
            runGateProbe(lower, upper) {
                AttendanceService.mark(
                    callerId = upper,
                    branchId = branchId,
                    targetUserId = lower,
                    present = true,
                    attendanceId = attendanceId,
                )
            }
        assertNoCycle(holderFailure, markFailure)

        val row = attendanceRow(attendanceId)
        assertEquals(lower, row.userId, "present mark writes the target's window")
        assertEquals(upper, row.markedBy, "present mark attributes the caller")
        assertEquals(listOf(upper), insertChangedBy(attendanceId), "audit attributes the marker")
    }

    @Test
    fun `absent mark against held first-ordered lock waits without deadlock`() {
        val (lower, upper) = orderedPair()
        AttendanceService.clockIn(TestFixtures.uuid(), branchId, lower)

        val (holderFailure, markFailure) =
            runGateProbe(lower, upper) {
                AttendanceService.mark(
                    callerId = upper,
                    branchId = branchId,
                    targetUserId = lower,
                    present = false,
                    attendanceId = null,
                )
            }
        assertNoCycle(holderFailure, markFailure)

        val row = openOrClosedRow(lower)
        assertNotNull(row.clockOut, "absent mark closed the target window")
        assertEquals(listOf(upper), updateChangedBy(row.id), "absent audit attributes the marker")
    }

    @Test
    fun `self mark locks once and removal behind the gate is observed`() {
        val (lower, upper) = orderedPair()

        val self =
            AttendanceService.mark(
                callerId = lower,
                branchId = branchId,
                targetUserId = lower,
                present = true,
                attendanceId = TestFixtures.uuid(),
            )
        assertTrue(self.created, "self-mark succeeds on the deduped single lock")

        runRevocationProbe(lower)

        assertFailsWith<ForbiddenException> {
            AttendanceService.mark(
                callerId = lower,
                branchId = branchId,
                targetUserId = upper,
                present = true,
                attendanceId = TestFixtures.uuid(),
            )
        }
    }

    private fun orderedPair(): Pair<UUID, UUID> {
        val (lower, upper) = listOf(userA, userB).sorted()
        return lower to upper
    }

    private fun assignmentOf(userId: UUID): UUID = if (userId == userA) assignmentA else assignmentB

    private fun runGateProbe(
        lower: UUID,
        upper: UUID,
        markAttempt: () -> Unit,
    ): Pair<Throwable?, Throwable?> {
        val transactionManager = TransactionManager.manager
        val savedAttempts = transactionManager.defaultMaxAttempts
        transactionManager.defaultMaxAttempts = SINGLE_ATTEMPT
        val lowerHeld = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(1)
        try {
            val holderFuture =
                executor.submit<String> {
                    transaction {
                        AttendanceRepository.hasActiveMembershipInTransaction(branchId, lower)
                        lowerHeld.countDown()
                        release.await()
                        AttendanceRepository.hasActiveMembershipInTransaction(branchId, upper)
                        "acquired"
                    }
                }
            assertTrue(lowerHeld.await(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))

            var markFailure: Throwable? = null
            val marker =
                thread {
                    markFailure =
                        runCatching { markAttempt() }.exceptionOrNull()
                }
            Thread.sleep(READINESS_MILLIS)
            if (!marker.isAlive) {
                fail("mark finished while the first-ordered lock was held (failure=$markFailure)")
            }
            release.countDown()
            val holderFailure =
                runCatching { holderFuture.get(HOLDER_TIMEOUT_SECONDS, TimeUnit.SECONDS) }.exceptionOrNull()
            marker.join(JOIN_MILLIS)
            assertTrue(!marker.isAlive, "mark still alive after the membership lock was released")
            return holderFailure to markFailure
        } finally {
            transactionManager.defaultMaxAttempts = savedAttempts
            release.countDown()
            executor.shutdownNow()
            executor.awaitTermination(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private fun assertNoCycle(
        holderFailure: Throwable?,
        markFailure: Throwable?,
    ) {
        val cycleEvidence = listOfNotNull(holderFailure, markFailure).firstOrNull(::isDeadlock)
        if (cycleEvidence != null) {
            fail(
                "reciprocal marks hold opposite participant locks (stable user-ID order violated): $cycleEvidence",
            )
        }
        if (holderFailure != null) fail("membership holder failed unexpectedly: $holderFailure")
        if (markFailure != null) fail("mark failed unexpectedly: $markFailure")
    }

    private fun runRevocationProbe(lower: UUID) {
        val gateHeld = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(1)
        try {
            val holderFuture =
                executor.submit<String> {
                    transaction {
                        AttendanceRepository.hasActiveMembershipInTransaction(branchId, lower)
                        gateHeld.countDown()
                        release.await()
                        "acquired"
                    }
                }
            assertTrue(gateHeld.await(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))

            var removeFailure: Throwable? = null
            val remover =
                thread {
                    removeFailure =
                        runCatching {
                            UserBranchAssignmentService.remove(managerId, branchId, assignmentOf(lower))
                        }.exceptionOrNull()
                }
            Thread.sleep(READINESS_MILLIS)
            if (!remover.isAlive) {
                fail("removal finished while the gate held the membership lock (failure=$removeFailure)")
            }
            release.countDown()
            val holderFailure =
                runCatching { holderFuture.get(HOLDER_TIMEOUT_SECONDS, TimeUnit.SECONDS) }.exceptionOrNull()
            remover.join(JOIN_MILLIS)
            assertTrue(!remover.isAlive, "removal still alive after the gate released")
            if (holderFailure != null) fail("gate holder failed unexpectedly: $holderFailure")
            if (removeFailure != null) fail("removal failed unexpectedly: $removeFailure")
        } finally {
            release.countDown()
            executor.shutdownNow()
            executor.awaitTermination(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
    }

    private fun isDeadlock(failure: Throwable): Boolean =
        generateSequence<Throwable>(failure) { it.cause }.any { cause ->
            cause.message?.contains("deadlock", ignoreCase = true) == true ||
                cause.message?.contains("40P01") == true
        }

    private data class AttendanceRow(
        val id: UUID,
        val userId: UUID,
        val markedBy: UUID,
        val clockOut: String?,
    )

    private fun attendanceRow(attendanceId: UUID): AttendanceRow =
        transaction {
            AttendanceTable
                .selectAll()
                .where { AttendanceTable.id eq attendanceId }
                .single()
                .let { row ->
                    AttendanceRow(
                        id = row[AttendanceTable.id],
                        userId = row[AttendanceTable.userId],
                        markedBy = row[AttendanceTable.markedBy],
                        clockOut = row[AttendanceTable.clockOut]?.toString(),
                    )
                }
        }

    private fun openOrClosedRow(userId: UUID): AttendanceRow =
        transaction {
            AttendanceTable
                .selectAll()
                .where { AttendanceTable.userId eq userId }
                .single()
                .let { row ->
                    AttendanceRow(
                        id = row[AttendanceTable.id],
                        userId = row[AttendanceTable.userId],
                        markedBy = row[AttendanceTable.markedBy],
                        clockOut = row[AttendanceTable.clockOut]?.toString(),
                    )
                }
        }

    private fun insertChangedBy(attendanceId: UUID): List<UUID> = auditChangedBy(attendanceId, AuditAction.INSERT)

    private fun updateChangedBy(attendanceId: UUID): List<UUID> = auditChangedBy(attendanceId, AuditAction.UPDATE)

    private fun auditChangedBy(
        attendanceId: UUID,
        action: AuditAction,
    ): List<UUID> =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq ATTENDANCE_TABLE) and
                        (AuditLogTable.recordId eq attendanceId) and
                        (AuditLogTable.action eq action)
                }.map { it[AuditLogTable.changedBy] }
        }

    companion object {
        private const val ATTENDANCE_TABLE = "attendance"
        private const val SINGLE_ATTEMPT = 1
        private const val READINESS_MILLIS = 3000L
        private const val JOIN_MILLIS = 30000L
        private const val EXECUTOR_TIMEOUT_SECONDS = 30L
        private const val HOLDER_TIMEOUT_SECONDS = 60L
    }
}
