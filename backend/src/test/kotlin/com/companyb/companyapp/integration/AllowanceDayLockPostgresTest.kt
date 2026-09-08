package com.companyb.companyapp.integration

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.finance.AllowanceRepository
import com.companyb.companyapp.finance.AllowanceService
import com.companyb.companyapp.finance.AllowanceTable
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
import kotlin.test.assertTrue

/**
 * Allowance create serializes with the remittance REMITTED transition.
 * The create gates through the locked branch-day read, so a pre-freeze
 * authorization can no longer write onto a financially-frozen day.
 */
class AllowanceDayLockPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val targetUserId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "allowance-lock-caller")
        IdentityFixtures.insertTestUser(targetUserId, "allowance-lock-target")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Allowance Lock Branch")
        branchDayId = BranchWorkforceFixtures.createBranchDayForToday(branchId)
        IdentityFixtures.grantAssignCompensation(callerId, sourceId)
    }

    @Test
    fun `create on day REMITTED by remittance submit is rejected with no row`() {
        submitRemittanceCoveringDay(branchDayId)

        val allowanceId = TestFixtures.uuid()
        val auditsBefore = callerAuditCount()

        assertFailsWith<ForbiddenException> {
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )
        }
        assertEquals(auditsBefore, callerAuditCount(), "rejected create writes no audit rows")
        assertTrue(
            AllowanceRepository.findByBranchDayId(branchDayId).none { it.id == allowanceId },
            "rejected create writes no allowance row",
        )
    }

    @Test
    fun `create on submit-REMITTED day with reason and EDIT_PAST_DAY succeeds flagged`() {
        submitRemittanceCoveringDay(branchDayId)
        BranchWorkforceFixtures.grantEditPastDay(callerId, branchId, sourceId)

        val allowanceId = TestFixtures.uuid()
        val allowance =
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
                reason = "Coordinator correction",
            )

        assertNotNull(allowance)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq AllowanceTable.tableName) and
                            (AuditLogTable.recordId eq allowanceId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `replay of pre-submit create after remittance submit acks without duplicate audit`() {
        val allowanceId = TestFixtures.uuid()
        AllowanceService.create(
            callerId = callerId,
            id = allowanceId,
            branchDayId = branchDayId,
            userId = targetUserId,
            amount = BigDecimal("500.00"),
        )

        submitRemittanceCoveringDay(branchDayId)

        val insertsBefore = allowanceInsertAuditCount(allowanceId)
        val replayed =
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )

        assertEquals(allowanceId, replayed.id)
        assertEquals(insertsBefore, allowanceInsertAuditCount(allowanceId), "replay writes no duplicate audit")
    }

    @Test
    fun `create on lazily-PAST day without EDIT_PAST_DAY is rejected`() {
        val pastDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today.minusDays(1))

        assertFailsWith<ForbiddenException> {
            AllowanceService.create(
                callerId = callerId,
                id = TestFixtures.uuid(),
                branchDayId = pastDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )
        }
    }

    @Test
    fun `replay with different caller fails closed`() {
        val allowanceId = TestFixtures.uuid()
        AllowanceService.create(
            callerId = callerId,
            id = allowanceId,
            branchDayId = branchDayId,
            userId = targetUserId,
            amount = BigDecimal("500.00"),
        )

        val otherCaller = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(otherCaller, "allowance-lock-other")
        IdentityFixtures.grantAssignCompensation(otherCaller, sourceId)

        assertFailsWith<ConflictException> {
            AllowanceService.create(
                callerId = otherCaller,
                id = allowanceId,
                branchDayId = branchDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )
        }
    }

    @Test
    fun `replay with different day fails closed`() {
        val allowanceId = TestFixtures.uuid()
        AllowanceService.create(
            callerId = callerId,
            id = allowanceId,
            branchDayId = branchDayId,
            userId = targetUserId,
            amount = BigDecimal("500.00"),
        )

        val otherDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, TestFixtures.today.minusDays(2))

        assertFailsWith<NotFoundException> {
            AllowanceService.create(
                callerId = callerId,
                id = allowanceId,
                branchDayId = otherDayId,
                userId = targetUserId,
                amount = BigDecimal("500.00"),
            )
        }
    }

    @Test
    fun `concurrent remittance day transition serializes with allowance create gate`() {
        val blockedId = TestFixtures.uuid()

        // Observable barrier (#527): the holder keeps the day row locked with the same
        // locked primitive remittance submit uses while the create attempts its in-tx
        // gate. Release happens only after the contender is observed waiting on the
        // holder's lock, so the create must block until the transition commits, then
        // read REMITTED and fail closed — never slipping an insert onto the frozen day.
        val failure =
            runCatching {
                LockBarrier.withDayRemitBarrier(branchDayId, callerId) {
                    AllowanceService.create(
                        callerId = callerId,
                        id = blockedId,
                        branchDayId = branchDayId,
                        userId = targetUserId,
                        amount = BigDecimal("500.00"),
                    )
                }
            }.exceptionOrNull()

        assertTrue(failure is ForbiddenException)
        assertTrue(
            AllowanceRepository.findByBranchDayId(branchDayId).none { it.id == blockedId },
            "blocked create writes no allowance row",
        )
        assertEquals(0L, allowanceAuditCount(blockedId))
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

    private fun allowanceInsertAuditCount(allowanceId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq AllowanceTable.tableName) and
                        (AuditLogTable.recordId eq allowanceId) and
                        (AuditLogTable.action eq AuditAction.INSERT)
                }.count()
        }

    private fun allowanceAuditCount(allowanceId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq AllowanceTable.tableName) and
                        (AuditLogTable.recordId eq allowanceId)
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
