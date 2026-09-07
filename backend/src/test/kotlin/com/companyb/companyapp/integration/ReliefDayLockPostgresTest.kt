package com.companyb.companyapp.integration

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.workforce.ReliefAccessStatus
import com.companyb.companyapp.contracts.workforce.ReliefInviteStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.notification.NotificationTable
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.workforce.relief.ReliefAccessRepository
import com.companyb.companyapp.workforce.relief.ReliefAccessService
import com.companyb.companyapp.workforce.relief.ReliefInviteRepository
import com.companyb.companyapp.workforce.relief.ReliefInviteService
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        IdentityFixtures.insertTestUser(memberId, "daylock-member")
        IdentityFixtures.insertTestUser(requesterId, "daylock-requester")
        IdentityFixtures.insertTestUser(inviteeId, "daylock-invitee")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Relief Day Lock Branch")
        BranchWorkforceFixtures.insertTestAssignment(
            userId = memberId,
            branchId = branchId,
            slot = 1,
            assignedBy = memberId,
        )
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
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
        BranchWorkforceFixtures.grantEditPastDay(memberId, branchId, sourceId)

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
        BranchWorkforceFixtures.grantEditPastDay(memberId, branchId, sourceId)

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
        BranchWorkforceFixtures.grantEditPastDay(requesterId, branchId, sourceId)

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

        // Observable barrier (#527, mirrors #509/#510/#511/#512/#513): the holder keeps
        // the day row locked with the same locked primitive remittance submit uses while
        // the grant attempts its in-tx gate. Release happens only after the contender is
        // observed waiting on the holder's lock, so the grant must block until the
        // transition commits, then read REMITTED and fail closed — never minting a
        // capability on the frozen day.
        val failure =
            runCatching {
                LockBarrier.withDayRemitBarrier(branchDayId) {
                    ReliefAccessService.grantAccess(requestId, memberId)
                }
            }.exceptionOrNull()

        assertTrue(failure is ForbiddenException)
        assertEquals(ReliefAccessStatus.PENDING, ReliefAccessRepository.findById(requestId)?.requestStatus)
        assertFalse(
            ReliefInviteRepository.hasActiveGrant(requesterId, branchDayId),
            "blocked grant mints no capability",
        )
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(branchDayId))
    }

    @Test
    fun `concurrent remittance day transition serializes with request gate`() {
        val requestId = TestFixtures.uuid()
        val failure =
            runCatching {
                LockBarrier.withDayRemitBarrier(branchDayId) {
                    ReliefAccessService.requestReliefAccess(requestId, branchId, TestFixtures.today, requesterId)
                }
            }.exceptionOrNull()

        assertTrue(failure is ForbiddenException)
        assertNull(ReliefAccessRepository.findById(requestId), "blocked request writes no row")
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(branchDayId))
    }

    @Test
    fun `concurrent remittance day transition serializes with createInvite gate`() {
        val failure =
            runCatching {
                LockBarrier.withDayRemitBarrier(branchDayId) {
                    ReliefInviteService.createInvite(memberId, branchId, inviteeId, TestFixtures.today)
                }
            }.exceptionOrNull()

        assertTrue(failure is ForbiddenException)
        assertTrue(
            ReliefInviteRepository.findLiveInviteeIds(branchDayId).isEmpty(),
            "blocked invite writes no row",
        )
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(branchDayId))
    }

    @Test
    fun `concurrent remittance day transition serializes with accept gate`() {
        val invite = ReliefInviteService.createInvite(memberId, branchId, inviteeId, TestFixtures.today)
        val failure =
            runCatching {
                LockBarrier.withDayRemitBarrier(branchDayId) {
                    ReliefInviteService.acceptInvite(inviteeId, invite.id)
                }
            }.exceptionOrNull()

        assertTrue(failure is ValidationException)
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
}
