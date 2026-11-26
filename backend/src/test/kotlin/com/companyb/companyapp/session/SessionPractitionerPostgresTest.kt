package com.companyb.companyapp.session
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.session.SessionPractitionerRepository
import com.companyb.companyapp.session.SessionPractitionerService
import com.companyb.companyapp.session.SessionPractitionerTable
import com.companyb.companyapp.session.SessionRepository
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionPractitionerPostgresTest : SessionServicePostgresBase() {
    @Test
    fun `add practitioner snapshots slot and writes audit`() {
        createSession(callerId, practitionerSessionId)
        val requestId = TestFixtures.uuid()

        val result =
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                NewSessionPractitioner(id = requestId, practitionerId = practitionerId, remarks = "Test remarks"),
                sessionId = practitionerSessionId,
            )

        assertTrue(result.created)
        assertEquals(practitionerId, result.practitioner.practitionerId)
        assertEquals(1, result.practitioner.slotAtTime.toInt())
        assertEquals("Test remarks", result.practitioner.remarks)
        assertEquals(1L, auditEntryCount(SessionTable.tableName, practitionerSessionId))
    }

    @Test
    fun `add practitioner returns existing on duplicate`() {
        createSession(callerId, practitionerSessionId)
        val requestId = TestFixtures.uuid()

        val first =
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                NewSessionPractitioner(id = requestId, practitionerId = practitionerId, remarks = null),
                sessionId = practitionerSessionId,
            )
        val duplicate =
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                NewSessionPractitioner(id = requestId, practitionerId = practitionerId, remarks = null),
                sessionId = practitionerSessionId,
            )

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals(first.practitioner.id, duplicate.practitioner.id)
    }

    @Test
    fun `add practitioner without EDIT_BRANCH_DATA is allowed at service layer`() {
        createSession(callerId, practitionerSessionId)
        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "session-other")
        insertAssignment(otherCaller)

        val result =
            SessionPractitionerService.addPractitioner(
                callerId = otherCaller,
                NewSessionPractitioner(id = TestFixtures.uuid(), practitionerId = practitionerId, remarks = null),
                sessionId = practitionerSessionId,
            )

        assertTrue(result.created)
    }

    @Test
    fun `add practitioner throws 404 for non-existent session`() {
        assertFailsWith<NotFoundException> {
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                NewSessionPractitioner(id = TestFixtures.uuid(), practitionerId = practitionerId, remarks = null),
                sessionId = TestFixtures.uuid(),
            )
        }
    }

    @Test
    fun `add practitioner throws 404 for unknown practitioner with no row written`() {
        createSession(callerId, practitionerSessionId)
        val unknownPractitionerId = TestFixtures.uuid()
        val requestId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                NewSessionPractitioner(id = requestId, practitionerId = unknownPractitionerId, remarks = null),
                sessionId = practitionerSessionId,
            )
        }
        val stored =
            transaction {
                SessionPractitionerRepository.findBySessionAndPractitionerInTransaction(
                    practitionerSessionId,
                    unknownPractitionerId,
                )
            }
        assertNull(stored)
        assertEquals(0L, auditEntryCount(SessionPractitionerTable.tableName, requestId))
    }

    // #926 — status-only INACTIVE guard (#366 parity): unknown users 404 above,
    // deactivated users 400 here before any slot snapshot or row write.
    @Test
    fun `add practitioner rejects INACTIVE practitioner with assignment and writes nothing`() {
        createSession(callerId, practitionerSessionId)
        val inactiveId = TestFixtures.uuid()
        IdentityFixtures.insertUser(
            id = inactiveId,
            username = "inactive-${inactiveId.toString().take(8)}",
            passwordHash = "test-password-hash",
            email = "${inactiveId.toString().take(8)}@t.st",
            displayName = "Test inactive",
            status = UserStatus.INACTIVE,
        )
        insertAssignment(inactiveId)
        val requestId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                NewSessionPractitioner(id = requestId, practitionerId = inactiveId, remarks = null),
                sessionId = practitionerSessionId,
            )
        }
        val stored =
            transaction {
                SessionPractitionerRepository.findBySessionAndPractitionerInTransaction(
                    practitionerSessionId,
                    inactiveId,
                )
            }
        assertNull(stored)
        assertEquals(0L, auditEntryCount(SessionPractitionerTable.tableName, requestId))
    }

    // #926 — the 999-fallback leg: an INACTIVE outsider (no assignment) must also 400,
    // never 201 slot-999.
    @Test
    fun `add practitioner rejects INACTIVE outsider with no assignment and writes nothing`() {
        createSession(callerId, practitionerSessionId)
        val inactiveOutsiderId = TestFixtures.uuid()
        IdentityFixtures.insertUser(
            id = inactiveOutsiderId,
            username = "inactive-out-${inactiveOutsiderId.toString().take(8)}",
            passwordHash = "test-password-hash",
            email = "out-${inactiveOutsiderId.toString().take(8)}@t.st",
            displayName = "Test inactive outsider",
            status = UserStatus.INACTIVE,
        )
        val requestId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                NewSessionPractitioner(id = requestId, practitionerId = inactiveOutsiderId, remarks = null),
                sessionId = practitionerSessionId,
            )
        }
        val stored =
            transaction {
                SessionPractitionerRepository.findBySessionAndPractitionerInTransaction(
                    practitionerSessionId,
                    inactiveOutsiderId,
                )
            }
        assertNull(stored)
        assertEquals(0L, auditEntryCount(SessionPractitionerTable.tableName, requestId))
    }

    // #926 — narrow-guard non-goal pin: an ACTIVE outsider (relief) still gets 201 slot-999.
    @Test
    fun `add practitioner accepts ACTIVE outsider with relief 999`() {
        createSession(callerId, practitionerSessionId)
        val outsiderId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(outsiderId, "session-relief-outsider")

        val result =
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                NewSessionPractitioner(id = TestFixtures.uuid(), practitionerId = outsiderId, remarks = null),
                sessionId = practitionerSessionId,
            )

        assertTrue(result.created)
        assertEquals(999, result.practitioner.slotAtTime.toInt())
    }

    @Test
    fun `update practitioner remarks succeeds and increments session version and writes audit`() {
        createSession(callerId, practitionerSessionId)
        val practitionerEntityId = TestFixtures.uuid()
        SessionPractitionerService.addPractitioner(
            callerId = callerId,
            NewSessionPractitioner(
                id = practitionerEntityId,
                practitionerId = practitionerId,
                remarks = "Initial remarks",
            ),
            sessionId = practitionerSessionId,
        )

        val updated =
            SessionPractitionerService.updatePractitionerRemarks(
                callerId = callerId,
                sessionId = practitionerSessionId,
                practitionerId = practitionerId,
                RemarksEdit(remarks = "Updated remarks"),
            )

        assertEquals("Updated remarks", updated.remarks)

        val session = SessionRepository.findById(practitionerSessionId)!!
        assertEquals(3, session.version)
        assertEquals(2L, auditEntryCount(SessionPractitionerTable.tableName, practitionerEntityId))
    }

    @Test
    fun `update practitioner remarks throws 404 for non-existent practitioner in session`() {
        createSession(callerId, practitionerSessionId)

        assertFailsWith<NotFoundException> {
            SessionPractitionerService.updatePractitionerRemarks(
                callerId = callerId,
                sessionId = practitionerSessionId,
                practitionerId = TestFixtures.uuid(),
                RemarksEdit(remarks = "Some remarks"),
            )
        }
    }

    @Test
    fun `remove practitioner succeeds and increments session version and writes audit`() {
        createSession(callerId, practitionerSessionId)
        val practitionerEntityId = TestFixtures.uuid()
        SessionPractitionerService.addPractitioner(
            callerId = callerId,
            NewSessionPractitioner(id = practitionerEntityId, practitionerId = practitionerId, remarks = null),
            sessionId = practitionerSessionId,
        )

        SessionPractitionerService.removePractitioner(
            callerId = callerId,
            sessionId = practitionerSessionId,
            practitionerId = practitionerId,
        )

        val practitioners =
            SessionPractitionerRepository.findBySessionId(practitionerSessionId)
        assertTrue(practitioners.isEmpty())

        val session = SessionRepository.findById(practitionerSessionId)!!
        assertEquals(3, session.version)
        assertEquals(2L, auditEntryCount(SessionPractitionerTable.tableName, practitionerEntityId))
        assertEquals(1L, auditEntryCount(SessionPractitionerTable.tableName, practitionerEntityId, AuditAction.DELETE))
    }

    @Test
    fun `remove practitioner throws 404 for non-existent practitioner in session`() {
        createSession(callerId, practitionerSessionId)

        assertFailsWith<NotFoundException> {
            SessionPractitionerService.removePractitioner(
                callerId = callerId,
                sessionId = practitionerSessionId,
                practitionerId = TestFixtures.uuid(),
            )
        }
    }

    @Test
    fun `stale practitioner mutation version update throws typed conflict`() {
        createSession(callerId, practitionerSessionId)

        assertFailsWith<VersionMismatchException> {
            transaction {
                SessionPractitionerRepository.incrementSessionVersion(practitionerSessionId, 0)
            }
        }
    }
}
