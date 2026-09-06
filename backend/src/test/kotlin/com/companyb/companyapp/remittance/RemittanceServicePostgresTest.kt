package com.companyb.companyapp.remittance
import com.companyb.companyapp.audit.AuditLog
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.domain.AuditAction
import com.companyb.companyapp.domain.DayStatus
import com.companyb.companyapp.domain.ExpenseCategory
import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.remittance.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.remittance.RemittancePolicy
import com.companyb.companyapp.remittance.RemittanceRepository
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.remittance.RemittanceSubmissionResult
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

@Suppress("LargeClass")
class RemittanceServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private var sessionId: UUID? = null
    private var productSaleId: UUID? = null

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "remittance-caller")

        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Remittance Branch ${TestFixtures.uuid()}")

        IdentityFixtures.grantSubmitRemittance(callerId, sourceId, branchId)
    }

    @Test
    fun `create draft remittance succeeds`() {
        val remittanceId = TestFixtures.uuid()
        val dateRangeStart = LocalDate.of(2026, 7, 1)
        val dateRangeEnd = LocalDate.of(2026, 7, 15)

        val remittance =
            RemittanceService.createDraft(
                callerId = callerId,
                id = remittanceId,
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = dateRangeStart,
                dateRangeEnd = dateRangeEnd,
            )

        assertNotNull(remittance)
        assertEquals(remittanceId, remittance.id)
        assertEquals(RemittanceType.SESSION, remittance.type)
        assertEquals(RemittanceStatus.DRAFT, remittance.status)
        assertEquals(branchId, remittance.branchId)
        assertEquals(RemittanceMethod.BANK_TRANSFER, remittance.method)
        assertEquals(dateRangeStart, remittance.dateRangeStart)
        assertEquals(dateRangeEnd, remittance.dateRangeEnd)
        assertEquals(callerId, remittance.submittedBy)
        assertEquals(1, remittance.version)
    }

    @Test
    fun `create draft PRODUCT type succeeds`() {
        val remittanceId = TestFixtures.uuid()
        val dateRangeStart = LocalDate.of(2026, 7, 1)
        val dateRangeEnd = LocalDate.of(2026, 7, 15)

        val remittance =
            RemittanceService.createDraft(
                callerId = callerId,
                id = remittanceId,
                type = RemittanceType.PRODUCT,
                branchId = branchId,
                method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
                dateRangeStart = dateRangeStart,
                dateRangeEnd = dateRangeEnd,
            )

        assertEquals(RemittanceType.PRODUCT, remittance.type)
    }

    @Test
    fun `create draft idempotent duplicate returns existing`() {
        val remittanceId = TestFixtures.uuid()
        val dateRangeStart = LocalDate.of(2026, 7, 1)
        val dateRangeEnd = LocalDate.of(2026, 7, 15)

        val first =
            RemittanceService.createDraft(
                callerId = callerId,
                id = remittanceId,
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = dateRangeStart,
                dateRangeEnd = dateRangeEnd,
            )

        val second =
            RemittanceService.createDraft(
                callerId = callerId,
                id = remittanceId,
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = dateRangeStart,
                dateRangeEnd = dateRangeEnd,
            )

        assertEquals(first.id, second.id)
        assertEquals(first.version, second.version)
    }

    @Test
    fun `create draft without SUBMIT_REMITTANCE is allowed at service layer`() {
        IdentityFixtures.revokeAllCapabilities(callerId)

        val remittance =
            RemittanceService.createDraft(
                callerId = callerId,
                id = TestFixtures.uuid(),
                type = RemittanceType.SESSION,
                branchId = branchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = LocalDate.of(2026, 7, 15),
            )

        assertNotNull(remittance)
    }

    @Test
    fun `create draft with non-existent branch returns not found`() {
        val nonexistentBranchId = TestFixtures.uuid()
        IdentityFixtures.grantSubmitRemittance(callerId, sourceId, nonexistentBranchId)

        assertFailsWith<NotFoundException> {
            RemittanceService.createDraft(
                callerId = callerId,
                id = TestFixtures.uuid(),
                type = RemittanceType.SESSION,
                branchId = nonexistentBranchId,
                method = RemittanceMethod.BANK_TRANSFER,
                dateRangeStart = LocalDate.of(2026, 7, 1),
                dateRangeEnd = LocalDate.of(2026, 7, 15),
            )
        }
    }

    @Test
    fun `create draft writes audit log entry`() {
        val remittanceId = TestFixtures.uuid()

        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = LocalDate.of(2026, 7, 15),
        )

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.auditTableName eq RemittanceTable.tableName)
                    }.count()
            }
        assertTrue(auditCount > 0)
    }

    @Test
    fun `submit SESSION remittance succeeds with snapshot and branch day updates`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("500.00"))

        val actualVersion = RemittanceService.getRemittance(remittanceId).remittance.version

        val (result, duration) =
            measureTimedValue {
                RemittanceService.submit(callerId, remittanceId, actualVersion)
            }
        assertTrue(duration < 15.seconds, "remittance submit regressed: took $duration")

        assertNotNull(result)
        assertEquals(RemittanceStatus.SUBMITTED, result.remittance.status)
        assertTrue(result.remittance.version > 1)
        assertEquals(BigDecimal("500.00"), result.grossIncome)
        assertEquals(BigDecimal.ZERO, result.totalCompensation)
        assertEquals(BigDecimal.ZERO, result.totalExpenses)
        assertEquals(BigDecimal("500.00"), result.netIncome)

        val bd = transaction { BranchDayTable.selectAll().where { BranchDayTable.id eq branchDayId }.single() }
        assertEquals(DayStatus.REMITTED, bd[BranchDayTable.status])

        val snap =
            transaction {
                RemittanceFinancialSnapshotTable
                    .selectAll()
                    .where { RemittanceFinancialSnapshotTable.remittanceId eq remittanceId }
                    .singleOrNull()
            }
        assertNotNull(snap)
        assertEquals(BigDecimal("500.00"), snap[RemittanceFinancialSnapshotTable.netIncome])
    }

    @Test
    fun `submit PRODUCT remittance succeeds without snapshot`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftProductRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addProductLine(remittanceId, lineId, BigDecimal("200.00"))

        val actualVersion = RemittanceService.getRemittance(remittanceId).remittance.version

        val result = RemittanceService.submit(callerId, remittanceId, actualVersion)

        assertNotNull(result)
        assertEquals(RemittanceStatus.SUBMITTED, result.remittance.status)
        assertEquals(BigDecimal.ZERO, result.grossIncome)

        val snap =
            transaction {
                RemittanceFinancialSnapshotTable
                    .selectAll()
                    .where { RemittanceFinancialSnapshotTable.remittanceId eq remittanceId }
                    .singleOrNull()
            }
        assertNull(snap)
    }

    @Test
    fun `submit calculates compensation and expenses correctly`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()
        val compId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("1000.00"))
        addCompensation(compId, branchDayId, BigDecimal("200.00"))
        addExpense(branchDayId, BigDecimal("150.00"))

        val actualVersion = RemittanceService.getRemittance(remittanceId).remittance.version

        val result = RemittanceService.submit(callerId, remittanceId, actualVersion)

        assertNotNull(result)
        assertEquals(BigDecimal("1000.00"), result.grossIncome)
        assertEquals(BigDecimal("200.00"), result.totalCompensation)
        assertEquals(BigDecimal("150.00"), result.totalExpenses)
        assertEquals(BigDecimal("650.00"), result.netIncome)
    }

    @Test
    fun `submit with version mismatch throws conflict`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)

        val auditsBefore = callerAuditCount()

        assertFailsWith<ConflictException> {
            RemittanceService.submit(callerId, remittanceId, 99)
        }
        assertEquals(auditsBefore, callerAuditCount(), "conflicted submit writes no audit rows")
    }

    @Test
    fun `submit without SUBMIT_REMITTANCE is allowed at service layer`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        IdentityFixtures.revokeAllCapabilities(callerId)

        val result = RemittanceService.submit(callerId, remittanceId, 1)

        assertNotNull(result)
        assertEquals(RemittanceStatus.SUBMITTED, result.remittance.status)
    }

    @Test
    fun `submit non-existent remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.submit(callerId, TestFixtures.uuid(), 1)
        }
    }

    @Test
    fun `submit already submitted remittance throws version mismatch`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("100.00"))

        val v1 = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, v1)
        val auditsBefore = callerAuditCount()

        assertFailsWith<VersionMismatchException> {
            RemittanceService.submit(callerId, remittanceId, 99)
        }
        assertEquals(auditsBefore, callerAuditCount(), "duplicate submit writes no audit rows")
    }

    @Test
    fun `submit writes audit log entries for remittance and branch days`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("300.00"))

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.action eq AuditAction.UPDATE) and
                            (
                                AuditLogTable.auditTableName.eq(RemittanceTable.tableName) or
                                    AuditLogTable.auditTableName.eq(BranchDayTable.tableName)
                            )
                    }.count()
            }
        assertTrue(auditCount >= 2)
    }

    @Test
    fun `submit branch day audit preserves lazy past before status`() {
        val remittanceId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.action eq AuditAction.UPDATE) and
                            (AuditLogTable.auditTableName eq BranchDayTable.tableName) and
                            (AuditLogTable.recordId eq branchDayId)
                    }.single()
            }
        assertEquals("OPEN", TestFixtures.extractJsonField(audit[AuditLogTable.oldValue].orEmpty(), "status"))
        assertEquals(
            "REMITTED",
            TestFixtures.extractJsonField(audit[AuditLogTable.newValue].orEmpty(), "status"),
        )
    }

    @Test
    fun `submit branch day audit preserves current open before status`() {
        val remittanceId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveCurrentBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.action eq AuditAction.UPDATE) and
                            (AuditLogTable.auditTableName eq BranchDayTable.tableName) and
                            (AuditLogTable.recordId eq branchDayId)
                    }.single()
            }
        assertEquals("OPEN", TestFixtures.extractJsonField(audit[AuditLogTable.oldValue].orEmpty(), "status"))
        assertEquals(
            "REMITTED",
            TestFixtures.extractJsonField(audit[AuditLogTable.newValue].orEmpty(), "status"),
        )
    }

    @Test
    fun `submit with no day breakdowns succeeds with zero calculations`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)

        val result = RemittanceService.submit(callerId, remittanceId, 1)

        assertNotNull(result)
        assertEquals(BigDecimal.ZERO, result.grossIncome)
        assertEquals(BigDecimal.ZERO, result.totalCompensation)
        assertEquals(BigDecimal.ZERO, result.totalExpenses)
        assertEquals(BigDecimal.ZERO, result.netIncome)
    }

    // ===== #320 contract proofs — command-owned SERIALIZABLE submission (ADR-0024) =====

    @Test
    fun `audit failure inside the submit command rolls the whole submission back`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, TestFixtures.uuid(), branchDayId)

        // Exercises the exact composition the migrated submit command runs — store writes and
        // Branch Day transitions on the command transaction, then the audit insert into that
        // same transaction. A failing audit statement must abort everything.
        val error =
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
                    BranchDayService.markDaysRemittedInTransaction(listOf(branchDayId))
                    AuditLog.record(
                        tableName = RemittanceTable.tableName,
                        recordId = remittanceId,
                        action = AuditAction.UPDATE,
                        changedBy = callerId,
                        oldValue = "{not-valid-json",
                    )
                }
            }.exceptionOrNull()

        assertNotNull(error, "malformed jsonb audit payload must fail the statement")

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
        assertEquals(RemittanceStatus.DRAFT, status, "mutation rolled back with the failed audit")
        assertEquals(DayStatus.OPEN, dayStatus, "branch-day transition rolled back with the failed audit")
        assertEquals(0L, auditCount, "no partial audit row survived")
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

    @Suppress("LongMethod")
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

        val outcome =
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
        assertEquals(RemittanceStatus.SUBMITTED, outcome.status)
        assertEquals(draftVersion + 1, outcome.version, "one winner, one version bump")
        assertEquals(DayStatus.REMITTED, outcome.dayStatus)
        assertEquals(1L, outcome.remittanceAudits, "one remittance audit row total")
        assertEquals(1L, outcome.dayAudits, "one branch-day audit row total")
    }

    private fun createDraftRemittance(remittanceId: UUID) {
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            // #483 — covers both the fixed July fixtures and the operational-today breakdown.
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = TestFixtures.today,
        )
    }

    private fun createDraftProductRemittance(remittanceId: UUID) {
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
            // #483 — same widened range as the SESSION draft helper above.
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = TestFixtures.today,
        )
    }

    private fun resolveBranchDay(): UUID {
        val today = LocalDate.of(2026, 7, 10)
        val bd = BranchDayService.resolveOrCreate(branchId, today)
        return bd.id
    }

    private fun resolveCurrentBranchDay(): UUID {
        val bd = BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
        return bd.id
    }

    private fun addDayBreakdown(
        remittanceId: UUID,
        breakdownId: UUID,
        branchDayId: UUID,
    ) {
        RemittanceService.addDayBreakdown(
            callerId = callerId,
            remittanceId = remittanceId,
            id = breakdownId,
            branchDayId = branchDayId,
        )
    }

    private fun addSessionLine(
        remittanceId: UUID,
        lineId: UUID,
        amount: BigDecimal,
    ) {
        val sId = createSession()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sId,
            productSaleId = null,
            amount = amount,
        )
    }

    private fun addProductLine(
        remittanceId: UUID,
        lineId: UUID,
        amount: BigDecimal,
    ) {
        val psId = createProductSale()
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            id = lineId,
            type = RemittanceLineType.PRODUCT_SALE,
            sessionId = null,
            productSaleId = psId,
            amount = amount,
        )
    }

    private var sessionCreated = false
    private var productSaleCreated = false

    private fun createSession(): UUID {
        if (sessionCreated) return sessionId!!
        ensureClientExists()
        val sId = TestFixtures.uuid()
        val branchDayId = resolveBranchDay()
        SessionClientFixtures.insertTestSession(
            id = sId,
            clientId = clientId,
            branchDayId = branchDayId,
        )
        sessionId = sId
        sessionCreated = true
        return sId
    }

    private fun createProductSale(): UUID {
        if (productSaleCreated) return productSaleId!!
        val psId = TestFixtures.uuid()
        val catId = TestFixtures.uuid()
        val prodId = TestFixtures.uuid()
        val branchDayId = resolveBranchDay()
        CommerceFinanceFixtures.insertTestCategory(catId, "Cat ${psId.toString().take(8)}")
        CommerceFinanceFixtures.insertTestProduct(prodId, "Prod ${psId.toString().take(8)}", catId)
        CommerceFinanceFixtures.insertTestProductSale(
            id = psId,
            branchDayId = branchDayId,
            productId = prodId,
            handledBy = callerId,
        )
        productSaleId = psId
        productSaleCreated = true
        return psId
    }

    private fun ensureClientExists() {
        transaction {
            val exists = ClientTable.selectAll().where { ClientTable.id eq clientId }.count() > 0
            if (!exists) {
                ClientTable.insert {
                    it[ClientTable.id] = clientId
                    it[ClientTable.firstName] = "Test"
                    it[ClientTable.lastName] = "Client"
                    it[ClientTable.gender] = "M"
                    it[ClientTable.age] = 30
                }
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun addCompensation(
        id: UUID,
        payingBranchDayId: UUID,
        amount: BigDecimal,
    ) {
        transaction {
            CompensationTable.insert {
                it[CompensationTable.id] = id
                it[CompensationTable.workBranchDayId] = payingBranchDayId
                it[CompensationTable.payingBranchDayId] = payingBranchDayId
                it[CompensationTable.userId] = callerId
                it[CompensationTable.amount] = amount
                it[CompensationTable.assignedBy] = callerId
            }
        }
    }

    @Suppress("SameParameterValue")
    private fun addExpense(
        branchDayId: UUID,
        amount: BigDecimal,
    ) {
        transaction {
            ExpenseTable.insert {
                it[ExpenseTable.id] = TestFixtures.uuid()
                it[ExpenseTable.branchDayId] = branchDayId
                it[ExpenseTable.amount] = amount
                it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                it[ExpenseTable.createdBy] = callerId
            }
        }
    }

    private fun callerAuditCount(): Long =
        transaction {
            AuditLogTable
                .selectAll()
                .where { AuditLogTable.changedBy eq callerId }
                .count()
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
