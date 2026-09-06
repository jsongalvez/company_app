package com.companyb.companyapp.service

import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.service.finance.remittance.RemittanceSubmissionResult
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #507 — REMITTED coverage is owned by the surviving SUBMITTED set, not by one remittance.
 * Undo releases a day only when no other SUBMITTED remittance still covers it.
 */
class RemittanceCrossTypeUndoPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    private val dayDateA = LocalDate.of(2026, 7, 10)
    private val dayDateB = LocalDate.of(2026, 7, 11)
    private val dayDateC = LocalDate.of(2026, 7, 12)

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "remittance-cross-undo")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Cross Undo Branch ${TestFixtures.uuid()}")
        IdentityFixtures.grantSubmitRemittance(callerId, sourceId, branchId)
    }

    @Test
    fun `undo SESSION first keeps REMITTED until PRODUCT undone`() {
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayDateA)
        val sessionId = createDraft(RemittanceType.SESSION)
        val productId = createDraft(RemittanceType.PRODUCT)
        addBreakdown(sessionId, dayId)
        addBreakdown(productId, dayId)
        val sessionVersion = submit(sessionId)
        val productVersion = submit(productId)
        assertEquals(DayStatus.REMITTED, dbDayStatus(dayId))

        val undoneSession = RemittanceService.undo(callerId, sessionId, sessionVersion, "fix session")
        assertEquals(RemittanceStatus.DRAFT, undoneSession.status)
        assertEquals(DayStatus.REMITTED, dbDayStatus(dayId))

        val undoneProduct = RemittanceService.undo(callerId, productId, productVersion, "fix product")
        assertEquals(RemittanceStatus.DRAFT, undoneProduct.status)
        assertEquals(expectedReleased(dayDateA), dbDayStatus(dayId))
    }

    @Test
    fun `undo PRODUCT first keeps REMITTED until SESSION undone`() {
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayDateA)
        val sessionId = createDraft(RemittanceType.SESSION)
        val productId = createDraft(RemittanceType.PRODUCT)
        addBreakdown(sessionId, dayId)
        addBreakdown(productId, dayId)
        val sessionVersion = submit(sessionId)
        val productVersion = submit(productId)

        RemittanceService.undo(callerId, productId, productVersion, "fix product")
        assertEquals(DayStatus.REMITTED, dbDayStatus(dayId))

        RemittanceService.undo(callerId, sessionId, sessionVersion, "fix session")
        assertEquals(expectedReleased(dayDateA), dbDayStatus(dayId))
    }

    @Test
    fun `overlapping multi-day sets release only uncovered days`() {
        val dayA = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayDateA)
        val dayB = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayDateB)
        val dayC = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayDateC)
        val sessionId = createDraft(RemittanceType.SESSION)
        val productId = createDraft(RemittanceType.PRODUCT)
        addBreakdown(sessionId, dayA)
        addBreakdown(sessionId, dayB)
        addBreakdown(productId, dayB)
        addBreakdown(productId, dayC)
        val sessionVersion = submit(sessionId)
        val productVersion = submit(productId)

        RemittanceService.undo(callerId, sessionId, sessionVersion, "fix session")
        assertEquals(expectedReleased(dayDateA), dbDayStatus(dayA))
        assertEquals(DayStatus.REMITTED, dbDayStatus(dayB))
        assertEquals(DayStatus.REMITTED, dbDayStatus(dayC))

        RemittanceService.undo(callerId, productId, productVersion, "fix product")
        assertEquals(expectedReleased(dayDateB), dbDayStatus(dayB))
        assertEquals(expectedReleased(dayDateC), dbDayStatus(dayC))
    }

    @Test
    fun `retained undo writes no branch-day audit, final release writes one`() {
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayDateA)
        val sessionId = createDraft(RemittanceType.SESSION)
        val productId = createDraft(RemittanceType.PRODUCT)
        addBreakdown(sessionId, dayId)
        addBreakdown(productId, dayId)
        val sessionVersion = submit(sessionId)
        val productVersion = submit(productId)

        val auditsBefore = branchDayAuditCount(dayId)
        RemittanceService.undo(callerId, sessionId, sessionVersion, "retained")
        assertEquals(auditsBefore, branchDayAuditCount(dayId))

        RemittanceService.undo(callerId, productId, productVersion, "final")
        assertEquals(auditsBefore + 1, branchDayAuditCount(dayId))
        assertEveryBranchDayAuditIsRealTransition(dayId)
    }

    @Test
    fun `concurrent cross-type submits both succeed with one REMITTED transition`() {
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayDateA)
        val sessionId = createDraft(RemittanceType.SESSION)
        val productId = createDraft(RemittanceType.PRODUCT)
        addBreakdown(sessionId, dayId)
        addBreakdown(productId, dayId)
        val sessionVersion = currentVersion(sessionId)
        val productVersion = currentVersion(productId)
        val auditsBefore = branchDayAuditCount(dayId)

        val executor = Executors.newFixedThreadPool(THREADS)
        val ready = CountDownLatch(THREADS)
        val start = CountDownLatch(1)
        val sessionFuture =
            executor.submit<Result<RemittanceSubmissionResult>> {
                ready.countDown()
                start.await()
                runCatching { RemittanceService.submit(callerId, sessionId, sessionVersion) }
            }
        val productFuture =
            executor.submit<Result<RemittanceSubmissionResult>> {
                ready.countDown()
                start.await()
                runCatching { RemittanceService.submit(callerId, productId, productVersion) }
            }
        try {
            assertTrue(ready.await(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            val sessionOutcome = sessionFuture.get()
            val productOutcome = productFuture.get()
            assertTrue(sessionOutcome.isSuccess)
            assertTrue(productOutcome.isSuccess)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        assertEquals(DayStatus.REMITTED, dbDayStatus(dayId))
        assertEquals(auditsBefore + 1, branchDayAuditCount(dayId))
        assertEveryBranchDayAuditIsRealTransition(dayId)
    }

    @Test
    fun `concurrent submit versus undo ends REMITTED with real audit transitions`() {
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayDateA)
        val sessionId = createDraft(RemittanceType.SESSION)
        val productId = createDraft(RemittanceType.PRODUCT)
        addBreakdown(sessionId, dayId)
        addBreakdown(productId, dayId)
        val sessionVersion = submit(sessionId)
        val productDraftVersion = currentVersion(productId)

        val executor = Executors.newFixedThreadPool(THREADS)
        val ready = CountDownLatch(THREADS)
        val start = CountDownLatch(1)
        val undoFuture =
            executor.submit<Result<Unit>> {
                ready.countDown()
                start.await()
                runCatching {
                    RemittanceService.undo(callerId, sessionId, sessionVersion, "race undo")
                }.map { }
            }
        val submitFuture =
            executor.submit<Result<RemittanceSubmissionResult>> {
                ready.countDown()
                start.await()
                runCatching { RemittanceService.submit(callerId, productId, productDraftVersion) }
            }
        try {
            assertTrue(ready.await(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            val undoOutcome = undoFuture.get()
            val submitOutcome = submitFuture.get()
            assertTrue(undoOutcome.isSuccess)
            assertTrue(submitOutcome.isSuccess)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        assertNotNull(RemittanceService.getRemittance(productId))
        assertEquals(DayStatus.REMITTED, dbDayStatus(dayId))
        assertEveryBranchDayAuditIsRealTransition(dayId)
    }

    private fun createDraft(type: RemittanceType): UUID {
        val id = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = callerId,
            id = id,
            type = type,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = TestFixtures.today,
        )
        return id
    }

    private fun addBreakdown(
        remittanceId: UUID,
        branchDayId: UUID,
    ) {
        RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), branchDayId)
    }

    private fun submit(remittanceId: UUID): Int {
        val version = currentVersion(remittanceId)
        val result = RemittanceService.submit(callerId, remittanceId, version)
        assertNotNull(result)
        return result.remittance.version
    }

    private fun currentVersion(remittanceId: UUID): Int =
        RemittanceService.getRemittance(remittanceId).remittance.version

    private fun dbDayStatus(dayId: UUID): DayStatus =
        transaction {
            BranchDayTable.selectAll().where { BranchDayTable.id eq dayId }.single()[BranchDayTable.status]
        }

    private fun expectedReleased(date: LocalDate): DayStatus =
        BranchDayService.evaluateStatus(DayStatus.OPEN, date, TestFixtures.today)

    private fun branchDayAuditCount(dayId: UUID): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where {
                    (AuditLogTable.auditTableName eq BranchDayTable.tableName) and
                        (AuditLogTable.recordId eq dayId) and
                        (AuditLogTable.action eq AuditAction.UPDATE)
                }.count()
        }

    private fun assertEveryBranchDayAuditIsRealTransition(dayId: UUID) {
        val rows =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq BranchDayTable.tableName) and
                            (AuditLogTable.recordId eq dayId) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.toList()
            }
        assertTrue(rows.isNotEmpty())
        rows.forEach { row ->
            val oldValue = row[AuditLogTable.oldValue].orEmpty()
            val newValue = row[AuditLogTable.newValue].orEmpty()
            assertTrue(oldValue != newValue, "branch-day audit must record an actual transition")
        }
    }

    private companion object {
        const val THREADS = 2
        const val EXECUTOR_TIMEOUT_SECONDS = 30L
    }
}
