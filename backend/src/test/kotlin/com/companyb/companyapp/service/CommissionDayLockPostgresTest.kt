package com.companyb.companyapp.service

import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ForbiddenException
import com.companyb.companyapp.repository.CommissionManualInclusionRepository
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.CommissionManualInclusionTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.commission.CommissionService
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * #516 — manual commission inclusions serialize with the remittance REMITTED
 * transition. The create gates through the locked branch-day read, so a
 * pre-freeze sale read can no longer commit an inclusion onto a
 * financially-frozen day without coordinator authority + reason.
 */
class CommissionDayLockPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val targetUserId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val categoryId = TestFixtures.uuid()
    private val productId = TestFixtures.uuid()

    private lateinit var branchDayId: UUID
    private lateinit var productSaleId: UUID

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "commission-lock-caller")
        DatabaseTestHelper.insertTestUser(targetUserId, "commission-lock-target")
        DatabaseTestHelper.insertTestBranch(branchId, "Test Commission Lock Branch")
        DatabaseTestHelper.insertTestCategory(categoryId, "Test Commission Lock Category")
        DatabaseTestHelper.insertTestProduct(productId, "Commission Lock Product", categoryId)
        branchDayId = DatabaseTestHelper.createBranchDayForToday(branchId)
        productSaleId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestProductSale(
            id = productSaleId,
            branchDayId = branchDayId,
            productId = productId,
            handledBy = callerId,
        )
    }

    @Test
    fun `create on day REMITTED by remittance submit is rejected with no row`() {
        submitRemittanceCoveringDay(branchDayId)

        val inclusionId = TestFixtures.uuid()
        val auditsBefore = callerAuditCount()

        assertFailsWith<ForbiddenException> {
            CommissionService.createManualInclusion(
                callerId = callerId,
                id = inclusionId,
                productSaleId = productSaleId,
                userId = targetUserId,
                isIncluded = true,
                reason = null,
            )
        }
        assertNull(
            CommissionManualInclusionRepository.findById(inclusionId),
            "rejected create writes no inclusion row",
        )
        assertEquals(auditsBefore, callerAuditCount(), "rejected create writes no audit rows")
    }

    @Test
    fun `create on submit-REMITTED day with reason and EDIT_PAST_DAY succeeds flagged`() {
        submitRemittanceCoveringDay(branchDayId)
        DatabaseTestHelper.grantEditPastDay(callerId, branchId, sourceId)

        val inclusionId = TestFixtures.uuid()
        val inclusion =
            CommissionService.createManualInclusion(
                callerId = callerId,
                id = inclusionId,
                productSaleId = productSaleId,
                userId = targetUserId,
                isIncluded = true,
                reason = "Coordinator correction",
            )

        assertNotNull(inclusion)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq CommissionManualInclusionTable.tableName) and
                            (AuditLogTable.recordId eq inclusionId)
                    }.single()
            }
        assertEquals(true, audit[AuditLogTable.isFlagged])
        assertEquals("Coordinator correction", audit[AuditLogTable.reason])
    }

    @Test
    fun `create on lazily-PAST day without EDIT_PAST_DAY is rejected`() {
        val pastDayId = DatabaseTestHelper.createBranchDayForDate(branchId, TestFixtures.today.minusDays(1))
        val pastSaleId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestProductSale(
            id = pastSaleId,
            branchDayId = pastDayId,
            productId = productId,
            handledBy = callerId,
        )

        assertFailsWith<ForbiddenException> {
            CommissionService.createManualInclusion(
                callerId = callerId,
                id = TestFixtures.uuid(),
                productSaleId = pastSaleId,
                userId = targetUserId,
                isIncluded = true,
                reason = null,
            )
        }
    }

    @Test
    fun `concurrent remittance day transition serializes with inclusion create gate`() {
        val blockedId = TestFixtures.uuid()
        val dayLocked = CountDownLatch(1)

        // #516 barrier (mirrors #509/#511): the submit side holds the day row lock
        // (the same locked primitive remittance submit uses) while the create
        // attempts its in-tx gate. The create must block on the lock until the
        // transition commits, then read REMITTED and fail closed — never slipping
        // an inclusion row onto the frozen day.
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
                    CommissionService.createManualInclusion(
                        callerId = callerId,
                        id = blockedId,
                        productSaleId = productSaleId,
                        userId = targetUserId,
                        isIncluded = true,
                        reason = null,
                    )
                }.exceptionOrNull()
            }
        submitter.join(BARRIER_JOIN_MILLIS)

        assertTrue(failure is ForbiddenException, "expected ForbiddenException but got $failure")
        assertTrue(
            blockedFor.inWholeMilliseconds >= BARRIER_MIN_BLOCKED_MILLIS,
            "create did not block on the day lock: finished in $blockedFor",
        )
        assertNull(
            CommissionManualInclusionRepository.findById(blockedId),
            "blocked create writes no inclusion row",
        )
        assertEquals(0L, inclusionAuditCount(blockedId))
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

    private fun inclusionAuditCount(inclusionId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq CommissionManualInclusionTable.tableName) and
                        (AuditLogTable.recordId eq inclusionId)
                }.count()
        }

    companion object {
        private const val BARRIER_HOLD_MILLIS = 5000L
        private const val BARRIER_MIN_BLOCKED_MILLIS = 3000L
        private const val BARRIER_WAIT_SECONDS = 10L
        private const val BARRIER_JOIN_MILLIS = 30000L
    }
}
