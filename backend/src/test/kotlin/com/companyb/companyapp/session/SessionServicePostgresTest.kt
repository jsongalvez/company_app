package com.companyb.companyapp.session
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.client.ClientService
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.identity.UserStatus
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.integration.LockBarrier
import com.companyb.companyapp.session.SessionPractitionerRepository
import com.companyb.companyapp.session.SessionPractitionerService
import com.companyb.companyapp.session.SessionPractitionerTable
import com.companyb.companyapp.session.SessionRepository
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.session.SessionVoidTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import com.companyb.companyapp.workforce.UserBranchAssignmentCreateParams
import com.companyb.companyapp.workforce.UserBranchAssignmentRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
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

// #593 1335-line scenario coverage stays whole (test-class precedent: RemittanceServicePostgresTest).
@Suppress("LargeClass") // #593
class SessionServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val rateId = TestFixtures.uuid()
    private val secondSessionRateId = TestFixtures.uuid()
    private val subsequentRateId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val walkInSessionId = TestFixtures.uuid()
    private val practitionerId = TestFixtures.uuid()
    private val practitionerSessionId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "session-caller")
        IdentityFixtures.insertTestUser(practitionerId, "session-practitioner")

        BranchWorkforceFixtures.insertTestBranch(branchId)

        SessionClientFixtures.insertTestClient(clientId)

        IdentityFixtures.grantEditBranchData(callerId, sourceId)
        IdentityFixtures.grantVoidSession(callerId, sourceId)

        SessionClientFixtures.insertTestBaseRate(rateId, branchId, callerId)
        SessionClientFixtures.insertTestBaseRate(
            secondSessionRateId,
            branchId,
            callerId,
            SessionType.SECOND_SESSION,
        )
        SessionClientFixtures.insertTestBaseRate(
            subsequentRateId,
            branchId,
            callerId,
            SessionType.SUBSEQUENT,
        )

        insertAssignment(practitionerId)
    }

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

        assertFailsWith<ConflictException> {
            createSession(callerId, secondSessionId)
        }
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
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
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
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        val result = createSession(callerId, sessionId, reason = "Late walk-in after remittance")

        assertTrue(result.created)
        val audit = auditEntry(SessionTable.tableName, sessionId)
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Late walk-in after remittance", audit[AuditLogTable.reason])
    }

    @Test
    fun `create on PAST day requires coordinator and needs no reason`() {
        val pastDayId =
            BranchWorkforceFixtures.createBranchDayForDate(
                branchId,
                TestFixtures.today.minusDays(1),
            )
        val blockedId = TestFixtures.uuid()

        assertFailsWith<ForbiddenException> {
            createSession(callerId, blockedId, gatedBranchDayId = pastDayId)
        }
        assertNull(SessionRepository.findById(blockedId))

        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val result = createSession(callerId, blockedId, gatedBranchDayId = pastDayId)

        assertTrue(result.created)
        val audit = auditEntry(SessionTable.tableName, blockedId)
        assertEquals(false, audit[AuditLogTable.isFlagged])
        assertNull(audit[AuditLogTable.reason])
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
    fun `create session throws 404 for non-existent branch`() {
        val unknownBranchId = TestFixtures.uuid()

        assertFailsWith<NotFoundException> {
            SessionService.create(
                callerId = callerId,
                id = sessionId,
                clientId = clientId,
                branchId = unknownBranchId,
                isWalkIn = false,
                requestedPractitionerId = null,
                finalPrice = BigDecimal("2500.00"),
                remarks = null,
                otherConcerns = null,
                nextAppointmentDate = null,
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

    @Test
    fun `medical mission branch always creates MEDICAL_MISSION session type`() {
        val mmBranchId = TestFixtures.uuid()
        val mmClientId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(mmBranchId, branchType = BranchType.MEDICAL_MISSION)
        SessionClientFixtures.insertTestClient(mmClientId)
        val mmSessionId = TestFixtures.uuid()
        val mmRateId = TestFixtures.uuid()
        SessionClientFixtures.insertTestBaseRate(
            mmRateId,
            mmBranchId,
            callerId,
            SessionType.MEDICAL_MISSION,
        )

        val result =
            SessionService.create(
                callerId = callerId,
                id = mmSessionId,
                clientId = mmClientId,
                branchId = mmBranchId,
                isWalkIn = false,
                requestedPractitionerId = null,
                finalPrice = BigDecimal.ZERO,
                remarks = null,
                otherConcerns = null,
                nextAppointmentDate = null,
            )

        assertEquals(SessionType.MEDICAL_MISSION, result.session.sessionType)
    }

    @Test
    fun `medical mission branch type not counted toward prior sessions`() {
        val mmBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(mmBranchId, branchType = BranchType.MEDICAL_MISSION)
        val mmRateId = TestFixtures.uuid()
        SessionClientFixtures.insertTestBaseRate(
            mmRateId,
            mmBranchId,
            callerId,
            SessionType.MEDICAL_MISSION,
        )
        val mmSessionId = TestFixtures.uuid()

        val mmResult =
            SessionService.create(
                callerId = callerId,
                id = mmSessionId,
                clientId = clientId,
                branchId = mmBranchId,
                isWalkIn = false,
                requestedPractitionerId = null,
                finalPrice = BigDecimal.ZERO,
                remarks = null,
                otherConcerns = null,
                nextAppointmentDate = null,
            )

        assertEquals(SessionType.MEDICAL_MISSION, mmResult.session.sessionType)

        // Complete the MM session in DB to allow creating a clinic session for same client
        transaction {
            SessionTable.update({ SessionTable.id eq mmSessionId }) {
                it[SessionTable.sessionStatus] = SessionStatus.COMPLETED
            }
        }

        val clinicSessionId = TestFixtures.uuid()
        val clinicResult = createSession(callerId, clinicSessionId)

        assertEquals(SessionType.REGULAR, clinicResult.session.sessionType)
    }

    @Test
    fun `medical mission create normalizes non-zero price to zero`() {
        val mmBranchId = TestFixtures.uuid()
        val mmClientId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(mmBranchId, branchType = BranchType.MEDICAL_MISSION)
        SessionClientFixtures.insertTestClient(mmClientId)
        val mmRateId = TestFixtures.uuid()
        SessionClientFixtures.insertTestBaseRate(
            mmRateId,
            mmBranchId,
            callerId,
            SessionType.MEDICAL_MISSION,
        )
        val mmSessionId = TestFixtures.uuid()

        // #405 — the caller sends a full clinic price; the invariant (BR §Session types)
        // normalizes it to ₱0 on the server.
        val result =
            createSession(
                callerId,
                mmSessionId,
                clientId = mmClientId,
                branchId = mmBranchId,
                finalPrice = BigDecimal("2500.00"),
            )

        assertEquals(SessionType.MEDICAL_MISSION, result.session.sessionType)
        assertEquals("0.00", result.session.finalPrice.toPlainString())
    }

    @Test
    fun `medical mission update final price normalizes non-zero price to zero`() {
        val mmBranchId = TestFixtures.uuid()
        val mmClientId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(mmBranchId, branchType = BranchType.MEDICAL_MISSION)
        SessionClientFixtures.insertTestClient(mmClientId)
        val mmRateId = TestFixtures.uuid()
        SessionClientFixtures.insertTestBaseRate(
            mmRateId,
            mmBranchId,
            callerId,
            SessionType.MEDICAL_MISSION,
        )
        val mmSessionId = TestFixtures.uuid()

        createSession(callerId, mmSessionId, clientId = mmClientId, branchId = mmBranchId, finalPrice = BigDecimal.ZERO)

        // #405 — a later price edit cannot give a mission session a price either.
        val updated =
            SessionService.updateFinalPrice(callerId, mmSessionId, BigDecimal("2750.00"), expectedVersion = 1)

        assertEquals("0.00", updated.finalPrice.toPlainString())
        assertEquals(2, updated.version)
    }

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
    fun `booked status corrections require Coordinator authority`() {
        val correctionSessionId = insertSessionWithStatus(SessionStatus.NO_SHOW)

        assertFailsWith<ForbiddenException> {
            SessionService.updateStatus(callerId, correctionSessionId, SessionStatus.PENDING, 1)
        }
    }

    @Test
    fun `booked corrections require Coordinator authority across every day state`() {
        correctionDays().forEach { (_, dayId) ->
            listOf(
                SessionStatus.NO_SHOW to SessionStatus.PENDING,
                SessionStatus.NO_SHOW to SessionStatus.CANCELLED,
                SessionStatus.CANCELLED to SessionStatus.PENDING,
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
    fun `Coordinator can apply every booked correction edge and audit status before and after`() {
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        val edges =
            listOf(
                SessionStatus.NO_SHOW to SessionStatus.PENDING,
                SessionStatus.NO_SHOW to SessionStatus.CANCELLED,
                SessionStatus.CANCELLED to SessionStatus.PENDING,
                SessionStatus.CANCELLED to SessionStatus.NO_SHOW,
            )

        correctionDays().forEach { (dayStatus, dayId) ->
            edges.forEach { (from, to) ->
                val correctionSessionId = TestFixtures.uuid()
                insertSessionOnDay(correctionSessionId, dayId, from)
                val reason = if (dayStatus == DayStatus.REMITTED) "Corrected attendance mark" else null
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
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
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
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val completedSessionId = insertSessionWithStatus(SessionStatus.COMPLETED)

        assertFailsWith<ValidationException> {
            SessionService.updateStatus(callerId, completedSessionId, SessionStatus.PENDING, 1)
        }
    }

    @Test
    fun `reopening a session for an anonymized client is rejected`() {
        createSession(callerId, sessionId)
        SessionService.updateStatus(callerId, sessionId, SessionStatus.NO_SHOW, 1)
        ClientService.anonymize(callerId, clientId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

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
                SessionStatus.COMPLETED to SessionStatus.PENDING,
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

    @Test
    fun `past-day correction remains Coordinator-only`() {
        val pastDayId =
            BranchWorkforceFixtures.createBranchDayForDate(
                branchId,
                TestFixtures.today.minusDays(1),
            )
        val pastSessionId = TestFixtures.uuid()
        insertSessionOnDay(pastSessionId, pastDayId, SessionStatus.NO_SHOW)

        assertFailsWith<ForbiddenException> {
            SessionService.updateStatus(callerId, pastSessionId, SessionStatus.PENDING, 1)
        }

        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)
        val updated = SessionService.updateStatus(callerId, pastSessionId, SessionStatus.PENDING, 1)

        assertEquals(SessionStatus.PENDING, updated.sessionStatus)
    }

    @Test
    fun `remitted correction requires reason and audits flagged status change`() {
        val remittedDayId = insertRemittedDay()
        val remittedSessionId = TestFixtures.uuid()
        insertSessionOnDay(remittedSessionId, remittedDayId, SessionStatus.NO_SHOW)

        assertFailsWith<ForbiddenException> {
            SessionService.updateStatus(callerId, remittedSessionId, SessionStatus.PENDING, 1)
        }

        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

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
    fun `update final price succeeds and increments version`() {
        createSession(callerId, sessionId)

        val updated = SessionService.updateFinalPrice(callerId, sessionId, BigDecimal("2750.00"), 1)

        assertEquals("2750.00", updated.finalPrice.toPlainString())
        assertEquals(2, updated.version)
        assertEquals(2L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `update final price with wrong version throws 409`() {
        createSession(callerId, sessionId)

        assertFailsWith<ConflictException> {
            SessionService.updateFinalPrice(callerId, sessionId, BigDecimal("2750.00"), 99)
        }
    }

    @Test
    fun `update final price to zero succeeds`() {
        createSession(callerId, sessionId)

        val updated = SessionService.updateFinalPrice(callerId, sessionId, BigDecimal.ZERO, 1)

        assertEquals("0.00", updated.finalPrice.toPlainString())
        assertEquals(2, updated.version)
    }

    @Test
    fun `update final price for non-existent session throws 404`() {
        assertFailsWith<NotFoundException> {
            SessionService.updateFinalPrice(callerId, TestFixtures.uuid(), BigDecimal("2750.00"), 1)
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
    fun `repo update final price with stale version throws 409`() {
        createSession(callerId, sessionId)

        assertFailsWith<ConflictException> {
            transaction {
                SessionRepository.updateFinalPriceInTransaction(sessionId, BigDecimal("2750.00"), 99)
            }
        }
    }

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

    @Test
    fun `update status writes audit log entry`() {
        createSession(callerId, sessionId)

        SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, 1)

        assertEquals(2L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `update status on REMITTED day without reason is rejected`() {
        val remittedDayId = insertRemittedDay()
        val remittedSessionId = TestFixtures.uuid()
        insertSessionOnDay(remittedSessionId, remittedDayId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        assertFailsWith<ValidationException> {
            SessionService.updateStatus(callerId, remittedSessionId, SessionStatus.COMPLETED, 1)
        }
    }

    @Test
    fun `update status on REMITTED day with reason succeeds and flags audit entry with reason`() {
        val remittedDayId = insertRemittedDay()
        val remittedSessionId = TestFixtures.uuid()
        insertSessionOnDay(remittedSessionId, remittedDayId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

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
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        val voidId = TestFixtures.uuid()
        val result = SessionService.voidSession(callerId, remittedSessionId, voidId, "Customer request")

        assertNotNull(result.sessionVoid)
        val audit = auditEntry(SessionVoidTable.tableName, voidId)
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Customer request", audit[AuditLogTable.reason])
    }

    @Test
    fun `add practitioner snapshots slot and writes audit`() {
        createSession(callerId, practitionerSessionId)
        val requestId = TestFixtures.uuid()

        val result =
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                id = requestId,
                sessionId = practitionerSessionId,
                practitionerId = practitionerId,
                remarks = "Test remarks",
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
                id = requestId,
                sessionId = practitionerSessionId,
                practitionerId = practitionerId,
                remarks = null,
            )
        val duplicate =
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                id = requestId,
                sessionId = practitionerSessionId,
                practitionerId = practitionerId,
                remarks = null,
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
                id = TestFixtures.uuid(),
                sessionId = practitionerSessionId,
                practitionerId = practitionerId,
                remarks = null,
            )

        assertTrue(result.created)
    }

    @Test
    fun `add practitioner throws 404 for non-existent session`() {
        assertFailsWith<NotFoundException> {
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                id = TestFixtures.uuid(),
                sessionId = TestFixtures.uuid(),
                practitionerId = practitionerId,
                remarks = null,
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
                id = requestId,
                sessionId = practitionerSessionId,
                practitionerId = unknownPractitionerId,
                remarks = null,
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

    @Test
    fun `update practitioner remarks succeeds and increments session version and writes audit`() {
        createSession(callerId, practitionerSessionId)
        val practitionerEntityId = TestFixtures.uuid()
        SessionPractitionerService.addPractitioner(
            callerId = callerId,
            id = practitionerEntityId,
            sessionId = practitionerSessionId,
            practitionerId = practitionerId,
            remarks = "Initial remarks",
        )

        val updated =
            SessionPractitionerService.updatePractitionerRemarks(
                callerId = callerId,
                sessionId = practitionerSessionId,
                practitionerId = practitionerId,
                remarks = "Updated remarks",
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
                remarks = "Some remarks",
            )
        }
    }

    @Test
    fun `remove practitioner succeeds and increments session version and writes audit`() {
        createSession(callerId, practitionerSessionId)
        val practitionerEntityId = TestFixtures.uuid()
        SessionPractitionerService.addPractitioner(
            callerId = callerId,
            id = practitionerEntityId,
            sessionId = practitionerSessionId,
            practitionerId = practitionerId,
            remarks = null,
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

    // #593 helper mirrors SessionService.create signature (fixture parity, #552 precedent).
    @Suppress("LongParameterList") // #593
    private fun createSession(
        callerId: UUID,
        id: UUID,
        clientId: UUID = this.clientId,
        branchId: UUID = this.branchId,
        isWalkIn: Boolean = false,
        requestedPractitionerId: UUID? = null,
        finalPrice: BigDecimal = BigDecimal("2500.00"),
        gatedBranchDayId: UUID? = null,
        nextAppointmentDate: LocalDate? = null,
        reason: String? = null,
    ) = SessionService.create(
        callerId = callerId,
        id = id,
        clientId = clientId,
        branchId = branchId,
        isWalkIn = isWalkIn,
        requestedPractitionerId = requestedPractitionerId,
        finalPrice = finalPrice,
        remarks = "Test session",
        otherConcerns = null,
        nextAppointmentDate = nextAppointmentDate,
        gatedBranchDayId = gatedBranchDayId,
        reason = reason,
    )

    private fun databaseNow() =
        transaction {
            BranchTable
                .select(CurrentTimestampWithTimeZone)
                .first()[CurrentTimestampWithTimeZone]
        }

    private fun insertAssignment(userId: UUID) {
        transaction {
            UserBranchAssignmentRepository.createInTransaction(
                UserBranchAssignmentCreateParams(
                    id = TestFixtures.uuid(),
                    userId = userId,
                    branchId = branchId,
                    slot = 1,
                    assignedBy = callerId,
                ),
            )
        }
    }

    private fun auditEntryCount(
        tableName: String,
        recordId: UUID,
        actionFilter: AuditAction? = null,
    ): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq tableName) and
                        (AuditLogTable.recordId eq recordId) and
                        (
                            actionFilter?.let { AuditLogTable.action eq it }
                                ?: AuditLogTable.auditTableName.isNotNull()
                        )
                }.count()
        }

    private fun insertRemittedDay(): UUID {
        val dayId =
            BranchWorkforceFixtures.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        return dayId
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

    private fun insertSessionOnDay(
        id: UUID,
        dayId: UUID,
        sessionStatus: SessionStatus = SessionStatus.PENDING,
    ) {
        val sessionClientId = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(id, sessionClientId, dayId, sessionStatus = sessionStatus)
    }

    private fun insertSessionWithStatus(status: SessionStatus): UUID {
        val day = BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
        val id = TestFixtures.uuid()
        val sessionClientId = SessionClientFixtures.insertTestClient()
        SessionClientFixtures.insertTestSession(id, sessionClientId, day.id, sessionStatus = status)
        return id
    }

    private fun auditEntry(
        tableName: String,
        recordId: UUID,
    ) = transaction {
        AuditLogTable
            .selectAll()
            .where {
                (AuditLogTable.auditTableName eq tableName) and
                    (AuditLogTable.recordId eq recordId)
            }.single()
    }
}
