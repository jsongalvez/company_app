package com.companyb.companyapp.service

import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.domain.ReliefInviteStatus
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.ReliefAccessRepository
import com.companyb.companyapp.repository.ReliefInviteRepository
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.NotificationTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * Relief grant/request/invite mutations serialize with the remittance REMITTED
 * transition (#515). Every path gates through the locked branch-day read, so a
 * pre-freeze authorization can no longer mint a day-scoped capability (or move
 * relief state) onto a financially-frozen day.
 */
class ReliefDayLockPostgresTest : BasePostgresTest() {
    private val memberId = TestFixtures.uuid()
    private val requesterId = TestFixtures.uuid()
    private val inviteeId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(memberId, "daylock-member")
        DatabaseTestHelper.insertTestUser(requesterId, "daylock-requester")
        DatabaseTestHelper.insertTestUser(inviteeId, "daylock-invitee")
        DatabaseTestHelper.insertTestBranch(branchId, "Test Relief Day Lock Branch")
        DatabaseTestHelper.insertTestAssignment(
            userId = memberId,
            branchId = branchId,
            slot = 1,
            assignedBy = memberId,
        )
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
    }

    @Test
    fun `grant on day REMITTED by remittance submit is rejected with no capability`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, requesterId)
        submitRemittanceCoveringDay(branchDayId)
        val auditsBefore = auditCount()
        val notificationsBefore = notificationCount()

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.grantAccess(requestId, memberId)
        }

        assertEquals(ReliefAccessStatus.PENDING, ReliefAccessRepository.findById(requestId)?.requestStatus)
        assertFalse(
            ReliefInviteRepository.hasActiveGrant(requesterId, branchDayId),
            "rejected grant mints no capability",
        )
        assertEquals(auditsBefore, auditCount(), "rejected grant writes no audit rows")
        assertEquals(notificationsBefore, notificationCount(), "rejected grant sends no notifications")
    }

    @Test
    fun `deny on day REMITTED by remittance submit is rejected with no mutation`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, requesterId)
        submitRemittanceCoveringDay(branchDayId)
        val auditsBefore = auditCount()
        val notificationsBefore = notificationCount()

        assertFailsWith<ForbiddenException> {
            ReliefAccessService.denyAccess(requestId, memberId)
        }

        assertEquals(ReliefAccessStatus.PENDING, ReliefAccessRepository.findById(requestId)?.requestStatus)
        assertEquals(auditsBefore, auditCount(), "rejected deny writes no audit rows")
        assertEquals(notificationsBefore, notificationCount(), "rejected deny sends no notifications")
    }

    @Test
    fun `request on day REMITTED by remittance submit is rejected with no row`() {
        submitRemittanceCoveringDay(branchDayId)
        val auditsBefore = auditCount()

        val requestId = TestFixtures.uuid()
        assertFailsWith<ForbiddenException> {
            ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, requesterId)
        }

        assertNull(ReliefAccessRepository.findById(requestId), "rejected request writes no row")
        assertEquals(auditsBefore, auditCount(), "rejected request writes no audit rows")
    }

    @Test
    fun `createInvite on day REMITTED by remittance submit is rejected with no row`() {
        submitRemittanceCoveringDay(branchDayId)

        assertFailsWith<ForbiddenException> {
            ReliefInviteService.createInvite(memberId, branchId, inviteeId, TestFixtures.today)
        }

        assertTrue(
            ReliefInviteRepository.findLiveInviteeIds(branchDayId).isEmpty(),
            "rejected invite writes no row",
        )
    }

    @Test
    fun `accept on day REMITTED by remittance submit is rejected with no grant`() {
        val invite = ReliefInviteService.createInvite(memberId, branchId, inviteeId, TestFixtures.today)
        submitRemittanceCoveringDay(branchDayId)
        val auditsBefore = auditCount()
        val notificationsBefore = notificationCount()

        assertFailsWith<ValidationException> {
            ReliefInviteService.acceptInvite(inviteeId, invite.id)
        }

        assertEquals(ReliefInviteStatus.PENDING, ReliefInviteRepository.findById(invite.id)?.status)
        assertFalse(
            ReliefInviteRepository.hasActiveGrant(inviteeId, branchDayId),
            "rejected accept mints no capability",
        )
        assertEquals(auditsBefore, auditCount(), "rejected accept writes no audit rows")
        assertEquals(notificationsBefore, notificationCount(), "rejected accept sends no notifications")
    }

    @Test
    fun `grant on submit-REMITTED day with reason and EDIT_PAST_DAY succeeds flagged`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, requesterId)
        submitRemittanceCoveringDay(branchDayId)
        DatabaseTestHelper.grantEditPastDay(memberId, branchId, sourceId)

        val result = ReliefAccessService.grantAccess(requestId, memberId, "Coordinator correction")

        assertEquals(ReliefAccessStatus.GRANTED, result.requestStatus)
        assertTrue(ReliefInviteRepository.hasActiveGrant(requesterId, branchDayId))
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq memberId) and
                            (AuditLogTable.recordId eq requestId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `deny on submit-REMITTED day with reason and EDIT_PAST_DAY succeeds flagged`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, requesterId)
        submitRemittanceCoveringDay(branchDayId)
        DatabaseTestHelper.grantEditPastDay(memberId, branchId, sourceId)

        val result = ReliefAccessService.denyAccess(requestId, memberId, "Coordinator correction")

        assertEquals(ReliefAccessStatus.DENIED, result.requestStatus)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq memberId) and
                            (AuditLogTable.recordId eq requestId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `request on submit-REMITTED day with reason and EDIT_PAST_DAY succeeds flagged`() {
        submitRemittanceCoveringDay(branchDayId)
        DatabaseTestHelper.grantEditPastDay(requesterId, branchId, sourceId)

        val requestId = TestFixtures.uuid()
        val result =
            ReliefAccessService.requestReliefAccess(
                requestId,
                branchId,
                TestFixtures.today,
                requesterId,
                "Coordinator correction",
            )

        assertEquals(ReliefAccessStatus.PENDING, result.requestStatus)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq requesterId) and
                            (AuditLogTable.recordId eq requestId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `concurrent remittance day transition serializes with grant gate`() {
        val requestId = TestFixtures.uuid()
        ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, requesterId)
        val dayLocked = CountDownLatch(1)

        // Barrier (mirrors #509/#510/#511/#512/#513): the submit side holds the day row
        // lock (the same locked primitive remittance submit uses) while the grant attempts
        // its in-tx gate. The grant must block on the lock until the transition commits,
        // then read REMITTED and fail closed — never minting a capability on the frozen day.
        val submitter =
            thread {
                transaction {
                    BranchDayService.lockDaysInTransaction(listOf(branchDayId))
                    dayLocked.countDown()
                    Thread.sleep(BARRIER_HOLD_MILLIS)
                    BranchDayService.markDaysRemittedInTransaction(listOf(branchDayId))
                }
            }
        assertTrue(dayLocked.await(BARRIER_WAIT_SECONDS, TimeUnit.SECONDS))

        val (failure, blockedFor) =
            measureTimedValue {
                runCatching {
                    ReliefAccessService.grantAccess(requestId, memberId)
                }.exceptionOrNull()
            }
        submitter.join(BARRIER_JOIN_MILLIS)

        assertTrue(failure is ForbiddenException)
        assertTrue(
            blockedFor.inWholeMilliseconds >= BARRIER_MIN_BLOCKED_MILLIS,
            "grant did not block on the day lock: finished in $blockedFor",
        )
        assertEquals(ReliefAccessStatus.PENDING, ReliefAccessRepository.findById(requestId)?.requestStatus)
        assertFalse(
            ReliefInviteRepository.hasActiveGrant(requesterId, branchDayId),
            "blocked grant mints no capability",
        )
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(branchDayId))
    }

    @Test
    fun `concurrent remittance day transition serializes with request gate`() {
        val dayLocked = CountDownLatch(1)

        val submitter =
            thread {
                transaction {
                    BranchDayService.lockDaysInTransaction(listOf(branchDayId))
                    dayLocked.countDown()
                    Thread.sleep(BARRIER_HOLD_MILLIS)
                    BranchDayService.markDaysRemittedInTransaction(listOf(branchDayId))
                }
            }
        assertTrue(dayLocked.await(BARRIER_WAIT_SECONDS, TimeUnit.SECONDS))

        val requestId = TestFixtures.uuid()
        val (failure, blockedFor) =
            measureTimedValue {
                runCatching {
                    ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, requesterId)
                }.exceptionOrNull()
            }
        submitter.join(BARRIER_JOIN_MILLIS)

        assertTrue(failure is ForbiddenException)
        assertTrue(
            blockedFor.inWholeMilliseconds >= BARRIER_MIN_BLOCKED_MILLIS,
            "request did not block on the day lock: finished in $blockedFor",
        )
        assertNull(ReliefAccessRepository.findById(requestId), "blocked request writes no row")
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(branchDayId))
    }

    @Test
    fun `concurrent remittance day transition serializes with createInvite gate`() {
        val dayLocked = CountDownLatch(1)

        val submitter =
            thread {
                transaction {
                    BranchDayService.lockDaysInTransaction(listOf(branchDayId))
                    dayLocked.countDown()
                    Thread.sleep(BARRIER_HOLD_MILLIS)
                    BranchDayService.markDaysRemittedInTransaction(listOf(branchDayId))
                }
            }
        assertTrue(dayLocked.await(BARRIER_WAIT_SECONDS, TimeUnit.SECONDS))

        val (failure, blockedFor) =
            measureTimedValue {
                runCatching {
                    ReliefInviteService.createInvite(memberId, branchId, inviteeId, TestFixtures.today)
                }.exceptionOrNull()
            }
        submitter.join(BARRIER_JOIN_MILLIS)

        assertTrue(failure is ForbiddenException)
        assertTrue(
            blockedFor.inWholeMilliseconds >= BARRIER_MIN_BLOCKED_MILLIS,
            "createInvite did not block on the day lock: finished in $blockedFor",
        )
        assertTrue(
            ReliefInviteRepository.findLiveInviteeIds(branchDayId).isEmpty(),
            "blocked invite writes no row",
        )
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(branchDayId))
    }

    @Test
    fun `concurrent remittance day transition serializes with accept gate`() {
        val invite = ReliefInviteService.createInvite(memberId, branchId, inviteeId, TestFixtures.today)
        val dayLocked = CountDownLatch(1)

        val submitter =
            thread {
                transaction {
                    BranchDayService.lockDaysInTransaction(listOf(branchDayId))
                    dayLocked.countDown()
                    Thread.sleep(BARRIER_HOLD_MILLIS)
                    BranchDayService.markDaysRemittedInTransaction(listOf(branchDayId))
                }
            }
        assertTrue(dayLocked.await(BARRIER_WAIT_SECONDS, TimeUnit.SECONDS))

        val (failure, blockedFor) =
            measureTimedValue {
                runCatching {
                    ReliefInviteService.acceptInvite(inviteeId, invite.id)
                }.exceptionOrNull()
            }
        submitter.join(BARRIER_JOIN_MILLIS)

        assertTrue(failure is ValidationException)
        assertTrue(
            blockedFor.inWholeMilliseconds >= BARRIER_MIN_BLOCKED_MILLIS,
            "accept did not block on the day lock: finished in $blockedFor",
        )
        assertEquals(ReliefInviteStatus.PENDING, ReliefInviteRepository.findById(invite.id)?.status)
        assertFalse(
            ReliefInviteRepository.hasActiveGrant(inviteeId, branchDayId),
            "blocked accept mints no capability",
        )
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(branchDayId))
    }

    private fun submitRemittanceCoveringDay(dayId: UUID) {
        val remittanceId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = memberId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = TestFixtures.today.minusDays(1),
            dateRangeEnd = TestFixtures.today,
        )
        RemittanceService.addDayBreakdown(
            callerId = memberId,
            remittanceId = remittanceId,
            id = TestFixtures.uuid(),
            branchDayId = dayId,
        )
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(memberId, remittanceId, version)
    }

    private fun auditCount(): Long =
        transaction {
            AuditLogTable.selectAll().count()
        }

    private fun notificationCount(): Long =
        transaction {
            NotificationTable.selectAll().count()
        }

    companion object {
        private const val BARRIER_HOLD_MILLIS = 5000L
        private const val BARRIER_MIN_BLOCKED_MILLIS = 3000L
        private const val BARRIER_WAIT_SECONDS = 10L
        private const val BARRIER_JOIN_MILLIS = 30000L
    }
}
