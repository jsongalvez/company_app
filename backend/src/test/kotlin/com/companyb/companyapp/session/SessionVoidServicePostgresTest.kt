package com.companyb.companyapp.session
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionVoidTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SessionVoidServicePostgresTest : SessionServicePostgresBase() {
    @Test
    fun `void session creates void record and writes audit`() {
        createSession(callerId, sessionId)
        val voidId = TestFixtures.uuid()

        val result = SessionService.voidSession(callerId, sessionId, voidId, "Customer request")

        assertTrue(result.created)
        assertEquals(sessionId, result.sessionVoid.sessionId)
        assertEquals("Customer request", result.sessionVoid.voidReason)
        assertEquals(callerId, result.sessionVoid.voidedBy)
        assertEquals(1L, auditEntryCount(SessionVoidTable.tableName, voidId))
    }

    @Test
    fun `void session returns existing on duplicate`() {
        createSession(callerId, sessionId)
        val voidId = TestFixtures.uuid()

        val first = SessionService.voidSession(callerId, sessionId, voidId, "Customer request")
        val duplicate = SessionService.voidSession(callerId, sessionId, voidId, "Customer request")

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals(first.sessionVoid.id, duplicate.sessionVoid.id)
    }

    @Test
    fun `void session without VOID_SESSION is allowed at service layer`() {
        createSession(callerId, sessionId)
        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "session-other")

        val result = SessionService.voidSession(otherCaller, sessionId, TestFixtures.uuid(), "Customer request")

        assertTrue(result.created)
    }

    @Test
    fun `void session throws 404 for non-existent session`() {
        assertFailsWith<NotFoundException> {
            SessionService.voidSession(callerId, TestFixtures.uuid(), TestFixtures.uuid(), "Customer request")
        }
    }

    @Test
    fun `unvoid session sets unvoided fields and writes audit`() {
        createSession(callerId, sessionId)
        val voidId = TestFixtures.uuid()
        SessionService.voidSession(callerId, sessionId, voidId, "Customer request")

        val result = SessionService.unvoidSession(callerId, sessionId, "Resolved in error")

        assertNotNull(result.unvoidedAt)
        assertEquals(callerId, result.unvoidedBy)
        assertEquals("Resolved in error", result.unvoidedReason)
        assertEquals(2L, auditEntryCount(SessionVoidTable.tableName, voidId))
    }

    @Test
    fun `unvoid session is idempotent`() {
        createSession(callerId, sessionId)
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Customer request")

        val first = SessionService.unvoidSession(callerId, sessionId, "Resolved in error")
        val duplicate = SessionService.unvoidSession(callerId, sessionId, "Resolved in error")

        assertEquals(first.id, duplicate.id)
        assertEquals(first.unvoidedAt, duplicate.unvoidedAt)
    }

    @Test
    fun `unvoid session throws 404 for non-voided session`() {
        createSession(callerId, sessionId)

        assertFailsWith<NotFoundException> {
            SessionService.unvoidSession(callerId, sessionId, "Resolved in error")
        }
    }

    @Test
    fun `unvoid session without VOID_SESSION is allowed at service layer`() {
        createSession(callerId, sessionId)
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Customer request")
        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "session-other")

        val result = SessionService.unvoidSession(otherCaller, sessionId, "Resolved in error")

        assertNotNull(result.unvoidedAt)
    }

    @Test
    fun `void session throws 404 for non-existent session on unvoid`() {
        assertFailsWith<NotFoundException> {
            SessionService.unvoidSession(callerId, TestFixtures.uuid(), "Resolved in error")
        }
    }
}
