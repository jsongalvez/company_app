package com.companyb.companyapp.service

import com.companyb.companyapp.branchday.BranchDayRepository
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.ReliefAccessRepository
import com.companyb.companyapp.repository.ReliefInviteRepository
import com.companyb.companyapp.service.attendance.AttendanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTimedValue

/**
 * #376 — the cutoff protocol regression harness. The retraction commands (relief-request
 * cancel #357, invite revoke #374) and clock-in serialize on the branch_day row lock, so a
 * retraction can never decide while a concurrent clock-in is deciding — the READ COMMITTED
 * interleaving that let a duty be cancelled after it started is closed. Each test holds the
 * branch-day row from a separate connection and proves the competing command BLOCKS until
 * the lock is released: on the pre-fix implementation these commands completed immediately,
 * so every assertion here fails without the protocol.
 */
class ShiftGuardCutoffConcurrencyPostgresTest : BasePostgresTest() {
    private val reliefUserId = TestFixtures.uuid()
    private val memberId = TestFixtures.uuid()
    private val inviteeId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(reliefUserId, "cutoff-relief")
        DatabaseTestHelper.insertTestUser(memberId, "cutoff-member")
        DatabaseTestHelper.insertTestUser(inviteeId, "cutoff-invitee")
        DatabaseTestHelper.insertTestBranch(branchId, "Cutoff Branch ${branchId.toString().take(8)}")
        // Day rows are resolved-or-created by the commands under test.
        // The member's home assignment — cancel/revoke authority (#357/#374).
        DatabaseTestHelper.insertTestAssignment(
            userId = memberId,
            branchId = branchId,
            slot = 1,
            assignedBy = memberId,
        )
        // Clock-in upserts a branch_day_assignment row — untracked it blocks user teardown.
    }

    @Test
    fun `cancel waits while another writer holds the branch-day row`() {
        val requestId = TestFixtures.uuid()
        val branchDayId =
            ReliefAccessService
                .requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)
                .branchDayId

        val blocked = holdBranchDayRowAndProbe(branchDayId) { ReliefAccessService.cancelRequest(requestId, memberId) }

        assertFalse(blocked, "cancel decided while the branch-day row was locked by another writer")
        assertEquals(
            ReliefAccessStatus.CANCELLED,
            ReliefAccessRepository.findById(requestId)?.requestStatus,
            "cancel commits after the holder releases",
        )
    }

    @Test
    fun `revocation waits while another writer holds the branch-day row`() {
        val invite =
            ReliefInviteService.createInvite(memberId, branchId, inviteeId, TestFixtures.today.plusDays(2))
        ReliefInviteService.acceptInvite(inviteeId, invite.id)

        val blocked =
            holdBranchDayRowAndProbe(invite.branchDayId) { ReliefInviteService.revokeInvite(memberId, invite.id) }

        assertFalse(blocked, "revocation decided while the branch-day row was locked by another writer")
        assertEquals(ReliefInviteStatus.REVOKED, ReliefInviteRepository.findById(invite.id)?.status)
    }

    @Test
    fun `clock-in waits for an in-flight retraction decision on the same branch day`() {
        val branchDayId =
            ReliefAccessService
                .requestReliefAccess(TestFixtures.uuid(), branchId, TestFixtures.today, reliefUserId)
                .branchDayId

        val blocked =
            holdBranchDayRowAndProbe(branchDayId) {
                AttendanceService.clockIn(TestFixtures.uuid(), branchId, reliefUserId)
            }

        assertFalse(blocked, "clock-in ran while a retraction decision held the branch-day row")
    }

    @Test
    fun `member cancel loses to a committed clock-in by the requester`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, reliefUserId)

        AttendanceService.clockIn(TestFixtures.uuid(), branchId, reliefUserId)

        try {
            ReliefAccessService.cancelRequest(requestId, memberId)
            fail("post-clock-in member cancel must 400")
        } catch (expected: ValidationException) {
            assertEquals(
                "Relief duty has already started — this request can no longer be cancelled",
                expected.message,
            )
        }
        assertEquals(ReliefAccessStatus.PENDING, ReliefAccessRepository.findById(requestId)?.requestStatus)
    }

    @Test
    fun `uncontended cutoff protocol stays fast on the attendance hot path`() {
        val (result, duration) =
            measureTimedValue { AttendanceService.clockIn(TestFixtures.uuid(), branchId, reliefUserId) }

        assertTrue(result.created, "fixture clock-in should create")
        assertTrue(
            duration < HOT_PATH_BUDGET_MS.milliseconds,
            "Performance regression: clockIn took $duration, expected < $HOT_PATH_BUDGET_MS ms",
        )
    }

    /**
     * Locks [branchDayId]'s row on its own connection, runs [command] on another thread,
     * and returns whether the command COMPLETED while the lock was held (true = the
     * serialization protocol is missing). The actor's commit is awaited before the
     * executors shut down, so postcondition reads never race teardown.
     */
    private fun holdBranchDayRowAndProbe(
        branchDayId: UUID,
        command: () -> Any?,
    ): Boolean {
        val holderLocked = CountDownLatch(1)
        val releaseHolder = CountDownLatch(1)
        val holder = Executors.newSingleThreadExecutor()
        val actor = Executors.newSingleThreadExecutor()
        try {
            holder.submit {
                transaction {
                    BranchDayRepository.acquireLock(branchDayId)
                    holderLocked.countDown()
                    releaseHolder.await(HOLDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            }
            assertTrue(holderLocked.await(HOLDER_TIMEOUT_SECONDS, TimeUnit.SECONDS), "holder never locked the row")

            val outcome = actor.submit { runCatching { command() } }
            // A correct implementation cannot finish while the row lock is held; a broken
            // one returns inside the probe window and fails the caller's assertFalse.
            val decidedWhileHeld =
                runCatching { outcome.get(PROBE_WAIT_MS, TimeUnit.MILLISECONDS) }
                    .fold(
                        onSuccess = { true },
                        onFailure = { failure ->
                            if (failure is TimeoutException) {
                                false
                            } else {
                                throw failure
                            }
                        },
                    )

            releaseHolder.countDown()
            outcome.get(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS)
            return decidedWhileHeld
        } finally {
            releaseHolder.countDown()
            holder.shutdownNow()
            actor.shutdownNow()
            assertTrue(holder.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
            assertTrue(actor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
        }
    }

    private companion object {
        private const val HOLDER_TIMEOUT_SECONDS = 10L
        private const val EXECUTOR_TERMINATION_SECONDS = 10L

        /** A correct implementation cannot finish while the row lock is held; a broken one finishes fast. */
        private const val PROBE_WAIT_MS = 750L
        private const val HOT_PATH_BUDGET_MS = 500L
    }
}
