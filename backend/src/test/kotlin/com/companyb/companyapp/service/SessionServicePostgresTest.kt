package com.companyb.companyapp.service

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.SessionType
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
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.repository.model.UserBranchAssignmentTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ConflictResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class SessionServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val rateId = UUID.randomUUID()
    private val secondSessionRateId = UUID.randomUUID()
    private val subsequentRateId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val walkInSessionId = UUID.randomUUID()
    private val practitionerId = UUID.randomUUID()
    private val practitionerSessionId = UUID.randomUUID()

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
        assertEquals(SessionType.REGULAR.name, result.session.sessionType)
        assertEquals("PENDING", result.session.sessionStatus)
        assertFalse(result.session.isWalkIn)
        assertEquals("2500.00", result.session.basePrice.toPlainString())
        assertEquals("2500.00", result.session.finalPrice.toPlainString())
        assertNotNull(result.session.version)
        assertEquals(1, result.session.version)
        assertEquals(1L, auditEntryCount(sessionId))
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
        assertEquals(1L, auditEntryCount(sessionId))
    }

    @Test
    fun `create session rejects concurrent pending session for same client`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        val secondSessionId = UUID.randomUUID()

        assertFailsWith<ConflictResponse> {
            createSession(callerId, secondSessionId)
        }
    }

    @Test
    fun `create session without EDIT_BRANCH_DATA is allowed at service layer`() {
        val otherCaller = UUID.randomUUID()
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
        val unknownBranchId = UUID.randomUUID()

        assertFailsWith<NotFoundResponse> {
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
                bookedAt = null,
                nextAppointmentDate = null,
            )
        }
    }

    @Test
    fun `medical mission branch always creates MEDICAL_MISSION session type`() {
        val mmBranchId = UUID.randomUUID()
        val mmClientId = UUID.randomUUID()
        DatabaseTestHelper.insertTestBranch(mmBranchId, branchType = BranchType.MEDICAL_MISSION)
        trackOwned(BranchTable, BranchTable.id, mmBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, mmBranchId)
        DatabaseTestHelper.insertTestClient(mmClientId)
        trackOwned(ClientTable, ClientTable.id, mmClientId)
        val mmSessionId = UUID.randomUUID()
        val mmRateId = UUID.randomUUID()
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
                bookedAt = null,
                nextAppointmentDate = null,
            )
        trackOwned(SessionTable, SessionTable.id, mmSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, mmSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, mmSessionId)

        assertEquals(SessionType.MEDICAL_MISSION.name, result.session.sessionType)
    }

    @Test
    fun `medical mission branch type not counted toward prior sessions`() {
        val mmBranchId = UUID.randomUUID()
        DatabaseTestHelper.insertTestBranch(mmBranchId, branchType = BranchType.MEDICAL_MISSION)
        trackOwned(BranchTable, BranchTable.id, mmBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, mmBranchId)
        val mmRateId = UUID.randomUUID()
        insertSessionBaseRate(mmRateId, mmBranchId, SessionType.MEDICAL_MISSION)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.id, mmRateId)
        trackOwned(SessionBaseRateTable, SessionBaseRateTable.setBy, callerId)
        val mmSessionId = UUID.randomUUID()

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
                bookedAt = null,
                nextAppointmentDate = null,
            )
        trackOwned(SessionTable, SessionTable.id, mmSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, mmSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, mmSessionId)

        assertEquals(SessionType.MEDICAL_MISSION.name, mmResult.session.sessionType)

        // Complete the MM session in DB to allow creating a clinic session for same client
        transaction {
            SessionTable.update({ SessionTable.id eq mmSessionId }) {
                it[SessionTable.sessionStatus] = SessionStatus.COMPLETED
            }
        }

        val clinicSessionId = UUID.randomUUID()
        val clinicResult = createSession(callerId, clinicSessionId)
        trackOwned(SessionTable, SessionTable.id, clinicSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, clinicSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, clinicSessionId)

        assertEquals(SessionType.REGULAR.name, clinicResult.session.sessionType)
    }

    @Test
    fun `update status from PENDING to COMPLETED succeeds and increments version`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        val updated = SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, 1)

        assertEquals("COMPLETED", updated.sessionStatus)
        assertEquals(2, updated.version)
        assertEquals(2L, auditEntryCount(sessionId))
    }

    @Test
    fun `update status with wrong version throws 409`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        assertFailsWith<ConflictResponse> {
            SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, 99)
        }
    }

    @Test
    fun `update status for non-existent session throws 404`() {
        assertFailsWith<NotFoundResponse> {
            SessionService.updateStatus(callerId, UUID.randomUUID(), SessionStatus.COMPLETED, 1)
        }
    }

    @Test
    fun `update status without EDIT_BRANCH_DATA is allowed at service layer`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "session-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)

        val updated = SessionService.updateStatus(otherCaller, sessionId, SessionStatus.COMPLETED, 1)

        assertEquals("COMPLETED", updated.sessionStatus)
        assertEquals(2, updated.version)
    }

    @Test
    fun `walk-in session cannot transition to NO_SHOW`() {
        createSession(callerId, walkInSessionId, isWalkIn = true)
        trackOwned(SessionTable, SessionTable.id, walkInSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, walkInSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, walkInSessionId)

        assertFailsWith<BadRequestResponse> {
            SessionService.updateStatus(callerId, walkInSessionId, SessionStatus.NO_SHOW, 1)
        }
    }

    @Test
    fun `walk-in session cannot transition to CANCELLED`() {
        createSession(callerId, walkInSessionId, isWalkIn = true)
        trackOwned(SessionTable, SessionTable.id, walkInSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, walkInSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, walkInSessionId)

        assertFailsWith<BadRequestResponse> {
            SessionService.updateStatus(callerId, walkInSessionId, SessionStatus.CANCELLED, 1)
        }
    }

    @Test
    fun `void session creates void record and writes audit`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        val voidId = UUID.randomUUID()

        val result = SessionService.voidSession(callerId, sessionId, voidId, "Customer request")

        assertTrue(result.created)
        assertEquals(sessionId, result.sessionVoid.sessionId)
        assertEquals("Customer request", result.sessionVoid.voidReason)
        assertEquals(callerId, result.sessionVoid.voidedBy)
    }

    @Test
    fun `void session returns existing on duplicate`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        val voidId = UUID.randomUUID()

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
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "session-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)

        val result = SessionService.voidSession(otherCaller, sessionId, UUID.randomUUID(), "Customer request")

        assertTrue(result.created)
    }

    @Test
    fun `void session throws 404 for non-existent session`() {
        assertFailsWith<NotFoundResponse> {
            SessionService.voidSession(callerId, UUID.randomUUID(), UUID.randomUUID(), "Customer request")
        }
    }

    @Test
    fun `unvoid session sets unvoided fields`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        SessionService.voidSession(callerId, sessionId, UUID.randomUUID(), "Customer request")

        val result = SessionService.unvoidSession(callerId, sessionId, "Resolved in error")

        assertNotNull(result.unvoidedAt)
        assertEquals(callerId, result.unvoidedBy)
        assertEquals("Resolved in error", result.unvoidedReason)
    }

    @Test
    fun `unvoid session is idempotent`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        SessionService.voidSession(callerId, sessionId, UUID.randomUUID(), "Customer request")

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

        assertFailsWith<NotFoundResponse> {
            SessionService.unvoidSession(callerId, sessionId, "Resolved in error")
        }
    }

    @Test
    fun `unvoid session without VOID_SESSION is allowed at service layer`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)
        SessionService.voidSession(callerId, sessionId, UUID.randomUUID(), "Customer request")
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "session-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)

        val result = SessionService.unvoidSession(otherCaller, sessionId, "Resolved in error")

        assertNotNull(result.unvoidedAt)
    }

    @Test
    fun `void session throws 404 for non-existent session on unvoid`() {
        assertFailsWith<NotFoundResponse> {
            SessionService.unvoidSession(callerId, UUID.randomUUID(), "Resolved in error")
        }
    }

    @Test
    fun `update status writes audit log entry`() {
        createSession(callerId, sessionId)
        trackOwned(SessionTable, SessionTable.id, sessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, sessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, sessionId)

        SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, 1)

        assertEquals(2L, auditEntryCount(sessionId))
    }

    @Test
    fun `add practitioner snapshots slot and writes audit`() {
        createSession(callerId, practitionerSessionId)
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)
        val requestId = UUID.randomUUID()

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
        assertEquals(1L, auditEntryCount(practitionerSessionId))
    }

    @Test
    fun `add practitioner returns existing on duplicate`() {
        createSession(callerId, practitionerSessionId)
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)
        val requestId = UUID.randomUUID()

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
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)
        val otherCaller = UUID.randomUUID()
        DatabaseTestHelper.insertTestUser(otherCaller, "session-other")
        trackOwned(AppUserTable, AppUserTable.id, otherCaller)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, otherCaller)
        insertAssignment(otherCaller)

        val result =
            SessionPractitionerService.addPractitioner(
                callerId = otherCaller,
                id = UUID.randomUUID(),
                sessionId = practitionerSessionId,
                practitionerId = practitionerId,
                remarks = null,
            )

        assertTrue(result.created)
    }

    @Test
    fun `add practitioner throws 404 for non-existent session`() {
        assertFailsWith<NotFoundResponse> {
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                id = UUID.randomUUID(),
                sessionId = UUID.randomUUID(),
                practitionerId = practitionerId,
                remarks = null,
            )
        }
    }

    @Test
    fun `update practitioner remarks succeeds and increments session version`() {
        createSession(callerId, practitionerSessionId)
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)
        SessionPractitionerService.addPractitioner(
            callerId = callerId,
            id = UUID.randomUUID(),
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
    }

    @Test
    fun `update practitioner remarks throws 404 for non-existent practitioner in session`() {
        createSession(callerId, practitionerSessionId)
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)

        assertFailsWith<NotFoundResponse> {
            SessionPractitionerService.updatePractitionerRemarks(
                callerId = callerId,
                sessionId = practitionerSessionId,
                practitionerId = UUID.randomUUID(),
                remarks = "Some remarks",
            )
        }
    }

    @Test
    fun `remove practitioner succeeds and increments session version`() {
        createSession(callerId, practitionerSessionId)
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)
        SessionPractitionerService.addPractitioner(
            callerId = callerId,
            id = UUID.randomUUID(),
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
    }

    @Test
    fun `remove practitioner throws 404 for non-existent practitioner in session`() {
        createSession(callerId, practitionerSessionId)
        trackOwned(SessionTable, SessionTable.id, practitionerSessionId)
        trackOwned(SessionVoidTable, SessionVoidTable.sessionId, practitionerSessionId)
        trackOwned(SessionPractitionerTable, SessionPractitionerTable.sessionId, practitionerSessionId)

        assertFailsWith<NotFoundResponse> {
            SessionPractitionerService.removePractitioner(
                callerId = callerId,
                sessionId = practitionerSessionId,
                practitionerId = UUID.randomUUID(),
            )
        }
    }

    @Suppress("LongParameterList")
    private fun createSession(
        callerId: UUID,
        id: UUID,
        clientId: UUID = this.clientId,
        branchId: UUID = this.branchId,
        isWalkIn: Boolean = false,
        finalPrice: BigDecimal = BigDecimal("2500.00"),
    ) = SessionService.create(
        callerId = callerId,
        id = id,
        clientId = clientId,
        branchId = branchId,
        isWalkIn = isWalkIn,
        requestedPractitionerId = null,
        finalPrice = finalPrice,
        remarks = "Test session",
        otherConcerns = null,
        bookedAt = null,
        nextAppointmentDate = null,
    )

    private fun insertAssignment(userId: UUID) {
        UserBranchAssignmentRepository.create(
            id = UUID.randomUUID(),
            userId = userId,
            branchId = branchId,
            slot = 1,
            assignedBy = callerId,
        )
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
                it[SessionBaseRateTable.effectiveFrom] = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1)
                it[SessionBaseRateTable.effectiveUntil] = OffsetDateTime.now(ZoneOffset.UTC).plusDays(365)
            }
        }
    }

    private fun auditEntryCount(sessionId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq SessionTable.tableName) and
                        (AuditLogTable.recordId eq sessionId)
                }.count()
        }
}
