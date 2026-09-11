package com.companyb.companyapp.session

import com.companyb.companyapp.client.ClientService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * #910 — practitioner/concern write paths are sticky on anonymization: once a
 * client is anonymized, every practitioner/concern mutation on the retained
 * sessions 409s (the session-create #692 and PENDING-reopen 409 precedent),
 * so no later write can reintroduce the free text #908 scrubs.
 */
class SessionAnonymizedGuardPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val practitionerId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val practitionerRowId = TestFixtures.uuid()
    private val systemConcernId = TestFixtures.uuid()
    private var branchDayId: UUID = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "anonymized-guard-caller")
        IdentityFixtures.insertTestUser(practitionerId, "anonymized-guard-practitioner")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Anonymized Guard Branch $branchId")
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.EDIT_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = TestFixtures.uuid(),
        )
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = TestFixtures.uuid(),
        )
        SessionClientFixtures.insertTestBaseRate(TestFixtures.uuid(), branchId, callerId)
        BranchWorkforceFixtures.insertTestAssignment(
            userId = practitionerId,
            branchId = branchId,
            slot = 1,
            assignedBy = callerId,
        )
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        BranchWorkforceFixtures.insertTestAttendance(branchDayId, callerId)
        transaction {
            ConcernTable.insert {
                it[ConcernTable.id] = systemConcernId
                it[ConcernTable.label] = "Guard Concern"
            }
        }
    }

    @Test
    fun `addPractitioner on anonymized client session is rejected`() {
        createClient()
        createCompletedSession()
        ClientService.anonymize(callerId, clientId)

        assertFailsWith<ConflictException> {
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                id = TestFixtures.uuid(),
                sessionId = sessionId,
                practitionerId = practitionerId,
                remarks = "late note",
            )
        }
    }

    @Test
    fun `updatePractitionerRemarks on anonymized client session is rejected`() {
        createClient()
        createSessionWithPractitioner()
        ClientService.anonymize(callerId, clientId)

        assertFailsWith<ConflictException> {
            SessionPractitionerService.updatePractitionerRemarks(
                callerId = callerId,
                sessionId = sessionId,
                practitionerId = practitionerId,
                remarks = "late edit",
            )
        }
    }

    @Test
    fun `removePractitioner on anonymized client session is rejected`() {
        createClient()
        createSessionWithPractitioner()
        ClientService.anonymize(callerId, clientId)

        assertFailsWith<ConflictException> {
            SessionPractitionerService.removePractitioner(
                callerId = callerId,
                sessionId = sessionId,
                practitionerId = practitionerId,
            )
        }
    }

    @Test
    fun `addToSession on anonymized client session is rejected`() {
        createClient()
        createCompletedSession()
        ClientService.anonymize(callerId, clientId)

        assertFailsWith<ConflictException> {
            SessionConcernService.addToSession(callerId, sessionId, systemConcernId)
        }
    }

    @Test
    fun `removeFromSession on anonymized client session is rejected`() {
        createClient()
        createCompletedSession()
        SessionConcernService.addToSession(callerId, sessionId, systemConcernId)
        ClientService.anonymize(callerId, clientId)

        assertFailsWith<ConflictException> {
            SessionConcernService.removeFromSession(callerId, sessionId, systemConcernId)
        }
    }

    @Test
    fun `promoteConcern on anonymized client session is rejected`() {
        createClient()
        createCompletedSession()
        ClientService.anonymize(callerId, clientId)

        assertFailsWith<ConflictException> {
            SessionConcernService.promoteConcern(
                callerId = callerId,
                sessionId = sessionId,
                concernId = TestFixtures.uuid(),
                label = "Late Label",
            )
        }
    }

    @Test
    fun `reads still serve anonymized client sessions`() {
        createClient()
        createSessionWithPractitioner()
        ClientService.anonymize(callerId, clientId)

        val practitioners = SessionPractitionerService.getForSession(callerId, sessionId)
        assertEquals(1, practitioners.size, "reads stay open; only writes are sticky-guarded")
        val concerns = SessionConcernService.getForSession(callerId, sessionId)
        assertEquals(0, concerns.size)
    }

    private fun createClient() {
        ClientService.create(
            callerId = callerId,
            id = clientId,
            firstName = "ZqxGuard",
            lastName = "Client",
            middleName = null,
            suffix = null,
            phoneNumber = null,
            address = "Cavite",
            gender = Gender.F,
            age = RETAINED_AGE,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )
    }

    private fun createCompletedSession() {
        SessionService.create(
            callerId = callerId,
            id = sessionId,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = false,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = null,
            otherConcerns = null,
            nextAppointmentDate = null,
        )
        SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, expectedVersion = 1)
    }

    private fun createSessionWithPractitioner() {
        SessionService.create(
            callerId = callerId,
            id = sessionId,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = false,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = null,
            otherConcerns = null,
            nextAppointmentDate = null,
        )
        SessionPractitionerService.addPractitioner(
            callerId = callerId,
            id = practitionerRowId,
            sessionId = sessionId,
            practitionerId = practitionerId,
            remarks = null,
        )
        SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, expectedVersion = 2)
    }

    companion object {
        private const val RETAINED_AGE = 42
    }
}
