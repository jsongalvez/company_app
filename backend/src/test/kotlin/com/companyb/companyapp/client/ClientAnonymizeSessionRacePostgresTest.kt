package com.companyb.companyapp.client

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.client.Gender
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.session.ConcernTable
import com.companyb.companyapp.session.SessionConcernService
import com.companyb.companyapp.session.SessionConcernTable
import com.companyb.companyapp.session.SessionPractitionerService
import com.companyb.companyapp.session.SessionPractitionerTable
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #910 — practitioner/concern write paths serialize with anonymization on
 * the client row (the #509 client-first lock order): a practitioner/concern
 * write can never commit inside the anonymize census-to-redaction window, so
 * concurrent writes leave no unredacted history and post-anonymize writes 409
 * instead of reintroducing the free text #908 scrubs.
 *
 * The latch probes pin the serialization deterministically (the contender
 * blocks while the client row is held); the gate probe mirrors the #524
 * `concurrent update and anonymize` pin (either order lands clean).
 */
class ClientAnonymizeSessionRacePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val practitionerId = TestFixtures.uuid()
    private val contenderPractitionerId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val practitionerRowId = TestFixtures.uuid()
    private val contenderRowId = TestFixtures.uuid()
    private var branchDayId: UUID = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "anon-race-caller")
        IdentityFixtures.insertTestUser(practitionerId, "anon-race-practitioner")
        IdentityFixtures.insertTestUser(contenderPractitionerId, "anon-race-contender")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Anon Race Branch $branchId")
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
        BranchWorkforceFixtures.insertTestAssignment(
            userId = contenderPractitionerId,
            branchId = branchId,
            slot = 2,
            assignedBy = callerId,
        )
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        BranchWorkforceFixtures.insertTestAttendance(branchDayId, callerId)
    }

    @Test
    fun `practitioner add blocks while the client row is held`() {
        createClient()
        createCompletedMarkedSession()
        val latches = Latches()
        val holder = startClientLockHolder(latches)
        try {
            assertTrue(latches.holderReady.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            var failure: Throwable? = null
            val contender =
                thread {
                    failure =
                        runCatching {
                            SessionPractitionerService.addPractitioner(
                                callerId = callerId,
                                id = contenderRowId,
                                sessionId = sessionId,
                                practitionerId = contenderPractitionerId,
                                remarks = "contender note $RACE_MARKER",
                            )
                        }.exceptionOrNull()
                }
            Thread.sleep(READINESS_MILLIS)
            assertTrue(contender.isAlive, "contender finished without contending the client row")
            latches.releaseHolder.countDown()
            contender.join(JOIN_MILLIS)
            assertTrue(!contender.isAlive, "contender still alive after the client lock was released")
            assertEquals(null, failure, "contender failed after release: $failure")
            assertTrue(
                practitionerRemarksLive().joinToString("").contains(RACE_MARKER),
                "released contender must land its write",
            )
        } finally {
            latches.releaseHolder.countDown()
            holder.join(JOIN_MILLIS)
        }
    }

    @Test
    fun `concern promote blocks while the client row is held`() {
        createClient()
        createCompletedMarkedSession()
        val latches = Latches()
        val holder = startClientLockHolder(latches)
        try {
            assertTrue(latches.holderReady.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            var failure: Throwable? = null
            val contender =
                thread {
                    failure =
                        runCatching {
                            SessionConcernService.promoteConcern(
                                callerId = callerId,
                                sessionId = sessionId,
                                concernId = TestFixtures.uuid(),
                                label = "Contender $RACE_MARKER",
                            )
                        }.exceptionOrNull()
                }
            Thread.sleep(READINESS_MILLIS)
            assertTrue(contender.isAlive, "contender finished without contending the client row")
            latches.releaseHolder.countDown()
            contender.join(JOIN_MILLIS)
            assertTrue(!contender.isAlive, "contender still alive after the client lock was released")
            assertEquals(null, failure, "contender failed after release: $failure")
        } finally {
            latches.releaseHolder.countDown()
            holder.join(JOIN_MILLIS)
        }
    }

    @Test
    fun `concurrent practitioner add racing anonymize leaves no unredacted history`() {
        createClient()
        createCompletedMarkedSession()
        val gate = CountDownLatch(1)
        var contenderFailure: Throwable? = null
        var anonymizeFailure: Throwable? = null
        val contender =
            thread {
                gate.await()
                contenderFailure =
                    runCatching {
                        SessionPractitionerService.addPractitioner(
                            callerId = callerId,
                            id = contenderRowId,
                            sessionId = sessionId,
                            practitionerId = contenderPractitionerId,
                            remarks = "contender note $RACE_MARKER",
                        )
                    }.exceptionOrNull()
            }
        val anonymizer =
            thread {
                gate.await()
                anonymizeFailure = runCatching { ClientService.anonymize(callerId, clientId) }.exceptionOrNull()
            }
        gate.countDown()
        contender.join(JOIN_MILLIS)
        anonymizer.join(JOIN_MILLIS)

        assertEquals(null, anonymizeFailure, "anonymize failed: $anonymizeFailure")
        assertTrue(
            contenderFailure == null || contenderFailure is ConflictException,
            "loser must 409, got $contenderFailure",
        )
        assertNull(liveSessionRemarks(), "session remarks must be scrubbed live")
        assertFalse(
            practitionerRemarksLive().joinToString("").contains(PHONE_MARKER),
            "pre-existing practitioner text leaked live",
        )
        val payloadText = sessionAndPractitionerPayloadText()
        assertFalse(payloadText.contains(PHONE_MARKER), "race leaked original text: $payloadText")
        assertFalse(payloadText.contains(RACE_MARKER), "race leaked contender text: $payloadText")
    }

    @Test
    fun `promoted concern labels are shared catalog rows outside per-client erasure`() {
        createClient()
        createCompletedMarkedSession()
        val concernId = TestFixtures.uuid()
        SessionConcernService.promoteConcern(callerId, sessionId, concernId, "Shared $RACE_MARKER")

        ClientService.anonymize(callerId, clientId)

        val label =
            transaction {
                ConcernTable.selectAll().where { ConcernTable.id eq concernId }.single()[ConcernTable.label]
            }
        assertTrue(label.contains(RACE_MARKER), "shared catalog rows are not per-client scrubbed: $label")
        val payloadText = sessionAndPractitionerPayloadText()
        assertFalse(payloadText.contains(PHONE_MARKER), "session history leaked: $payloadText")
        assertFalse(payloadText.contains(RACE_MARKER), "promote session history leaked: $payloadText")
        assertNull(liveSessionRemarks(), "session remarks must be scrubbed live")
    }

    private fun startClientLockHolder(latches: Latches): Thread =
        thread {
            transaction {
                ClientRepository.acquireLockInTransaction(clientId) ?: error("client missing")
                latches.holderReady.countDown()
                assertTrue(latches.releaseHolder.await(LATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            }
        }

    private fun createClient() {
        ClientService.create(
            callerId = callerId,
            id = clientId,
            firstName = "ZqxRace",
            lastName = "Client",
            middleName = null,
            suffix = null,
            phoneNumber = PHONE_MARKER,
            address = "Cavite",
            gender = Gender.F,
            age = RETAINED_AGE,
            systolicBp = null,
            diastolicBp = null,
            medicalConditions = null,
        )
    }

    private fun createCompletedMarkedSession() {
        SessionService.create(
            callerId = callerId,
            id = sessionId,
            clientId = clientId,
            branchId = branchId,
            isWalkIn = false,
            requestedPractitionerId = null,
            finalPrice = BigDecimal("2500.00"),
            remarks = "call $PHONE_MARKER after lunch",
            otherConcerns = null,
            nextAppointmentDate = null,
        )
        SessionPractitionerService.addPractitioner(
            callerId = callerId,
            id = practitionerRowId,
            sessionId = sessionId,
            practitionerId = practitionerId,
            remarks = "practitioner note $PHONE_MARKER",
        )
        SessionService.updateStatus(callerId, sessionId, SessionStatus.COMPLETED, expectedVersion = 2)
    }

    private fun liveSessionRemarks(): String? =
        transaction {
            SessionTable.selectAll().where { SessionTable.id eq sessionId }.single()[SessionTable.remarks]
        }

    private fun practitionerRemarksLive(): List<String?> =
        transaction {
            SessionPractitionerTable
                .selectAll()
                .where { SessionPractitionerTable.sessionId eq sessionId }
                .map { it[SessionPractitionerTable.remarks] }
        }

    private fun sessionAndPractitionerPayloadText(): String =
        transaction {
            val sessionPayloads =
                AuditLogTable
                    .selectAll()
                    .where { AuditLogTable.auditTableName eq SessionTable.tableName }
                    .joinToString("") { it[AuditLogTable.oldValue].orEmpty() + it[AuditLogTable.newValue].orEmpty() }
            val practitionerPayloads =
                AuditLogTable
                    .selectAll()
                    .where { AuditLogTable.auditTableName eq SessionPractitionerTable.tableName }
                    .joinToString("") { it[AuditLogTable.oldValue].orEmpty() + it[AuditLogTable.newValue].orEmpty() }
            val linkPayloads =
                AuditLogTable
                    .selectAll()
                    .where { AuditLogTable.auditTableName eq SessionConcernTable.tableName }
                    .joinToString("") { it[AuditLogTable.oldValue].orEmpty() + it[AuditLogTable.newValue].orEmpty() }
            sessionPayloads + practitionerPayloads + linkPayloads
        }

    private class Latches {
        val holderReady = CountDownLatch(1)
        val releaseHolder = CountDownLatch(1)
    }

    companion object {
        private const val PHONE_MARKER = "Zqx910Phone"
        private const val RACE_MARKER = "Zqx910Race"
        private const val RETAINED_AGE = 42
        private const val READINESS_MILLIS = 1000L
        private const val JOIN_MILLIS = 30000L
        private const val LATCH_TIMEOUT_SECONDS = 30L
    }
}
