package com.companyb.companyapp.service
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.domain.UserStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.repository.SessionPractitionerRepository
import com.companyb.companyapp.repository.SessionRepository
import com.companyb.companyapp.repository.UserBranchAssignmentRepository
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.SessionBaseRateTable
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentCreateParams
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.session.SessionService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
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

@Suppress("LargeClass")
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
        DatabaseTestHelper.insertTestUser(callerId, "session-caller")
        DatabaseTestHelper.insertTestUser(practitionerId, "session-practitioner")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        trackOwned(AppUserTable, AppUserTable.id, practitionerId)

        DatabaseTestHelper.insertTestBranch(branchId)
        trackOwned(BranchTable, BranchTable.id, branchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)

        DatabaseTestHelper.insertTestClient(clientId)
        trackOwned(ClientTable, ClientTable.id, clientId)

        DatabaseTestHelper.grantEditBranchData(callerId, sourceId)
        DatabaseTestHelper.grantVoidSession(callerId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        insertSessionBaseRate()
        insertSessionBaseRate(id = secondSessionRateId, sessionType = SessionType.SECOND_SESSION)
        insertSessionBaseRate(id = subsequentRateId, sessionType = SessionType.SUBSEQUENT)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, rateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, secondSessionRateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, subsequentRateId)

        insertAssignment(practitionerId)
        trackOwned(UserBranchAssignmentTable, UserBranchAssignmentTable.branchId, branchId)

        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, practitionerId)
    }

    @Test
    fun `create session persists all fields and writes audit`() {
        val (result, duration) = measureTimedValue { createSession(callerId, sessionId) }
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
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
        trackOwned(SessionTable, SessionTable.id, sessionId)

        assertTrue(result.created)
        assertEquals(practitionerId, result.session.requestedPractitionerId)
    }

    @Test
    fun `create session rejects requested practitioner with no active membership`() {
        assertFailsWith<ValidationException> {
            createSession(callerId, sessionId, requestedPractitionerId = TestFixtures.uuid())
        }
    }

    @Test
    fun `create session rejects deactivated requested practitioner`() {
        val inactiveId = TestFixtures.uuid()
        DatabaseTestHelper.insertUser(
            id = inactiveId,
            username = "inactive-${inactiveId.toString().take(8)}",
            passwordHash = "test-password-hash",
            email = "${inactiveId.toString().take(8)}@t.st",
            displayName = "Test inactive",
            status = UserStatus.INACTIVE,
        )
        trackOwned(AppUserTable, AppUserTable.id, inactiveId)
        insertAssignment(inactiveId)

        assertFailsWith<ValidationException> {
            createSession(callerId, sessionId, requestedPractitionerId = inactiveId)
        }
    }

    @Test
    fun `create session returns existing on duplicate id`() {
        val first = createSession(callerId, sessionId)
        val duplicate = createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

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
        DatabaseTestHelper.insertTestBranch(otherBranchId)
        trackOwned(BranchTable, BranchTable.id, otherBranchId)

        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        assertFailsWith<ConflictException> {
            createSession(callerId, sessionId, branchId = otherBranchId)
        }
        assertEquals(1L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `create session rejects duplicate id for another client`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        assertFailsWith<ConflictException> {
            createSession(callerId, sessionId, clientId = TestFixtures.uuid())
        }
        assertEquals(1L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `create session rejects duplicate id from another caller`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        assertFailsWith<ConflictException> {
            createSession(TestFixtures.uuid(), sessionId)
        }
        assertEquals(1L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `idempotent replay classifies by stored creator without audit history`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
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
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        assertFailsWith<ConflictException> {
            createSession(callerId, sessionId, gatedBranchDayId = TestFixtures.uuid())
        }
        assertEquals(1L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `create session rejects concurrent pending session for same client`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        val secondSessionId = TestFixtures.uuid()

        assertFailsWith<ConflictException> {
            createSession(callerId, secondSessionId)
        }
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
        DatabaseTestHelper.insertTestUser(otherCaller, "session-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)

        val result = createSession(otherCaller, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        assertTrue(result.created)
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
    fun `medical mission branch always creates MEDICAL_MISSION session type`() {
        val mmBranchId = TestFixtures.uuid()
        val mmClientId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(mmBranchId, branchType = BranchType.MEDICAL_MISSION)
        trackOwned(BranchTable, BranchTable.id, mmBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, mmBranchId)
        DatabaseTestHelper.insertTestClient(mmClientId)
        trackOwned(ClientTable, ClientTable.id, mmClientId)
        val mmSessionId = TestFixtures.uuid()
        val mmRateId = TestFixtures.uuid()
        insertSessionBaseRate(mmRateId, mmBranchId, SessionType.MEDICAL_MISSION)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, mmRateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.setBy, callerId)

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
        trackOwned(SessionTable, SessionTable.id, mmSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, mmSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, mmSessionId)

        assertEquals(SessionType.MEDICAL_MISSION, result.session.sessionType)
    }

    @Test
    fun `medical mission branch type not counted toward prior sessions`() {
        val mmBranchId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(mmBranchId, branchType = BranchType.MEDICAL_MISSION)
        trackOwned(BranchTable, BranchTable.id, mmBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, mmBranchId)
        val mmRateId = TestFixtures.uuid()
        insertSessionBaseRate(mmRateId, mmBranchId, SessionType.MEDICAL_MISSION)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, mmRateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.setBy, callerId)
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
        trackOwned(SessionTable, SessionTable.id, mmSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, mmSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, mmSessionId)

        assertEquals(SessionType.MEDICAL_MISSION, mmResult.session.sessionType)

        // Complete the MM session in DB to allow creating a clinic session for same client
        transaction {
            SessionTable.update({ SessionTable.id eq mmSessionId }) {
                it[SessionTable.sessionStatus] = SessionStatus.COMPLETED
            }
        }

        val clinicSessionId = TestFixtures.uuid()
        val clinicResult = createSession(callerId, clinicSessionId)
        trackOwned(SessionTable, SessionTable.id, clinicSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, clinicSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, clinicSessionId)

        assertEquals(SessionType.REGULAR, clinicResult.session.sessionType)
    }

    @Test
    fun `medical mission create normalizes non-zero price to zero`() {
        val mmBranchId = TestFixtures.uuid()
        val mmClientId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(mmBranchId, branchType = BranchType.MEDICAL_MISSION)
        trackOwned(BranchTable, BranchTable.id, mmBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, mmBranchId)
        DatabaseTestHelper.insertTestClient(mmClientId)
        trackOwned(ClientTable, ClientTable.id, mmClientId)
        val mmRateId = TestFixtures.uuid()
        insertSessionBaseRate(mmRateId, mmBranchId, SessionType.MEDICAL_MISSION)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, mmRateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.setBy, callerId)
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
        trackOwned(SessionTable, SessionTable.id, mmSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, mmSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, mmSessionId)

        assertEquals(SessionType.MEDICAL_MISSION, result.session.sessionType)
        assertEquals("0.00", result.session.finalPrice.toPlainString())
    }

    @Test
    fun `medical mission update final price normalizes non-zero price to zero`() {
        val mmBranchId = TestFixtures.uuid()
        val mmClientId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(mmBranchId, branchType = BranchType.MEDICAL_MISSION)
        trackOwned(BranchTable, BranchTable.id, mmBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, mmBranchId)
        DatabaseTestHelper.insertTestClient(mmClientId)
        trackOwned(ClientTable, ClientTable.id, mmClientId)
        val mmRateId = TestFixtures.uuid()
        insertSessionBaseRate(mmRateId, mmBranchId, SessionType.MEDICAL_MISSION)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, mmRateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.setBy, callerId)
        val mmSessionId = TestFixtures.uuid()

        createSession(callerId, mmSessionId, clientId = mmClientId, branchId = mmBranchId, finalPrice = BigDecimal.ZERO)
        trackOwned(SessionTable, SessionTable.id, mmSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, mmSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, mmSessionId)

        // #405 — a later price edit cannot give a mission session a price either.
        val updated =
            SessionService.updateFinalPrice(callerId, mmSessionId, BigDecimal("2750.00"), expectedVersion = 1)

        assertEquals("0.00", updated.finalPrice.toPlainString())
        assertEquals(2, updated.version)
    }

    @Test
    fun `update status from PENDING to COMPLETED succeeds and increments version`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        val updated = SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, 1)

        assertEquals(SessionStatus.COMPLETED, updated.sessionStatus)
        assertEquals(2, updated.version)
        assertEquals(2L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `update status with wrong version throws 409`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

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
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        val otherCaller = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherCaller, "session-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)

        val updated = SessionService.updateStatus(otherCaller, sessionId, SessionStatus.COMPLETED, 1)

        assertEquals(SessionStatus.COMPLETED, updated.sessionStatus)
        assertEquals(2, updated.version)
    }

    @Test
    fun `walk-in session cannot transition to NO_SHOW`() {
        createSession(callerId, walkInSessionId, isWalkIn = true)
        trackOwned(SessionTable, SessionTable.id, walkInSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, walkInSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, walkInSessionId)

        assertFailsWith<ValidationException> {
            SessionService.updateStatus(callerId, walkInSessionId, SessionStatus.NO_SHOW, 1)
        }
    }

    @Test
    fun `walk-in session cannot transition to CANCELLED`() {
        createSession(callerId, walkInSessionId, isWalkIn = true)
        trackOwned(SessionTable, SessionTable.id, walkInSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, walkInSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, walkInSessionId)

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
        trackOwned(SessionTable, SessionTable.id, bookedSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, bookedSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, bookedSessionId)

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
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)

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
                    DatabaseTestHelper.extractJsonField(audit[AuditLogTable.oldValue].orEmpty(), "sessionStatus"),
                )
                assertEquals(
                    to.name,
                    DatabaseTestHelper.extractJsonField(audit[AuditLogTable.newValue].orEmpty(), "sessionStatus"),
                )
            }
        }
    }

    @Test
    fun `reopening session conflicts when client already has a pending session`() {
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        val day = BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
        val sharedClientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, sharedClientId)
        val pendingSessionId = TestFixtures.uuid()
        val noShowSessionId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestSession(pendingSessionId, sharedClientId, day.id)
        DatabaseTestHelper.insertTestSession(
            noShowSessionId,
            sharedClientId,
            day.id,
            sessionStatus = SessionStatus.NO_SHOW,
        )
        trackOwned(SessionTable, SessionTable.id, pendingSessionId)
        trackOwned(SessionTable, SessionTable.id, noShowSessionId)

        assertFailsWith<ConflictException> {
            SessionService.updateStatus(callerId, noShowSessionId, SessionStatus.PENDING, 1)
        }
    }

    @Test
    fun `completed status cannot be corrected through status endpoint`() {
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        val completedSessionId = insertSessionWithStatus(SessionStatus.COMPLETED)

        assertFailsWith<ValidationException> {
            SessionService.updateStatus(callerId, completedSessionId, SessionStatus.PENDING, 1)
        }
    }

    @Test
    fun `reopening a session for an anonymized client is rejected`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        SessionService.updateStatus(callerId, sessionId, SessionStatus.NO_SHOW, 1)
        ClientService.anonymize(callerId, clientId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)

        assertFailsWith<ConflictException> {
            SessionService.updateStatus(callerId, sessionId, SessionStatus.PENDING, 2)
        }
        assertEquals(SessionStatus.NO_SHOW, SessionRepository.findById(sessionId)?.sessionStatus)
    }

    @Test
    fun `voided session status remains editable`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")

        val updated = SessionService.updateStatus(callerId, sessionId, SessionStatus.NO_SHOW, 1)

        assertEquals(SessionStatus.NO_SHOW, updated.sessionStatus)
    }

    @Test
    fun `voided pending session does not block replacement pending session`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Created in error")

        val replacementId = TestFixtures.uuid()
        val replacement = createSession(callerId, replacementId, clientId = clientId)
        trackOwned(SessionTable, SessionTable.id, replacementId)

        assertEquals(SessionStatus.PENDING, replacement.session.sessionStatus)
    }

    @Test
    fun `voided pending session does not block client anonymization`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
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
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        val voidId = TestFixtures.uuid()
        SessionService.voidSession(callerId, sessionId, voidId, "Created in error")

        val replacementId = TestFixtures.uuid()
        createSession(callerId, replacementId, clientId = clientId)
        trackOwned(SessionTable, SessionTable.id, replacementId)

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
            DatabaseTestHelper.createBranchDayForDate(
                branchId,
                TestFixtures.today.minusDays(1),
            )
        trackOwned(BranchDayTable, BranchDayTable.id, pastDayId)
        val pastSessionId = TestFixtures.uuid()
        insertSessionOnDay(pastSessionId, pastDayId, SessionStatus.NO_SHOW)

        assertFailsWith<ForbiddenException> {
            SessionService.updateStatus(callerId, pastSessionId, SessionStatus.PENDING, 1)
        }

        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
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

        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)

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
            DatabaseTestHelper.extractJsonField(audit[AuditLogTable.oldValue].orEmpty(), "sessionStatus"),
        )
        assertEquals(
            "PENDING",
            DatabaseTestHelper.extractJsonField(audit[AuditLogTable.newValue].orEmpty(), "sessionStatus"),
        )
    }

    @Test
    fun `update final price succeeds and increments version`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        val updated = SessionService.updateFinalPrice(callerId, sessionId, BigDecimal("2750.00"), 1)

        assertEquals("2750.00", updated.finalPrice.toPlainString())
        assertEquals(2, updated.version)
        assertEquals(2L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `update final price with wrong version throws 409`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        assertFailsWith<ConflictException> {
            SessionService.updateFinalPrice(callerId, sessionId, BigDecimal("2750.00"), 99)
        }
    }

    @Test
    fun `update final price to zero succeeds`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

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
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        assertFailsWith<ConflictException> {
            transaction {
                SessionRepository.updateStatusInTransaction(sessionId, SessionStatus.COMPLETED, 99)
            }
        }
    }

    @Test
    fun `repo update final price with stale version throws 409`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        assertFailsWith<ConflictException> {
            transaction {
                SessionRepository.updateFinalPriceInTransaction(sessionId, BigDecimal("2750.00"), 99)
            }
        }
    }

    @Test
    fun `void session creates void record and writes audit`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
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
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
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
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        val otherCaller = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherCaller, "session-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)

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
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        val voidId = TestFixtures.uuid()
        SessionService.voidSession(callerId, sessionId, voidId, "Customer request")
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)

        val result = SessionService.unvoidSession(callerId, sessionId, "Resolved in error")

        assertNotNull(result.unvoidedAt)
        assertEquals(callerId, result.unvoidedBy)
        assertEquals("Resolved in error", result.unvoidedReason)
        assertEquals(2L, auditEntryCount(SessionVoidTable.tableName, voidId))
    }

    @Test
    fun `unvoid session is idempotent`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Customer request")

        val first = SessionService.unvoidSession(callerId, sessionId, "Resolved in error")
        val duplicate = SessionService.unvoidSession(callerId, sessionId, "Resolved in error")

        assertEquals(first.id, duplicate.id)
        assertEquals(first.unvoidedAt, duplicate.unvoidedAt)
    }

    @Test
    fun `unvoid session throws 404 for non-voided session`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        assertFailsWith<NotFoundException> {
            SessionService.unvoidSession(callerId, sessionId, "Resolved in error")
        }
    }

    @Test
    fun `unvoid session without VOID_SESSION is allowed at service layer`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        SessionService.voidSession(callerId, sessionId, TestFixtures.uuid(), "Customer request")
        val otherCaller = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherCaller, "session-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)

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
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, 1)

        assertEquals(2L, auditEntryCount(SessionTable.tableName, sessionId))
    }

    @Test
    fun `update status on REMITTED day without reason is rejected`() {
        val remittedDayId = insertRemittedDay()
        val remittedSessionId = TestFixtures.uuid()
        insertSessionOnDay(remittedSessionId, remittedDayId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        assertFailsWith<ValidationException> {
            SessionService.updateStatus(callerId, remittedSessionId, SessionStatus.COMPLETED, 1)
        }
    }

    @Test
    fun `update status on REMITTED day with reason succeeds and flags audit entry with reason`() {
        val remittedDayId = insertRemittedDay()
        val remittedSessionId = TestFixtures.uuid()
        insertSessionOnDay(remittedSessionId, remittedDayId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

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
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)

        val voidId = TestFixtures.uuid()
        val result = SessionService.voidSession(callerId, remittedSessionId, voidId, "Customer request")

        assertNotNull(result.sessionVoid)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, remittedSessionId)
        val audit = auditEntry(SessionVoidTable.tableName, voidId)
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Customer request", audit[AuditLogTable.reason])
    }

    @Test
    fun `add practitioner snapshots slot and writes audit`() {
        createSession(callerId, practitionerSessionId)
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)
        val requestId = TestFixtures.uuid()

        val result =
            SessionService.addPractitioner(
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
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)
        val requestId = TestFixtures.uuid()

        val first =
            SessionService.addPractitioner(
                callerId = callerId,
                id = requestId,
                sessionId = practitionerSessionId,
                practitionerId = practitionerId,
                remarks = null,
            )
        val duplicate =
            SessionService.addPractitioner(
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
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)
        val otherCaller = TestFixtures.uuid()
        DatabaseTestHelper.insertTestUser(otherCaller, "session-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)
        insertAssignment(otherCaller)

        val result =
            SessionService.addPractitioner(
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
            SessionService.addPractitioner(
                callerId = callerId,
                id = TestFixtures.uuid(),
                sessionId = TestFixtures.uuid(),
                practitionerId = practitionerId,
                remarks = null,
            )
        }
    }

    @Test
    fun `update practitioner remarks succeeds and increments session version and writes audit`() {
        createSession(callerId, practitionerSessionId)
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)
        val practitionerEntityId = TestFixtures.uuid()
        SessionService.addPractitioner(
            callerId = callerId,
            id = practitionerEntityId,
            sessionId = practitionerSessionId,
            practitionerId = practitionerId,
            remarks = "Initial remarks",
        )

        val updated =
            SessionService.updatePractitionerRemarks(
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
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)

        assertFailsWith<NotFoundException> {
            SessionService.updatePractitionerRemarks(
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
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)
        val practitionerEntityId = TestFixtures.uuid()
        SessionService.addPractitioner(
            callerId = callerId,
            id = practitionerEntityId,
            sessionId = practitionerSessionId,
            practitionerId = practitionerId,
            remarks = null,
        )

        SessionService.removePractitioner(
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
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)

        assertFailsWith<NotFoundException> {
            SessionService.removePractitioner(
                callerId = callerId,
                sessionId = practitionerSessionId,
                practitionerId = TestFixtures.uuid(),
            )
        }
    }

    @Test
    fun `stale practitioner mutation version update throws typed conflict`() {
        createSession(callerId, practitionerSessionId)
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)

        assertFailsWith<VersionMismatchException> {
            transaction {
                SessionPractitionerRepository.incrementSessionVersion(practitionerSessionId, 0)
            }
        }
    }

    @Suppress("LongParameterList")
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

    private fun insertSessionBaseRate(
        id: UUID = rateId,
        branchId: UUID = this.branchId,
        sessionType: SessionType = SessionType.REGULAR,
    ) {
        transaction {
            SessionBaseRateTable.insert {
                it[SessionBaseRateTable.id] = id
                it[SessionBaseRateTable.setBy] = callerId
                it[SessionBaseRateTable.branchId] = branchId
                it[SessionBaseRateTable.sessionType] = sessionType
                it[SessionBaseRateTable.rate] = BigDecimal("2500.00")
                it[SessionBaseRateTable.effectiveFrom] = TestFixtures.now.minusDays(1)
                it[SessionBaseRateTable.effectiveUntil] = TestFixtures.now.plusDays(365)
            }
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
            DatabaseTestHelper.createRemittedBranchDay(
                branchId,
                TestFixtures.today.minusDays(3),
            )
        trackOwned(BranchDayTable, BranchDayTable.id, dayId)
        return dayId
    }

    private fun correctionDays(): List<Pair<DayStatus, UUID>> {
        val pastDayId =
            DatabaseTestHelper.createBranchDayForDate(
                branchId,
                TestFixtures.today.minusDays(1),
            )
        trackOwned(BranchDayTable, BranchDayTable.id, pastDayId)
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
        val sessionClientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, sessionClientId)
        DatabaseTestHelper.insertTestSession(id, sessionClientId, dayId, sessionStatus = sessionStatus)
        trackOwned(SessionTable, SessionTable.id, id)
    }

    private fun insertSessionWithStatus(status: SessionStatus): UUID {
        val day = BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
        val id = TestFixtures.uuid()
        val sessionClientId = DatabaseTestHelper.insertTestClient()
        trackOwned(ClientTable, ClientTable.id, sessionClientId)
        DatabaseTestHelper.insertTestSession(id, sessionClientId, day.id, sessionStatus = status)
        trackOwned(SessionTable, SessionTable.id, id)
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
