package com.companyb.companyapp.service

import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.SessionVoidRepository
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.service.session.SessionService
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

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

        val sessionLocked = CountDownLatch(1)
        val holder =
            thread {
                transaction {
                    SessionRepository.acquireLockInTransaction(sessionId)
                        ?: error("session not found for re-void barrier: $sessionId")
                    sessionLocked.countDown()
                    Thread.sleep(BARRIER_HOLD_MILLIS)
                }
            }
        assertTrue(sessionLocked.await(BARRIER_WAIT_SECONDS, TimeUnit.SECONDS))

        // The re-void must block on the session row lock (the same lock the command
        // takes before the re-arm write) rather than slipping a second row past it.
        // The elapsed lower bound is the load-bearing assertion: without the lock the
        // re-void would finish in milliseconds.
        val (result, blockedFor) =
            measureTimedValue {
                SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Rechecked — still in error")
            }
        holder.join(BARRIER_JOIN_MILLIS)

        assertTrue(result.created)
        assertTrue(
            blockedFor.inWholeMilliseconds >= BARRIER_MIN_BLOCKED_MILLIS,
            "re-void did not block on the session lock: finished in $blockedFor",
        )
        assertTrue(blockedFor < BARRIER_HOLD_MILLIS.seconds * 4, "re-void regressed: took $blockedFor")
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

    companion object {
        private const val BARRIER_HOLD_MILLIS = 5000L
        private const val BARRIER_MIN_BLOCKED_MILLIS = 3000L
        private const val BARRIER_WAIT_SECONDS = 10L
        private const val BARRIER_JOIN_MILLIS = 30000L
    }
}
