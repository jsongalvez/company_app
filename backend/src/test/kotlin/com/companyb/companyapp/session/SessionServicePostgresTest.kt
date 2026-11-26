package com.companyb.companyapp.session
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.client.ClientService
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.session.SessionRepository
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.AttendanceTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class SessionServicePostgresTest : SessionServicePostgresBase() {
    @Test
    fun `create session persists all fields and writes audit`() {
        val (result, duration) = measureTimedValue { createSession(callerId, sessionId) }
        assertTrue(duration < 10.seconds, "session create regressed: took $duration")

        assertTrue(result.created)
        assertEquals(clientId, result.session.clientId)
        assertEquals(SessionType.REGULAR, result.session.sessionType)
        assertEquals(SessionStatus.PENDING, result.session.sessionStatus)
        assertFalse(result.session.isWalkIn)
        assertNull(result.session.requestedPractitionerId)
        assertEquals("2500.00", result.session.basePrice.toPlainString())
        assertEquals("2500.00", result.session.finalPrice.toPlainString())
        assertNotNull(result.session.version)
        assertEquals(1, result.session.version)
        assertEquals(1L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    // --- #366 requested-practitioner revalidation ---
    @Test
    fun `create session accepts requested practitioner who is an active branch member`() {
        val result = createSession(callerId, sessionId, requestedPractitionerId = practitionerId)

        assertTrue(result.created)
        assertEquals(practitionerId, result.session.requestedPractitionerId)
    }

    @Test
    fun `create session rejects requested practitioner with no active membership`() {
        val outsiderId = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(outsiderId, "session-outsider")

        assertFailsWith<ValidationException> {
            createSession(callerId, sessionId, requestedPractitionerId = outsiderId)
        }
    }

    @Test
    fun `create session throws 404 for unknown requested practitioner with no row written`() {
        val unknownPractitionerId = TestFixtures.uuid()
        val blockedId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            createSession(callerId, blockedId, requestedPractitionerId = unknownPractitionerId)
        }
        assertNull(SessionRepository.findById(blockedId))
        assertEquals(0L, auditEntryCount(SessionTable.tableName, blockedId))
    }

    @Test
    fun `create session rejects deactivated requested practitioner`() {
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

        assertFailsWith<ValidationException> {
            createSession(callerId, sessionId, requestedPractitionerId = inactiveId)
        }
    }

    @Test
    fun `create session returns existing on duplicate id`() {
        val first = createSession(callerId, sessionId)
        val duplicate = createSession(callerId, sessionId)

        assertTrue(first.created)
        assertFalse(duplicate.created)
        assertEquals(first.session.id, duplicate.session.id)
        assertNotNull(first.session.bookedAt)
        assertEquals(first.session.bookedAt, duplicate.session.bookedAt)
        assertEquals(1L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `create session rejects duplicate id from another branch`() {
        val otherBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(otherBranchId)

        createSession(callerId, sessionId)

        assertFailsWith<ConflictException> {
            createSession(callerId, sessionId, branchId = otherBranchId)
        }
        assertEquals(1L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `create session rejects duplicate id for another client`() {
        createSession(callerId, sessionId)

        assertFailsWith<ConflictException> {
            createSession(callerId, sessionId, clientId = TestFixtures.uuid())
        }
        assertEquals(1L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `create session rejects duplicate id from another caller`() {
        createSession(callerId, sessionId)

        assertFailsWith<ConflictException> {
            createSession(TestFixtures.uuid(), sessionId)
        }
        assertEquals(1L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `idempotent replay classifies by stored creator without audit history`() {
        createSession(callerId, sessionId)
        transaction { AuditLogTable.deleteWhere { AuditLogTable.recordId eq sessionId } }

        val replay = createSession(callerId, sessionId)

        assertFalse(replay.created)
        assertEquals(callerId, replay.session.createdBy)
        assertFailsWith<ConflictException> {
            createSession(TestFixtures.uuid(), sessionId)
        }
    }

    @Test
    fun `create session rejects duplicate id for another branch day`() {
        createSession(callerId, sessionId)

        assertFailsWith<ConflictException> {
            createSession(callerId, sessionId, gatedBranchDayId = TestFixtures.uuid())
        }
        assertEquals(1L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `create session rejects concurrent pending session for same client`() {
        createSession(callerId, sessionId)
        val secondSessionId = TestFixtures.uuid()

        val error =
            assertFailsWith<ConflictException> {
                createSession(callerId, secondSessionId)
            }
        assertEquals(
            "This client is already waiting at Test Branch ${branchId.toString().take(
                8,
            )} on this day. Mark that session first.",
            error.message,
        )
    }

    @Test
    fun `a session left waiting on an earlier day does not block adding the client today`() {
        val yesterday = BranchDayService.resolveOrCreate(branchId, TestFixtures.today.minusDays(1))
        SessionClientFixtures.insertTestSession(TestFixtures.uuid(), clientId, yesterday.id)

        val result = createSession(callerId, sessionId)

        assertEquals(SessionStatus.PENDING, result.session.sessionStatus)
    }

    @Test
    fun `an earlier day's session can go back to waiting while the client waits today`() {
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId)
        val yesterday = BranchDayService.resolveOrCreate(branchId, TestFixtures.today.minusDays(1))
        val earlierId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            earlierId,
            clientId,
            yesterday.id,
            sessionStatus = SessionStatus.NO_SHOW,
        )
        createSession(callerId, sessionId)

        val updated = SessionService.updateStatus(callerId, earlierId, SessionStatus.PENDING, 1, "entered late")

        assertEquals(SessionStatus.PENDING, updated.sessionStatus)
    }

    // #751 — history reads join the command transaction after the client-row lock,
    // so sequential creates observe committed priors (no stale priorCount=0).
    @Test
    fun `sequential creates walk the type ladder REGULAR to SECOND_SESSION to SUBSEQUENT`() {
        val firstId = TestFixtures.uuid()
        val first = createSession(callerId, firstId)
        assertEquals(SessionType.REGULAR, first.session.sessionType)
        SessionService.updateStatus(callerId, firstId, SessionStatus.COMPLETED, 1)

        val secondId = TestFixtures.uuid()
        val second = createSession(callerId, secondId)
        assertEquals(SessionType.SECOND_SESSION, second.session.sessionType)
        SessionService.updateStatus(callerId, secondId, SessionStatus.COMPLETED, 1)

        val thirdId = TestFixtures.uuid()
        val third = createSession(callerId, thirdId)
        assertEquals(SessionType.SUBSEQUENT, third.session.sessionType)
    }

    // #751 — voided sessions stay excluded from the in-transaction prior count.
    @Test
    fun `voided sessions do not count toward prior session type`() {
        val firstId = TestFixtures.uuid()
        createSession(callerId, firstId)
        SessionService.voidSession(callerId, firstId, TestFixtures.uuid(), "Created in error")

        val secondId = TestFixtures.uuid()
        val second = createSession(callerId, secondId)

        assertEquals(SessionType.REGULAR, second.session.sessionType)
    }

    @Test
    fun `create session for anonymized client throws conflict`() {
        ClientService.anonymize(callerId, clientId)

        assertFailsWith<ConflictException> {
            createSession(callerId, sessionId)
        }
    }

    @Test
    fun `create session without EDIT_BRANCH_DATA is allowed at service layer`() {
        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "session-other")

        val result = createSession(otherCaller, sessionId)

        assertTrue(result.created)
    }

    @Test
    fun `create session throws 404 for non-existent branch`() {
        val unknownBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            SessionService.create(
                callerId = callerId,
                NewSession(
                    id = sessionId,
                    clientId = clientId,
                    branchId = unknownBranchId,
                    isWalkIn = false,
                    requestedPractitionerId = null,
                    finalPrice = BigDecimal("2500.00"),
                    remarks = null,
                    otherConcerns = null,
                    nextAppointmentDate = null,
                ),
                context =
                    SessionCreateContext(requireClockIn = false),
            )
        }
    }

    @Test
    fun `create session throws 404 for unknown client with no row written`() {
        val unknownClientId = TestFixtures.uuid()
        val blockedId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            createSession(callerId, blockedId, clientId = unknownClientId)
        }
        assertNull(SessionRepository.findById(blockedId))
        assertEquals(0L, auditEntryCount(SessionTable.tableName, blockedId))
    }

    // #924 persisted-range pre-gate (the #912/#921/#923 fail-closed class):
    // NUMERIC(10,2) overflow must 400 before the write, never 500 from Postgres.
    @Test
    fun `create session rejects finalPrice over NUMERIC(10,2) range with no row written`() {
        val blockedId = TestFixtures.uuid()

        assertFailsWith<ValidationException> {
            createSession(callerId, blockedId, finalPrice = BigDecimal("9999999999.99"))
        }
        assertNull(SessionRepository.findById(blockedId))
        assertEquals(0L, auditEntryCount(SessionTable.tableName, blockedId))
    }

    @Test
    fun `create session persists boundary max money amount`() {
        val result = createSession(callerId, sessionId, finalPrice = BigDecimal("99999999.99"))

        assertTrue(result.created)
        assertEquals("99999999.99", result.session.finalPrice.toPlainString())
    }

    @Test
    fun `create fails closed without a clock-in unless a caller opts out`() {
        fun create(id: UUID) =
            SessionService.create(
                callerId = callerId,
                NewSession(
                    id = id,
                    clientId = clientId,
                    branchId = branchId,
                    isWalkIn = false,
                    requestedPractitionerId = null,
                    finalPrice = BigDecimal("2500.00"),
                    remarks = null,
                    otherConcerns = null,
                    nextAppointmentDate = null,
                ),
            )

        val ex = assertFailsWith<ForbiddenException> { create(TestFixtures.uuid()) }
        assertEquals("Clock in at this branch first", ex.message)

        val today = BranchDayService.resolveOrCreate(branchId, BranchDayService.currentOperationalDate())
        transaction {
            AttendanceTable.insert {
                it[AttendanceTable.id] = TestFixtures.uuid()
                it[AttendanceTable.branchDayId] = today.id
                it[AttendanceTable.userId] = callerId
                it[AttendanceTable.markedBy] = callerId
            }
        }
        assertTrue(create(TestFixtures.uuid()).created)
    }
}
