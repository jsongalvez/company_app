package com.companyb.companyapp.session
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.session.SessionRepository
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.session.SessionTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SessionTypeAndPricePostgresTest : SessionServicePostgresBase() {
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
                NewSession(
                    id = mmSessionId,
                    clientId = mmClientId,
                    branchId = mmBranchId,
                    isWalkIn = false,
                    requestedPractitionerId = null,
                    finalPrice = BigDecimal.ZERO,
                    remarks = null,
                    otherConcerns = null,
                    nextAppointmentDate = null,
                ),
                context =
                    SessionCreateContext(requireClockIn = false),
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
                NewSession(
                    id = mmSessionId,
                    clientId = clientId,
                    branchId = mmBranchId,
                    isWalkIn = false,
                    requestedPractitionerId = null,
                    finalPrice = BigDecimal.ZERO,
                    remarks = null,
                    otherConcerns = null,
                    nextAppointmentDate = null,
                ),
                context =
                    SessionCreateContext(requireClockIn = false),
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

    @Test
    fun `update final price rejects amount over NUMERIC(10,2) range`() {
        createSession(callerId, sessionId)

        assertFailsWith<ValidationException> {
            SessionService.updateFinalPrice(callerId, sessionId, BigDecimal("100000000"), 1)
        }
    }

    @Test
    fun `update final price persists boundary max money amount`() {
        createSession(callerId, sessionId)

        val updated = SessionService.updateFinalPrice(callerId, sessionId, BigDecimal("99999999.99"), 1)

        assertEquals("99999999.99", updated.finalPrice.toPlainString())
        assertEquals(2, updated.version)
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
}
