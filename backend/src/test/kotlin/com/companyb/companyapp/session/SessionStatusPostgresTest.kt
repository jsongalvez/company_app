package com.companyb.companyapp.session
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.client.ClientService
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.session.SessionRepository
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.session.SessionVoidTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionStatusPostgresTest : SessionServicePostgresBase() {
    @Test
    fun `update status from PENDING to COMPLETED succeeds and increments version`() {
        createSession(callerId, sessionId)

        val updated = SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, 1)

        assertEquals(SessionStatus.COMPLETED, updated.sessionStatus)
        assertEquals(2, updated.version)
        assertEquals(2L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `update status with wrong version throws 409`() {
        createSession(callerId, sessionId)

        assertFailsWith<ConflictException> {
            SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, 99)
        }
    }

    @Test
    fun `update status for non-existent session throws 404`() {
        assertFailsWith<NotFoundException> {
            SessionService.updateStatus(callerId, TestFixtures.uuid(), SessionStatus.COMPLETED, 1)
        }
    }

    @Test
    fun `update status without EDIT_BRANCH_DATA is allowed at service layer`() {
        createSession(callerId, sessionId)
        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "session-other")

        val updated = SessionService.updateStatus(otherCaller, sessionId, SessionStatus.COMPLETED, 1)

        assertEquals(SessionStatus.COMPLETED, updated.sessionStatus)
        assertEquals(2, updated.version)
    }

    @Test
    fun `walk-in session cannot transition to NO_SHOW`() {
        createSession(callerId, walkInSessionId, isWalkIn = true)

        assertFailsWith<ValidationException> {
            SessionService.updateStatus(callerId, walkInSessionId, SessionStatus.NO_SHOW, 1)
        }
    }

    @Test
    fun `walk-in session cannot transition to CANCELLED`() {
        createSession(callerId, walkInSessionId, isWalkIn = true)

        assertFailsWith<ValidationException> {
            SessionService.updateStatus(callerId, walkInSessionId, SessionStatus.CANCELLED, 1)
        }
    }

    // #423 — the booking flow pin: a booked create round-trips its fields and the
    // NO_SHOW/CANCELLED statuses are reachable for booked sessions (the flip side of the
    // walk-in prohibition above — BR §Session Status "Booked sessions can be marked as
    // no-show or cancelled").
    @Test
    fun `booked session create stamps booking time on the database clock and reaches NO_SHOW`() {
        val bookedSessionId = TestFixtures.uuid()
        val nextAppointmentDate = TestFixtures.today.plusDays(2)
        val beforeCreate = databaseNow()

        val result =
            createSession(
                callerId,
                bookedSessionId,
                isWalkIn = false,
                nextAppointmentDate = nextAppointmentDate,
            )
        val afterCreate = databaseNow()

        assertFalse(result.session.isWalkIn)
        val bookedAt = assertNotNull(result.session.bookedAt)
        assertTrue(bookedAt >= beforeCreate)
        assertTrue(bookedAt <= afterCreate)
        assertEquals(nextAppointmentDate, result.session.nextAppointmentDate)

        val updated = SessionService.updateStatus(callerId, bookedSessionId, SessionStatus.NO_SHOW, 1)
        assertEquals(SessionStatus.NO_SHOW, updated.sessionStatus)
    }

    @Test
    fun `booked pending session can be marked CANCELLED without Coordinator authority`() {
        val bookedSessionId = insertSessionWithStatus(SessionStatus.PENDING)

        val updated = SessionService.updateStatus(callerId, bookedSessionId, SessionStatus.CANCELLED, 1)

        assertEquals(SessionStatus.CANCELLED, updated.sessionStatus)
    }

    @Test
    fun `booked status corrections need branch membership`() {
        val correctionSessionId = insertSessionWithStatus(SessionStatus.NO_SHOW)

        assertFailsWith<ForbiddenException> {
            SessionService.updateStatus(callerId, correctionSessionId, SessionStatus.CANCELLED, 1)
        }
    }

    @Test
    fun `anyone who can mark can undo a mark on an open day`() {
        listOf(SessionStatus.COMPLETED, SessionStatus.NO_SHOW, SessionStatus.CANCELLED).forEach { from ->
            val undoSessionId = insertSessionWithStatus(from)
            val updated = SessionService.updateStatus(callerId, undoSessionId, SessionStatus.PENDING, 1)
            assertEquals(SessionStatus.PENDING, updated.sessionStatus)
        }
    }

    @Test
    fun `booked corrections need branch membership on every day state`() {
        correctionDays().forEach { (_, dayId) ->
            listOf(
                SessionStatus.NO_SHOW to SessionStatus.CANCELLED,
                SessionStatus.CANCELLED to SessionStatus.NO_SHOW,
            ).forEach { (from, to) ->
                val correctionSessionId = TestFixtures.uuid()
                insertSessionOnDay(correctionSessionId, dayId, from)

                assertFailsWith<ForbiddenException> {
                    SessionService.updateStatus(callerId, correctionSessionId, to, 1)
                }
            }
        }
    }

    @Test
    fun `a branch member applies every booked correction edge and audit status before and after`() {
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)

        val edges =
            listOf(
                SessionStatus.NO_SHOW to SessionStatus.PENDING,
                SessionStatus.NO_SHOW to SessionStatus.CANCELLED,
                SessionStatus.CANCELLED to SessionStatus.PENDING,
                SessionStatus.CANCELLED to SessionStatus.NO_SHOW,
            )

        correctionDays().forEach { (_, dayId) ->
            edges.forEach { (from, to) ->
                val correctionSessionId = TestFixtures.uuid()
                insertSessionOnDay(correctionSessionId, dayId, from)
                val reason = "Corrected attendance mark"
                val updated = SessionService.updateStatus(callerId, correctionSessionId, to, 1, reason)

                assertEquals(to, updated.sessionStatus)
                val audit = auditEntry(SessionTable.tableName, correctionSessionId)
                assertEquals(
                    from.name,
                    TestFixtures.extractJsonField(audit[AuditLogTable.oldValue].orEmpty(), "sessionStatus"),
                )
                assertEquals(
                    to.name,
                    TestFixtures.extractJsonField(audit[AuditLogTable.newValue].orEmpty(), "sessionStatus"),
                )
            }
        }
    }

    @Test
    fun `reopening session conflicts when client already has a pending session`() {
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        val day = BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
        val sharedClientId = SessionClientFixtures.insertTestClient()
        val pendingSessionId = TestFixtures.uuid()
        val noShowSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(pendingSessionId, sharedClientId, day.id)
        SessionClientFixtures.insertTestSession(
            noShowSessionId,
            sharedClientId,
            day.id,
            sessionStatus = SessionStatus.NO_SHOW,
        )

        assertFailsWith<ConflictException> {
            SessionService.updateStatus(callerId, noShowSessionId, SessionStatus.PENDING, 1)
        }
    }

    @Test
    fun `completed status cannot be corrected through status endpoint`() {
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        val completedSessionId = insertSessionWithStatus(SessionStatus.COMPLETED)

        assertFailsWith<ValidationException> {
            SessionService.updateStatus(callerId, completedSessionId, SessionStatus.NO_SHOW, 1)
        }
    }

    @Test
    fun `reopening a session for an anonymized client is rejected`() {
        createSession(callerId, sessionId)
        SessionService.updateStatus(callerId, sessionId, SessionStatus.NO_SHOW, 1)
        ClientService.anonymize(callerId, clientId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)

        assertFailsWith<ConflictException> {
            SessionService.updateStatus(callerId, sessionId, SessionStatus.PENDING, 2)
        }
        assertEquals(SessionStatus.NO_SHOW, SessionRepository.findById(sessionId)?.sessionStatus)
    }

    @Test
    fun `voided session status is frozen`() {
        createSession(callerId, sessionId)
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")

        assertFailsWith<ConflictException> {
            SessionService.updateStatus(callerId, sessionId, SessionStatus.NO_SHOW, 1)
        }
        assertEquals(SessionStatus.PENDING, SessionRepository.findById(sessionId)?.sessionStatus)
    }

    @Test
    fun `unvoided session status becomes editable again`() {
        createSession(callerId, sessionId)
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")
        SessionService.unvoidSession(callerId, sessionId, "Resolved in error")

        val updated = SessionService.updateStatus(callerId, sessionId, SessionStatus.NO_SHOW, 1)

        assertEquals(SessionStatus.NO_SHOW, updated.sessionStatus)
    }

    @Test
    fun `voided pending session does not block replacement pending session`() {
        createSession(callerId, sessionId)
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")

        val replacementId = TestFixtures.uuid()
        val replacement = createSession(callerId, replacementId, clientId = clientId)

        assertEquals(SessionStatus.PENDING, replacement.session.sessionStatus)
    }

    @Test
    fun `voided pending session does not block client anonymization`() {
        createSession(callerId, sessionId)
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")

        ClientService.anonymize(callerId, clientId)

        assertNotNull(
            transaction {
                ClientTable
                    .selectAll()
                    .where { ClientTable.id eq clientId }
                    .single()[ClientTable.deletedAt]
            },
        )
    }

    @Test
    fun `unvoiding pending session conflicts with replacement pending session`() {
        createSession(callerId, sessionId)
        val voidId = TestFixtures.uuid()
        SessionService.voidSession(callerId, sessionId, voidId, "Created in error")

        val replacementId = TestFixtures.uuid()
        createSession(callerId, replacementId, clientId = clientId)

        assertFailsWith<ConflictException> {
            SessionService.unvoidSession(callerId, sessionId, "Resolved in error")
        }
        assertNull(
            transaction {
                SessionVoidTable
                    .selectAll()
                    .where { SessionVoidTable.id eq voidId }
                    .single()[SessionVoidTable.unvoidedAt]
            },
        )
    }

    @Test
    fun `status endpoint rejects illegal completed and no-op transitions`() {
        val illegalEdges =
            listOf(
                SessionStatus.COMPLETED to SessionStatus.CANCELLED,
                SessionStatus.NO_SHOW to SessionStatus.COMPLETED,
                SessionStatus.CANCELLED to SessionStatus.COMPLETED,
                SessionStatus.PENDING to SessionStatus.PENDING,
            )

        illegalEdges.forEach { (from, to) ->
            val sessionId = insertSessionWithStatus(from)

            assertFailsWith<ValidationException> {
                SessionService.updateStatus(callerId, sessionId, to, 1)
            }
        }
    }

    // #149 count-0 misfire pins (deterministic, no interleave — the #136 discipline): the
    // repo's conditional UPDATE must 409 on a stale version itself; a concurrent commit
    // between the service pre-check and the UPDATE would otherwise read back the other
    // writer's row and serve it as this caller's success (a silent lost update).
    @Test
    fun `repo update status with stale version throws 409`() {
        createSession(callerId, sessionId)

        assertFailsWith<ConflictException> {
            transaction {
                SessionRepository.updateStatusInTransaction(sessionId, SessionStatus.COMPLETED, 99)
            }
        }
    }

    @Test
    fun `update status writes audit log entry`() {
        createSession(callerId, sessionId)

        SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, 1)

        assertEquals(2L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    private fun databaseNow() =
        transaction {
            BranchTable
                .select(CurrentTimestampWithTimeZone)
                .first()[CurrentTimestampWithTimeZone]
        }

    private fun correctionDays(): List<Pair<DayStatus, UUID>> {
        val pastDayId =
            BranchWorkforceFixtures.createBranchDayForDate(
                branchId,
                TestFixtures.today.minusDays(1),
            )
        return listOf(
            DayStatus.OPEN to BranchDayService.resolveOrCreate(branchId, TestFixtures.today).id,
            DayStatus.PAST to pastDayId,
            DayStatus.REMITTED to insertRemittedDay(),
        )
    }

    private fun insertSessionWithStatus(status: SessionStatus): UUID {
        val day = BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
        val id = TestFixtures.uuid()
        val sessionClientId = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(id, sessionClientId, day.id, sessionStatus = status)
        return id
    }
}
