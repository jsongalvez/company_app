package com.companyb.companyapp.remittance
import com.companyb.companyapp.audit.AuditContext
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.audit.AuditedRow
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.remittance.RemittancePolicy
import com.companyb.companyapp.remittance.RemittanceRepository
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.remittance.RemittanceSubmissionResult
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
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

class RemittanceSubmitGuardPostgresTest : RemittanceServicePostgresBase() {
    // ===== #320 contract proofs — command-owned SERIALIZABLE submission (ADR-0024) =====
    @Test
    fun `audit failure inside the submit command rolls the whole submission back`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, TestFixtures.uuid(), branchDayId)

        // Exercises the exact composition the migrated submit command runs — store writes and
        // Branch Day transitions (which write their own audit, #603) on the command transaction,
        // then the audit insert into that same transaction. A failing audit statement must abort
        // everything.
        val error = runFailingSubmitWithBadAudit(remittanceId, branchDayId)

        assertNotNull(error, "malformed jsonb audit payload must fail the statement")

        val rollback = readSubmitRollback(remittanceId, branchDayId)
        assertEquals(RemittanceStatus.DRAFT, rollback.status, "mutation rolled back with the failed audit")
        assertEquals(DayStatus.OPEN, rollback.dayStatus, "branch-day transition rolled back with the failed audit")
        assertEquals(0L, rollback.remittanceAudits, "no partial audit row survived")
        assertEquals(0L, rollback.dayAudits, "no partial branch-day audit row survived")
    }

    @Test
    fun `failed submit writes no audit rows`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, TestFixtures.uuid(), branchDayId)

        assertFailsWith<VersionMismatchException> {
            RemittanceService.submit(callerId, remittanceId, 999)
        }

        val (status, dayStatus, auditCount) =
            transaction {
                val remittance = RemittanceTable.selectAll().where { RemittanceTable.id eq remittanceId }.single()
                val day = BranchDayTable.selectAll().where { BranchDayTable.id eq branchDayId }.single()
                val audits =
                    AuditLogTable
                        .selectAll()
                        .where {
                            (AuditLogTable.recordId eq remittanceId) and
                                (AuditLogTable.action eq AuditAction.UPDATE)
                        }.count()
                Triple(remittance[RemittanceTable.status], day[BranchDayTable.status], audits)
            }
        assertEquals(RemittanceStatus.DRAFT, status, "row untouched")
        assertEquals(DayStatus.OPEN, dayStatus, "no day transition for a failed mutation")
        assertEquals(0L, auditCount, "no audit row for a failed mutation")
    }

    @Test
    fun `concurrent submit of the same draft lets exactly one win`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, TestFixtures.uuid(), branchDayId)
        // #506 — day breakdowns are versioned content changes, so the draft is at v2 here.
        val draftVersion = RemittanceService.getRemittance(remittanceId).remittance.version

        val threads = 2
        val executor = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        val futures =
            (1..threads).map {
                executor.submit<Result<RemittanceSubmissionResult>> {
                    ready.countDown()
                    start.await()
                    runCatching { RemittanceService.submit(callerId, remittanceId, draftVersion) }
                }
            }

        try {
            assertTrue(ready.await(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
            start.countDown()
            val outcomes = futures.map { it.get() }
            assertEquals(1, outcomes.count { it.isSuccess }, "exactly one concurrent submit wins")
            assertEquals(1, outcomes.count { it.isFailure }, "the loser is rejected")
        } finally {
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(EXECUTOR_TERMINATION_SECONDS, TimeUnit.SECONDS))
        }

        val outcome = readConcurrentSubmitOutcome(remittanceId, branchDayId)
        assertEquals(RemittanceStatus.SUBMITTED, outcome.status)
        assertEquals(draftVersion + 1, outcome.version, "one winner, one version bump")
        assertEquals(DayStatus.REMITTED, outcome.dayStatus)
        assertEquals(1L, outcome.remittanceAudits, "one remittance audit row total")
        assertEquals(1L, outcome.dayAudits, "one branch-day audit row total")
    }

    // #925 aggregate persisted-range pre-gate (the #924 fail-closed class): individually
    // legal lines can still sum past NUMERIC(10,2) — the frozen snapshot must 400 at
    // submit instead of 500ing on the insert.
    @Test
    fun `submit rejects snapshot sums over NUMERIC(10,2) range`() {
        val remittanceId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, TestFixtures.uuid(), branchDayId)
        addSessionLine(remittanceId, TestFixtures.uuid(), BigDecimal("99999999.99"))
        // A second line needs its own session (duplicate-source guard 409s a repeat).
        val secondSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = secondSessionId,
            clientId = clientId,
            branchDayId = branchDayId,
            sessionStatus = SessionStatus.COMPLETED,
        )
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            NewRemittanceLine(
                id = TestFixtures.uuid(),
                type = RemittanceLineType.SESSION,
                sessionId = secondSessionId,
                productSaleId = null,
                amount = BigDecimal("99999999.99"),
            ),
        )

        val actualVersion = RemittanceService.getRemittance(remittanceId).remittance.version

        assertFailsWith<ValidationException> {
            RemittanceService.submit(callerId, remittanceId, actualVersion)
        }
    }

    // #925 aggregate-guard scoping pin: PRODUCT remittances freeze no snapshot, so an
    // over-range aggregate must still submit (the SESSION-only guard above must not leak).
    @Test
    fun `submit PRODUCT remittance with over-range aggregate still succeeds without snapshot`() {
        val remittanceId = TestFixtures.uuid()

        createDraftProductRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, TestFixtures.uuid(), branchDayId)
        addProductLine(remittanceId, TestFixtures.uuid(), BigDecimal("99999999.99"))
        // Second line needs its own sale (duplicate-source guard 409s a repeat).
        val secondSaleId = TestFixtures.uuid()
        val secondCatId = TestFixtures.uuid()
        val secondProdId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestCategory(secondCatId, "Cat ${secondSaleId.toString().take(8)}")
        CommerceFinanceFixtures.insertTestProduct(secondProdId, "Prod ${secondSaleId.toString().take(8)}", secondCatId)
        CommerceFinanceFixtures.insertTestProductSale(
            id = secondSaleId,
            branchDayId = branchDayId,
            productId = secondProdId,
            handledBy = callerId,
        )
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            NewRemittanceLine(
                id = TestFixtures.uuid(),
                type = RemittanceLineType.PRODUCT_SALE,
                sessionId = null,
                productSaleId = secondSaleId,
                amount = BigDecimal("99999999.99"),
            ),
        )

        val actualVersion = RemittanceService.getRemittance(remittanceId).remittance.version

        val result = RemittanceService.submit(callerId, remittanceId, actualVersion)

        assertEquals(RemittanceStatus.SUBMITTED, result.remittance.status)
        assertNull(RemittanceService.getRemittance(remittanceId).snapshot)
    }

    private fun runFailingSubmitWithBadAudit(
        remittanceId: UUID,
        branchDayId: UUID,
    ): Throwable? =
        runCatching {
            transaction {
                val before =
                    RemittanceRepository.lockByIdInTransaction(remittanceId) ?: error("missing draft")
                RemittancePolicy.assertSubmittable(before.status, before.version, 1, remittanceId)
                RemittanceRepository.markSubmittedInTransaction(
                    remittanceId,
                    1,
                    callerId,
                    LocalDate.of(2026, 7, 10),
                )
                BranchDayService.markDaysRemittedInTransaction(listOf(branchDayId), changedBy = callerId)
                AuditLog.record(
                    context = AuditContext(changedBy = callerId),
                    row = AuditedRow(RemittanceTable.tableName, remittanceId),
                    action = AuditAction.UPDATE,
                    oldValue = "{not-valid-json",
                )
            }
        }.exceptionOrNull()

    private fun readSubmitRollback(
        remittanceId: UUID,
        branchDayId: UUID,
    ): SubmitOutcome =
        transaction {
            val remittance = RemittanceTable.selectAll().where { RemittanceTable.id eq remittanceId }.single()
            val day = BranchDayTable.selectAll().where { BranchDayTable.id eq branchDayId }.single()
            val audits =
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.recordId eq remittanceId) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.count()
            val dayAudits =
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.recordId eq branchDayId) and
                            (AuditLogTable.auditTableName eq BranchDayTable.tableName) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.count()
            SubmitOutcome(
                status = remittance[RemittanceTable.status],
                version = remittance[RemittanceTable.version],
                dayStatus = day[BranchDayTable.status],
                remittanceAudits = audits,
                dayAudits = dayAudits,
            )
        }

    private fun readConcurrentSubmitOutcome(
        remittanceId: UUID,
        branchDayId: UUID,
    ): SubmitOutcome =
        transaction {
            val remittance = RemittanceTable.selectAll().where { RemittanceTable.id eq remittanceId }.single()
            val day = BranchDayTable.selectAll().where { BranchDayTable.id eq branchDayId }.single()
            val rAudits =
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.recordId eq remittanceId) and
                            (AuditLogTable.auditTableName eq RemittanceTable.tableName) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.count()
            val dAudits =
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.recordId eq branchDayId) and
                            (AuditLogTable.auditTableName eq BranchDayTable.tableName) and
                            (AuditLogTable.action eq AuditAction.UPDATE)
                    }.count()
            SubmitOutcome(
                remittance[RemittanceTable.status],
                remittance[RemittanceTable.version],
                day[BranchDayTable.status],
                rAudits,
                dAudits,
            )
        }

    private data class SubmitOutcome(
        val status: RemittanceStatus,
        val version: Int,
        val dayStatus: DayStatus,
        val remittanceAudits: Long,
        val dayAudits: Long,
    )

    private companion object {
        private const val EXECUTOR_TERMINATION_SECONDS = 30L
    }
}
