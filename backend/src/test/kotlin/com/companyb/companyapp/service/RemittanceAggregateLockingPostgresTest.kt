package com.companyb.companyapp.service

import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.service.finance.remittance.RemittanceSubmissionResult
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * #506 — the remittance aggregate command boundary. Every header/line/day mutation locks
 * the parent before draft validation with the parent first in the lock order, and every
 * actual content change bumps the parent version. Barrier-controlled races prove submit
 * observes the exact versioned child set.
 */
class RemittanceAggregateLockingPostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()

    private val rangeStart = LocalDate.of(2026, 7, 1)
    private val rangeEnd = LocalDate.of(2026, 7, 15)
    private val dayDateA = LocalDate.of(2026, 7, 10)
    private val dayDateB = LocalDate.of(2026, 7, 11)

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "remittance-agg-lock")
        DatabaseTestHelper.insertTestBranch(branchId, "Agg Lock Branch ${TestFixtures.uuid()}")
        DatabaseTestHelper.grantSubmitRemittance(callerId, sourceId, branchId)
    }

    @Test
    fun `add breakdown bumps version and idempotent retry does not`() {
        val remittanceId = createDraft()
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, dayDateA)
        val before = RemittanceService.getRemittance(remittanceId).remittance
        val auditsBefore = callerAuditCount()

        val first = RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayId)
        assertNotNull(first)
        val afterFirst = RemittanceService.getRemittance(remittanceId).remittance
        assertEquals(before.version + 1, afterFirst.version)

        val second = RemittanceService.addDayBreakdown(callerId, remittanceId, first.id, dayId)
        assertEquals(first.id, second.id)
        assertEquals(afterFirst.version, RemittanceService.getRemittance(remittanceId).remittance.version)
        assertEquals(auditsBefore + 1, callerAuditCount())
    }

    @Test
    fun `remove breakdown bumps version`() {
        val remittanceId = createDraft()
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, dayDateA)
        val breakdownId = TestFixtures.uuid()
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)
        val afterAdd = RemittanceService.getRemittance(remittanceId).remittance

        RemittanceService.removeDayBreakdown(callerId, remittanceId, breakdownId)
        val afterRemove = RemittanceService.getRemittance(remittanceId).remittance
        assertEquals(afterAdd.version + 1, afterRemove.version)
        assertTrue(RemittanceService.getRemittance(remittanceId).dayBreakdowns.isEmpty())
    }

    @Test
    fun `stale submit after breakdown bump fails without partial writes`() {
        val remittanceId = createDraft()
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, dayDateA)
        RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayId)
        val current = RemittanceService.getRemittance(remittanceId)
        val auditsBefore = callerAuditCount()

        assertFailsWith<VersionMismatchException> {
            RemittanceService.submit(callerId, remittanceId, current.remittance.version - 1)
        }

        val after = RemittanceService.getRemittance(remittanceId)
        assertEquals(RemittanceStatus.DRAFT, after.remittance.status)
        assertEquals(current.remittance.version, after.remittance.version)
        assertEquals(1, after.dayBreakdowns.size)
        assertNull(snapshotOrNull(remittanceId))
        assertEquals(DayStatus.OPEN, dbDayStatus(dayId))
        assertEquals(auditsBefore, callerAuditCount())
    }

    @Test
    fun `concurrent add breakdown versus submit leaves exactly one winner with consistent state`() {
        val remittanceId = createDraft()
        val dayIdA = DatabaseTestHelper.createBranchDayForDate(branchId, dayDateA)
        val dayIdB = DatabaseTestHelper.createBranchDayForDate(branchId, dayDateB)
        RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayIdA)
        val draftVersion = RemittanceService.getRemittance(remittanceId).remittance.version

        val executor = Executors.newFixedThreadPool(THREADS)
        val ready = CountDownLatch(THREADS)
        val start = CountDownLatch(1)
        val submitFuture =
            executor.submit<Result<RemittanceSubmissionResult>> {
                ready.countDown()
                start.await()
                runCatching { RemittanceService.submit(callerId, remittanceId, draftVersion) }
            }
        val addFuture =
            executor.submit<Result<Unit>> {
                ready.countDown()
                start.await()
                runCatching {
                    RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayIdB)
                }.map { }
            }
        try {
            assertTrue(ready.await(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            val submitOutcome = submitFuture.get()
            val addOutcome = addFuture.get()
            assertEquals(1, listOf(submitOutcome.isSuccess, addOutcome.isSuccess).count { it })
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        val detail = RemittanceService.getRemittance(remittanceId)
        if (detail.remittance.status == RemittanceStatus.SUBMITTED) {
            assertEquals(1, detail.dayBreakdowns.size)
            assertNotNull(snapshotOrNull(remittanceId))
            assertEquals(DayStatus.REMITTED, dbDayStatus(dayIdA))
        } else {
            assertEquals(RemittanceStatus.DRAFT, detail.remittance.status)
            assertEquals(2, detail.dayBreakdowns.size)
            assertNull(snapshotOrNull(remittanceId))
            assertEquals(DayStatus.OPEN, dbDayStatus(dayIdA))
            assertEquals(DayStatus.OPEN, dbDayStatus(dayIdB))
        }
    }

    @Test
    fun `concurrent remove breakdown versus submit leaves exactly one winner`() {
        val remittanceId = createDraft()
        val dayIdA = DatabaseTestHelper.createBranchDayForDate(branchId, dayDateA)
        val dayIdB = DatabaseTestHelper.createBranchDayForDate(branchId, dayDateB)
        val breakdownA = TestFixtures.uuid()
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownA, dayIdA)
        RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayIdB)
        val draftVersion = RemittanceService.getRemittance(remittanceId).remittance.version

        val executor = Executors.newFixedThreadPool(THREADS)
        val ready = CountDownLatch(THREADS)
        val start = CountDownLatch(1)
        val submitFuture =
            executor.submit<Result<RemittanceSubmissionResult>> {
                ready.countDown()
                start.await()
                runCatching { RemittanceService.submit(callerId, remittanceId, draftVersion) }
            }
        val removeFuture =
            executor.submit<Result<Unit>> {
                ready.countDown()
                start.await()
                runCatching {
                    RemittanceService.removeDayBreakdown(callerId, remittanceId, breakdownA)
                }.map { }
            }
        try {
            assertTrue(ready.await(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            val submitOutcome = submitFuture.get()
            val removeOutcome = removeFuture.get()
            assertEquals(1, listOf(submitOutcome.isSuccess, removeOutcome.isSuccess).count { it })
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        val detail = RemittanceService.getRemittance(remittanceId)
        if (detail.remittance.status == RemittanceStatus.SUBMITTED) {
            assertEquals(2, detail.dayBreakdowns.size)
            assertNotNull(snapshotOrNull(remittanceId))
        } else {
            assertEquals(RemittanceStatus.DRAFT, detail.remittance.status)
            assertEquals(1, detail.dayBreakdowns.size)
            assertNull(snapshotOrNull(remittanceId))
        }
    }

    @Test
    fun `concurrent header shrink versus breakdown insert never orphans out-of-range content`() {
        val remittanceId = createDraft()
        val dayIdB = DatabaseTestHelper.createBranchDayForDate(branchId, dayDateB)
        val draftVersion = RemittanceService.getRemittance(remittanceId).remittance.version

        val executor = Executors.newFixedThreadPool(THREADS)
        val ready = CountDownLatch(THREADS)
        val start = CountDownLatch(1)
        val shrinkFuture =
            executor.submit<Result<Unit>> {
                ready.countDown()
                start.await()
                runCatching {
                    RemittanceService.updateHeader(
                        callerId = callerId,
                        remittanceId = remittanceId,
                        type = RemittanceType.SESSION,
                        method = RemittanceMethod.BANK_TRANSFER,
                        dateRangeStart = rangeStart,
                        dateRangeEnd = dayDateA,
                        expectedVersion = draftVersion,
                    )
                }.map { }
            }
        val addFuture =
            executor.submit<Result<Unit>> {
                ready.countDown()
                start.await()
                runCatching {
                    RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayIdB)
                }.map { }
            }
        try {
            assertTrue(ready.await(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            val shrinkOutcome = shrinkFuture.get()
            val addOutcome = addFuture.get()
            assertEquals(1, listOf(shrinkOutcome.isSuccess, addOutcome.isSuccess).count { it })
            val shrinkRejected = shrinkOutcome.exceptionOrNull() is ValidationException
            val addRejected = addOutcome.exceptionOrNull() is ValidationException
            assertTrue(shrinkRejected || addRejected)
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        val detail = RemittanceService.getRemittance(remittanceId)
        detail.dayBreakdowns.forEach { breakdown ->
            val date = dbBranchDayDate(breakdown.branchDayId)
            val afterStart = !date.isBefore(detail.remittance.dateRangeStart)
            val beforeEnd = !date.isAfter(detail.remittance.dateRangeEnd)
            assertTrue(afterStart && beforeEnd)
        }
    }

    @Test
    fun `concurrent headers with the same version let exactly one win without partial writes`() {
        val remittanceId = createDraft()
        val dayId = DatabaseTestHelper.createBranchDayForDate(branchId, dayDateA)
        RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayId)
        val draftVersion = RemittanceService.getRemittance(remittanceId).remittance.version
        val auditsBefore = callerAuditCount()

        val executor = Executors.newFixedThreadPool(THREADS)
        val ready = CountDownLatch(THREADS)
        val start = CountDownLatch(1)
        val futures =
            (1..THREADS).map {
                executor.submit<Result<Unit>> {
                    ready.countDown()
                    start.await()
                    runCatching {
                        RemittanceService.updateHeader(
                            callerId = callerId,
                            remittanceId = remittanceId,
                            type = RemittanceType.SESSION,
                            method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
                            dateRangeStart = rangeStart,
                            dateRangeEnd = rangeEnd,
                            expectedVersion = draftVersion,
                        )
                    }.map { }
                }
            }
        try {
            assertTrue(ready.await(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            val outcomes = futures.map { it.get() }
            assertEquals(1, outcomes.count { it.isSuccess })
            assertEquals(1, outcomes.count { it.exceptionOrNull() is VersionMismatchException })
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(EXECUTOR_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }

        assertEquals(draftVersion + 1, RemittanceService.getRemittance(remittanceId).remittance.version)
        assertEquals(auditsBefore + 1, callerAuditCount())
    }

    @Test
    fun `submit then undo releases exactly the original covered day set`() {
        val remittanceId = createDraft()
        val dayIdA = DatabaseTestHelper.createBranchDayForDate(branchId, dayDateA)
        val dayIdB = DatabaseTestHelper.createBranchDayForDate(branchId, dayDateB)
        RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayIdA)
        RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayIdB)
        val detailBefore = RemittanceService.getRemittance(remittanceId)
        val coveredBefore = detailBefore.dayBreakdowns.map { it.branchDayId }.toSet()

        val submittedVersion = submit(remittanceId)
        assertFailsWith<ValidationException> {
            RemittanceService.removeDayBreakdown(callerId, remittanceId, coveredBefore.first())
        }
        val undone = RemittanceService.undo(callerId, remittanceId, submittedVersion, "release exact set")

        assertEquals(RemittanceStatus.DRAFT, undone.status)
        val detailAfter = RemittanceService.getRemittance(remittanceId)
        val coveredAfter = detailAfter.dayBreakdowns.map { it.branchDayId }.toSet()
        assertEquals(coveredBefore, coveredAfter)
        assertNull(snapshotOrNull(remittanceId))
        val today = BranchDayService.currentOperationalDate()
        assertEquals(BranchDayService.evaluateStatus(DayStatus.OPEN, dayDateA, today), dbDayStatus(dayIdA))
        assertEquals(BranchDayService.evaluateStatus(DayStatus.OPEN, dayDateB, today), dbDayStatus(dayIdB))
    }

    private fun createDraft(): UUID {
        val id = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = callerId,
            id = id,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        return id
    }

    private fun submit(remittanceId: UUID): Int {
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        val result = RemittanceService.submit(callerId, remittanceId, version)
        assertNotNull(result)
        return result.remittance.version
    }

    private fun snapshotOrNull(remittanceId: UUID) =
        transaction {
            RemittanceFinancialSnapshotTable
                .selectAll()
                .where { RemittanceFinancialSnapshotTable.remittanceId eq remittanceId }
                .singleOrNull()
        }

    private fun dbDayStatus(dayId: UUID): DayStatus =
        transaction {
            BranchDayTable.selectAll().where { BranchDayTable.id eq dayId }.single()[BranchDayTable.status]
        }

    private fun dbBranchDayDate(dayId: UUID): LocalDate =
        transaction {
            BranchDayTable.selectAll().where { BranchDayTable.id eq dayId }.single()[BranchDayTable.date]
        }

    private fun callerAuditCount(): Long =
        transaction {
            AuditLogTable.selectAll().where { AuditLogTable.changedBy eq callerId }.count()
        }

    private companion object {
        const val THREADS = 2
        const val EXECUTOR_TIMEOUT_SECONDS = 30L
    }
}
