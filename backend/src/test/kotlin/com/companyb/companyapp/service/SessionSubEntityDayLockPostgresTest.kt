package com.companyb.companyapp.service

import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.repository.ConcernRepository
import com.companyb.companyapp.repository.SessionPractitionerRepository
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.ConcernTable
import com.companyb.companyapp.repository.model.SessionConcernTable
import com.companyb.companyapp.repository.model.SessionPractitionerTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.service.session.SessionConcernService
import com.companyb.companyapp.service.session.SessionPractitionerService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

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
        DatabaseTestHelper.insertTestUser(callerId, "subentity-lock-caller")
        DatabaseTestHelper.insertTestUser(practitionerId, "subentity-lock-practitioner")
        DatabaseTestHelper.insertTestBranch(branchId, "Test Subentity Lock Branch")
        DatabaseTestHelper.insertTestClient(clientId)
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        DatabaseTestHelper.insertTestSession(sessionId, clientId, branchDayId)
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
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)

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
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)

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
        val dayLocked = CountDownLatch(1)

        // Barrier (mirrors #509/#510/#511/#512): the submit side holds the day row lock
        // (the same locked primitive remittance submit uses) while the add attempts its
        // in-tx gate. The add must block on the lock until the transition commits, then
        // read REMITTED and fail closed — never slipping a row onto the frozen day.
        val submitter =
            thread {
                transaction {
                    BranchDayService.lockDaysInTransaction(listOf(branchDayId))
                    dayLocked.countDown()
                    Thread.sleep(BARRIER_HOLD_MILLIS)
                    BranchDayService.markDaysRemittedInTransaction(listOf(branchDayId))
                }
            }
        assertTrue(dayLocked.await(BARRIER_WAIT_SECONDS, TimeUnit.SECONDS))

        val (failure, blockedFor) =
            measureTimedValue {
                runCatching {
                    SessionPractitionerService.addPractitioner(
                        callerId = callerId,
                        id = TestFixtures.uuid(),
                        sessionId = sessionId,
                        practitionerId = practitionerId,
                        remarks = null,
                    )
                }.exceptionOrNull()
            }
        submitter.join(BARRIER_JOIN_MILLIS)

        assertTrue(failure is ForbiddenException)
        assertTrue(
            blockedFor.inWholeMilliseconds >= BARRIER_MIN_BLOCKED_MILLIS,
            "add did not block on the day lock: finished in $blockedFor",
        )
        assertTrue(
            SessionPractitionerRepository.findBySessionId(sessionId).isEmpty(),
            "blocked add writes no practitioner row",
        )
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(branchDayId))
    }

    @Test
    fun `concurrent remittance day transition serializes with concern add gate`() {
        val dayLocked = CountDownLatch(1)

        val submitter =
            thread {
                transaction {
                    BranchDayService.lockDaysInTransaction(listOf(branchDayId))
                    dayLocked.countDown()
                    Thread.sleep(BARRIER_HOLD_MILLIS)
                    BranchDayService.markDaysRemittedInTransaction(listOf(branchDayId))
                }
            }
        assertTrue(dayLocked.await(BARRIER_WAIT_SECONDS, TimeUnit.SECONDS))

        val (failure, blockedFor) =
            measureTimedValue {
                runCatching {
                    SessionConcernService.addToSession(callerId, sessionId, systemConcernId)
                }.exceptionOrNull()
            }
        submitter.join(BARRIER_JOIN_MILLIS)

        assertTrue(failure is ForbiddenException)
        assertTrue(
            blockedFor.inWholeMilliseconds >= BARRIER_MIN_BLOCKED_MILLIS,
            "add did not block on the day lock: finished in $blockedFor",
        )
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

    companion object {
        private const val BARRIER_HOLD_MILLIS = 5000L
        private const val BARRIER_MIN_BLOCKED_MILLIS = 3000L
        private const val BARRIER_WAIT_SECONDS = 10L
        private const val BARRIER_JOIN_MILLIS = 30000L
    }
}
