package com.companyb.companyapp.integration

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.session.ConcernRepository
import com.companyb.companyapp.session.ConcernTable
import com.companyb.companyapp.session.SessionConcernService
import com.companyb.companyapp.session.SessionConcernTable
import com.companyb.companyapp.session.SessionPractitionerRepository
import com.companyb.companyapp.session.SessionPractitionerService
import com.companyb.companyapp.session.SessionPractitionerTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Session practitioner and concern mutations serialize with the remittance REMITTED
 * transition (#513). Both helpers gate through the locked branch-day read, so a
 * pre-freeze authorization can no longer write onto a financially-frozen day.
 */
class SessionSubEntityDayLockPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val practitionerId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private val sessionId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val systemConcernId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "subentity-lock-caller")
        IdentityFixtures.insertTestUser(practitionerId, "subentity-lock-practitioner")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Subentity Lock Branch")
        SessionClientFixtures.insertTestClient(clientId)
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        SessionClientFixtures.insertTestSession(sessionId, clientId, branchDayId)
        transaction {
            ConcernTable.insert {
                it[ConcernTable.id] = systemConcernId
                it[ConcernTable.label] = "Knee Pain"
            }
        }
    }

    @Test
    fun `practitioner add on day REMITTED by remittance submit is rejected with no row`() {
        submitRemittanceCoveringDay(branchDayId)

        val auditsBefore = callerAuditCount()

        assertFailsWith<ForbiddenException> {
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                id = TestFixtures.uuid(),
                sessionId = sessionId,
                practitionerId = practitionerId,
                remarks = null,
            )
        }
        assertEquals(auditsBefore, callerAuditCount(), "rejected add writes no audit rows")
        assertTrue(
            SessionPractitionerRepository.findBySessionId(sessionId).none { it.practitionerId == practitionerId },
            "rejected add writes no practitioner row",
        )
    }

    @Test
    fun `concern add on day REMITTED by remittance submit is rejected with no link`() {
        submitRemittanceCoveringDay(branchDayId)

        val auditsBefore = callerAuditCount()

        assertFailsWith<ForbiddenException> {
            SessionConcernService.addToSession(callerId, sessionId, systemConcernId)
        }
        assertEquals(auditsBefore, callerAuditCount(), "rejected add writes no audit rows")
        assertTrue(
            ConcernRepository.getConcernsForSession(sessionId).isEmpty(),
            "rejected add writes no concern link",
        )
    }

    @Test
    fun `practitioner add on submit-REMITTED day with reason and EDIT_PAST_DAY succeeds flagged`() {
        submitRemittanceCoveringDay(branchDayId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        val result =
            SessionPractitionerService.addPractitioner(
                callerId = callerId,
                id = TestFixtures.uuid(),
                sessionId = sessionId,
                practitionerId = practitionerId,
                remarks = null,
                reason = "Coordinator correction",
            )

        assertTrue(result.created)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq SessionPractitionerTable.tableName) and
                            (AuditLogTable.recordId eq result.practitioner.id)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `concern add on submit-REMITTED day with reason and EDIT_PAST_DAY succeeds flagged`() {
        submitRemittanceCoveringDay(branchDayId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        SessionConcernService.addToSession(callerId, sessionId, systemConcernId, "Coordinator correction")

        assertEquals(listOf(systemConcernId), ConcernRepository.getConcernsForSession(sessionId).map { it.id })
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq SessionConcernTable.tableName) and
                            (AuditLogTable.recordId eq sessionId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `concurrent remittance day transition serializes with practitioner add gate`() {
        // Observable barrier (#527, mirrors #509/#510/#511/#512): the holder keeps the
        // day row locked with the same locked primitive remittance submit uses while the
        // add attempts its in-tx gate. Release happens only after the contender is
        // observed waiting on the holder's lock, so the add must block until the
        // transition commits, then read REMITTED and fail closed — never slipping a row
        // onto the frozen day.
        val failure =
            runCatching {
                LockBarrier.withDayRemitBarrier(branchDayId) {
                    SessionPractitionerService.addPractitioner(
                        callerId = callerId,
                        id = TestFixtures.uuid(),
                        sessionId = sessionId,
                        practitionerId = practitionerId,
                        remarks = null,
                    )
                }
            }.exceptionOrNull()

        assertTrue(failure is ForbiddenException)
        assertTrue(
            SessionPractitionerRepository.findBySessionId(sessionId).isEmpty(),
            "blocked add writes no practitioner row",
        )
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(branchDayId))
    }

    @Test
    fun `concurrent remittance day transition serializes with concern add gate`() {
        val failure =
            runCatching {
                LockBarrier.withDayRemitBarrier(branchDayId) {
                    SessionConcernService.addToSession(callerId, sessionId, systemConcernId)
                }
            }.exceptionOrNull()

        assertTrue(failure is ForbiddenException)
        assertTrue(
            ConcernRepository.getConcernsForSession(sessionId).isEmpty(),
            "blocked add writes no concern link",
        )
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(branchDayId))
    }

    private fun submitRemittanceCoveringDay(dayId: UUID) {
        val remittanceId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = TestFixtures.today.minusDays(1),
            dateRangeEnd = TestFixtures.today,
        )
        RemittanceService.addDayBreakdown(
            callerId = callerId,
            remittanceId = remittanceId,
            id = TestFixtures.uuid(),
            branchDayId = dayId,
        )
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)
    }

    private fun callerAuditCount(): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.changedBy eq callerId }
                .count()
        }
}
