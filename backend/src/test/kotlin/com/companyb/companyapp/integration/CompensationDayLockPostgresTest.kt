package com.companyb.companyapp.integration

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.finance.CompensationRepository
import com.companyb.companyapp.finance.CompensationService
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #511 — compensation mutations serialize with the remittance REMITTED transition.
 * Both mutations gate through the locked branch-day read, so a pre-freeze
 * authorization can no longer write onto a financially-frozen day.
 */
class CompensationDayLockPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val targetUserId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    private lateinit var workBranchDayId: UUID
    private lateinit var payingBranchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "comp-lock-caller")
        IdentityFixtures.insertTestUser(targetUserId, "comp-lock-target")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Compensation Lock Branch")
        workBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        payingBranchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        IdentityFixtures.grantAssignCompensation(callerId, sourceId)
    }

    @Test
    fun `create on day REMITTED by remittance submit is rejected with no row`() {
        submitRemittanceCoveringDay(payingBranchDayId)

        val compId = TestFixtures.uuid()
        val auditsBefore = callerAuditCount()

        assertFailsWith<ForbiddenException> {
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
        assertEquals(auditsBefore, callerAuditCount(), "rejected create writes no audit rows")
        assertNull(CompensationRepository.findById(compId), "rejected create writes no compensation row")
    }

    @Test
    fun `create on submit-REMITTED day with reason and EDIT_PAST_DAY succeeds flagged`() {
        submitRemittanceCoveringDay(payingBranchDayId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        val compId = TestFixtures.uuid()
        val comp =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
                reason = "Coordinator correction",
            )

        assertNotNull(comp)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq CompensationTable.tableName) and
                            (AuditLogTable.recordId eq compId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `replay of pre-submit create after remittance submit acks without duplicate audit`() {
        val compId = TestFixtures.uuid()
        CompensationService.create(
            callerId = callerId,
            id = compId,
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = "First",
        )

        submitRemittanceCoveringDay(payingBranchDayId)

        val insertsBefore = compensationInsertAuditCount(compId)
        val replayed =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = "First",
            )

        assertEquals(compId, replayed.id)
        assertEquals(insertsBefore, compensationInsertAuditCount(compId), "replay writes no duplicate audit")
    }

    @Test
    fun `update on day REMITTED by remittance submit is rejected`() {
        val compId = TestFixtures.uuid()
        val created =
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )

        submitRemittanceCoveringDay(payingBranchDayId)

        val auditsBefore = callerAuditCount()
        assertFailsWith<ForbiddenException> {
            CompensationService.update(
                callerId = callerId,
                compensationId = compId,
                amount = BigDecimal("2000.00"),
                note = null,
                expectedVersion = created.version,
            )
        }
        assertEquals(auditsBefore, callerAuditCount(), "rejected update writes no audit rows")
    }

    @Test
    fun `create on lazily-PAST day without EDIT_PAST_DAY is rejected`() {
        val pastDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today.minusDays(1))

        assertFailsWith<ForbiddenException> {
            CompensationService.create(
                callerId = callerId,
                id = TestFixtures.uuid(),
                workBranchDayId = pastDayId,
                payingBranchDayId = pastDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
                note = null,
            )
        }
    }

    @Test
    fun `replay with different caller fails closed`() {
        val compId = TestFixtures.uuid()
        CompensationService.create(
            callerId = callerId,
            id = compId,
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )

        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "comp-lock-other")
        IdentityFixtures.grantAssignCompensation(otherCaller, sourceId)

        assertFailsWith<ConflictException> {
            CompensationService.create(
                callerId = otherCaller,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = payingBranchDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
    }

    @Test
    fun `replay with different paying day fails closed`() {
        val compId = TestFixtures.uuid()
        CompensationService.create(
            callerId = callerId,
            id = compId,
            workBranchDayId = workBranchDayId,
            payingBranchDayId = payingBranchDayId,
            userId = targetUserId,
            amount = BigDecimal("1500.00"),
            note = null,
        )

        val otherDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today.minusDays(2))

        assertFailsWith<NotFoundException> {
            CompensationService.create(
                callerId = callerId,
                id = compId,
                workBranchDayId = workBranchDayId,
                payingBranchDayId = otherDayId,
                userId = targetUserId,
                amount = BigDecimal("1500.00"),
                note = null,
            )
        }
    }

    @Test
    fun `concurrent remittance day transition serializes with compensation create gate`() {
        val blockedId = TestFixtures.uuid()

        // Observable barrier (#527, mirrors #509): the holder keeps the day row locked
        // with the same locked primitive remittance submit uses while the create
        // attempts its in-tx gate. Release happens only after the contender is observed
        // waiting on the holder's lock, so the create must block until the transition
        // commits, then read REMITTED and fail closed — never slipping an insert onto
        // the frozen day.
        val failure =
            runCatching {
                LockBarrier.withDayRemitBarrier(payingBranchDayId) {
                    CompensationService.create(
                        callerId = callerId,
                        id = blockedId,
                        workBranchDayId = workBranchDayId,
                        payingBranchDayId = payingBranchDayId,
                        userId = targetUserId,
                        amount = BigDecimal("1500.00"),
                        note = null,
                    )
                }
            }.exceptionOrNull()

        assertTrue(failure is ForbiddenException)
        assertNull(CompensationRepository.findById(blockedId))
        assertEquals(0L, compensationAuditCount(blockedId))
        assertEquals(DayStatus.REMITTED, BranchDayService.getEffectiveStatus(payingBranchDayId))
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

    private fun compensationInsertAuditCount(compId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq CompensationTable.tableName) and
                        (AuditLogTable.recordId eq compId) and
                        (AuditLogTable.action eq AuditAction.INSERT)
                }.count()
        }

    private fun compensationAuditCount(compId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq CompensationTable.tableName) and
                        (AuditLogTable.recordId eq compId)
                }.count()
        }

    private fun callerAuditCount(): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.changedBy eq callerId }
                .count()
        }
}
