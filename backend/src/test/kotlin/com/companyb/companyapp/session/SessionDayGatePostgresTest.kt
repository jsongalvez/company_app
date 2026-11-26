package com.companyb.companyapp.session
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.integration.LockBarrier
import com.companyb.companyapp.session.SessionRepository
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.session.SessionVoidTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionDayGatePostgresTest : SessionServicePostgresBase() {
    // #509 — session create joins the day-state gate inside the command transaction.
    @Test
    fun `create on REMITTED today without EDIT_PAST_DAY is rejected with no row written`() {
        BranchWorkforceFixtures.createRemittedBranchDay(branchId, TestFixtures.today)
        val blockedId = TestFixtures.uuid()

        assertFailsWith<ForbiddenException> {
            createSession(callerId, blockedId)
        }
        assertNull(SessionRepository.findById(blockedId))
        assertEquals(0L, auditEntryCount(SessionTable.tableName, blockedId))
    }

    @Test
    fun `coordinator create on REMITTED today without reason is rejected with no row written`() {
        BranchWorkforceFixtures.createRemittedBranchDay(branchId, TestFixtures.today)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        val blockedId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            createSession(callerId, blockedId)
        }
        assertNull(SessionRepository.findById(blockedId))
        assertEquals(0L, auditEntryCount(SessionTable.tableName, blockedId))
    }

    @Test
    fun `coordinator create on REMITTED today with reason succeeds and flags audit`() {
        BranchWorkforceFixtures.createRemittedBranchDay(branchId, TestFixtures.today)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)

        val result = createSession(callerId, sessionId, reason = "Late walk-in after remittance")

        assertTrue(result.created)
        val audit = auditEntry(SessionTable.tableName, sessionId)
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Late walk-in after remittance", audit[AuditLogTable.reason])
    }

    @Test
    fun `create on PAST day needs no coordinator and no reason`() {
        val pastDayId =
            BranchWorkforceFixtures.createBranchDayForDate(
                branchId,
                TestFixtures.today.minusDays(1),
            )
        val addedId = TestFixtures.uuid()

        val result = createSession(callerId, addedId, gatedBranchDayId = pastDayId)

        assertTrue(result.created)
        assertEquals(pastDayId, result.session.branchDayId)
        assertEquals(1L, auditEntryCount(SessionTable.tableName, addedId))
    }

    @Test
    fun `idempotent replay on REMITTED day bypasses the gate`() {
        createSession(callerId, sessionId)
        BranchWorkforceFixtures.createRemittedBranchDay(branchId, TestFixtures.today)

        val replay = createSession(callerId, sessionId)

        assertFalse(replay.created)
        assertEquals(sessionId, replay.session.id)
        assertEquals(1L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `concurrent remittance day transition serializes with session create gate`() {
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today)
        val blockedId = TestFixtures.uuid()

        // Observable barrier (#527): the holder keeps the day row locked with the same
        // locked primitive remittance submit/undo use per #506/#507 while the create
        // attempts its in-tx gate. Release happens only after the contender is observed
        // waiting on the holder's lock, so the create must block until the transition
        // commits, then read REMITTED and fail closed — never slipping an insert onto
        // the frozen day.
        val failure =
            runCatching {
                LockBarrier.withDayRemitBarrier(dayId, callerId) {
                    createSession(callerId, blockedId)
                }
            }.exceptionOrNull()

        assertTrue(failure is ForbiddenException)
        assertNull(SessionRepository.findById(blockedId))
        assertEquals(0L, auditEntryCount(SessionTable.tableName, blockedId))
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(dayId))
    }

    @Test
    fun `a branch member corrects a past day's mark with a reason`() {
        val pastDayId =
            BranchWorkforceFixtures.createBranchDayForDate(
                branchId,
                TestFixtures.today.minusDays(1),
            )
        val pastSessionId = TestFixtures.uuid()
        insertSessionOnDay(pastSessionId, pastDayId, SessionStatus.NO_SHOW)

        assertFailsWith<ForbiddenException> {
            SessionService.updateStatus(callerId, pastSessionId, SessionStatus.CANCELLED, 1)
        }

        IdentityFixtures.member(callerId, branchId)
        assertFailsWith<ValidationException> {
            SessionService.updateStatus(callerId, pastSessionId, SessionStatus.CANCELLED, 1)
        }
        val updated =
            SessionService.updateStatus(
                callerId,
                pastSessionId,
                SessionStatus.CANCELLED,
                1,
                "Called to cancel",
            )

        assertEquals(SessionStatus.CANCELLED, updated.sessionStatus)
    }

    @Test
    fun `remitted correction requires reason and audits flagged status change`() {
        val remittedDayId = insertRemittedDay()
        val remittedSessionId = TestFixtures.uuid()
        insertSessionOnDay(remittedSessionId, remittedDayId, SessionStatus.NO_SHOW)

        assertFailsWith<ForbiddenException> {
            SessionService.updateStatus(callerId, remittedSessionId, SessionStatus.PENDING, 1)
        }

        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)

        assertFailsWith<ValidationException> {
            SessionService.updateStatus(callerId, remittedSessionId, SessionStatus.PENDING, 1)
        }

        val updated =
            SessionService.updateStatus(
                callerId,
                remittedSessionId,
                SessionStatus.PENDING,
                1,
                "Corrected attendance mark",
            )

        assertEquals(SessionStatus.PENDING, updated.sessionStatus)
        val audit = auditEntry(SessionTable.tableName, remittedSessionId)
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Corrected attendance mark", audit[AuditLogTable.reason])
        assertEquals(
            "NO_SHOW",
            TestFixtures.extractJsonField(audit[AuditLogTable.oldValue].orEmpty(), "sessionStatus"),
        )
        assertEquals(
            "PENDING",
            TestFixtures.extractJsonField(audit[AuditLogTable.newValue].orEmpty(), "sessionStatus"),
        )
    }

    @Test
    fun `update status on REMITTED day without reason is rejected`() {
        val remittedDayId = insertRemittedDay()
        val remittedSessionId = TestFixtures.uuid()
        insertSessionOnDay(remittedSessionId, remittedDayId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)

        assertFailsWith<ValidationException> {
            SessionService.updateStatus(callerId, remittedSessionId, SessionStatus.COMPLETED, 1)
        }
    }

    @Test
    fun `update status on REMITTED day with reason succeeds and flags audit entry with reason`() {
        val remittedDayId = insertRemittedDay()
        val remittedSessionId = TestFixtures.uuid()
        insertSessionOnDay(remittedSessionId, remittedDayId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)

        val updated =
            SessionService.updateStatus(
                callerId,
                remittedSessionId,
                SessionStatus.COMPLETED,
                1,
                "Coordinator correction",
            )

        assertEquals(SessionStatus.COMPLETED, updated.sessionStatus)
        val audit = auditEntry(SessionTable.tableName, remittedSessionId)
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `void session on REMITTED day uses voidReason as audit reason and flags entry`() {
        val remittedDayId = insertRemittedDay()
        val remittedSessionId = TestFixtures.uuid()
        insertSessionOnDay(remittedSessionId, remittedDayId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)

        val voidId = TestFixtures.uuid()
        val result = SessionService.voidSession(callerId, remittedSessionId, voidId, "Customer request")

        assertNotNull(result.sessionVoid)
        val audit = auditEntry(SessionVoidTable.tableName, voidId)
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Customer request", audit[AuditLogTable.reason])
    }
}
