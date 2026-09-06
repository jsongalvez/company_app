package com.companyb.companyapp.service

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.repository.SessionVoidRepository
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.service.session.SessionService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.LockBarrier
import com.companyb.companyapp.test.TestFixtures
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Session void replay is ownership-validated with re-arm (#514): a void id owned by
 * another session fails closed, a re-void after unvoid re-arms the single per-session
 * row (fresh caller id discarded), and a same-id retry stays an idempotent ack.
 */
class SessionVoidReplayPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "void-replay-caller")
        DatabaseTestHelper.insertTestBranch(branchId, "Test Void Replay Branch")
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
    }

    @Test
    fun `re-void after unvoid re-arms the same row and writes audit`() {
        val sessionId = insertSession()
        val voidId = TestFixtures.uuid()
        SessionService.voidSession(callerId, sessionId, voidId, "Created in error")
        SessionService.unvoidSession(callerId, sessionId, "Resolved in error")

        val freshId = TestFixtures.uuid()
        val result = SessionService.voidSession(callerId, sessionId, freshId, "Rechecked — still in error")

        assertTrue(result.created)
        assertEquals(voidId, result.sessionVoid.id, "re-arm preserves the canonical row id")
        assertNull(result.sessionVoid.unvoidedAt)
        assertEquals("Rechecked — still in error", result.sessionVoid.voidReason)
        assertTrue(isVoided(sessionId))
        assertEquals(3L, voidAuditCount(voidId), "void + unvoid + re-arm each audit once")
        assertEquals(0L, voidAuditCount(freshId), "discarded fresh id writes no audit")
    }

    @Test
    fun `void id owned by another session fails closed with neither state changed`() {
        val sessionA = insertSession()
        val sessionB = insertSession()
        val voidIdB = TestFixtures.uuid()
        SessionService.voidSession(callerId, sessionB, voidIdB, "Created in error")

        assertFailsWith<ConflictException> {
            SessionService.voidSession(callerId, sessionA, voidIdB, "Created in error")
        }

        assertNull(SessionVoidRepository.findBySessionId(sessionA), "rejected void writes no row")
        assertFalse(isVoided(sessionA))
        val voidB = SessionVoidRepository.findBySessionId(sessionB)
        assertNotNull(voidB)
        assertNull(voidB.unvoidedAt, "owner row untouched")
        assertEquals(1L, voidAuditCount(voidIdB), "rejected void writes no audit")
    }

    @Test
    fun `same-id retry stays idempotent with no duplicate audit`() {
        val sessionId = insertSession()
        val voidId = TestFixtures.uuid()

        val first = SessionService.voidSession(callerId, sessionId, voidId, "Created in error")
        val duplicate = SessionService.voidSession(callerId, sessionId, voidId, "Created in error")

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals(first.sessionVoid.id, duplicate.sessionVoid.id)
        assertEquals(1L, voidAuditCount(voidId))
    }

    @Test
    fun `concurrent re-voids serialize on the session lock to a single re-armed row`() {
        val sessionId = insertSession()
        val voidId = TestFixtures.uuid()
        SessionService.voidSession(callerId, sessionId, voidId, "Created in error")
        SessionService.unvoidSession(callerId, sessionId, "Resolved in error")

        // Observable barrier (#527): the holder keeps the session row locked with the
        // same locked primitive the re-void command takes while the re-void attempts
        // its write. Release happens only after the contender is observed waiting on
        // the holder's lock, so the re-void must block rather than slipping a second
        // row past it.
        val result =
            LockBarrier.withSessionBarrier(sessionId) {
                SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Rechecked — still in error")
            }

        assertTrue(result.created)
        assertNull(SessionVoidRepository.findBySessionId(sessionId)?.unvoidedAt)
        assertEquals(1L, voidRowCount(sessionId), "exactly one void row per session")
        assertEquals(3L, voidAuditCount(voidId))
    }

    private fun insertSession(): UUID {
        val clientId = DatabaseTestHelper.insertTestClient()
        val sessionId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestSession(sessionId, clientId, branchDayId)
        return sessionId
    }

    private fun isVoided(sessionId: UUID): Boolean =
        transaction {
            SessionTable
                .selectAll()
                .where { SessionTable.id eq sessionId }
                .single()[SessionTable.isVoided]
        }

    private fun voidAuditCount(voidId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq SessionVoidTable.tableName) and
                        (AuditLogTable.recordId eq voidId)
                }.count()
        }

    private fun voidRowCount(sessionId: UUID): Long =
        transaction {
            SessionVoidTable
                .selectAll()
                .where { SessionVoidTable.sessionId eq sessionId }
                .count()
        }
}
