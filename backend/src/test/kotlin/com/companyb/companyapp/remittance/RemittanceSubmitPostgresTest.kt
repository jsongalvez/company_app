package com.companyb.companyapp.remittance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.ConflictException
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.exception.VersionMismatchException
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.remittance.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.reporting.DailySalesSummaryService
import com.companyb.companyapp.session.SessionService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.insert
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

class RemittanceSubmitPostgresTest : RemittanceServicePostgresBase() {
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
    fun `submit excludes voided session lines from snapshot`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val branchDayId = resolveCurrentBranchDay()
        addDayBreakdown(remittanceId, TestFixtures.uuid(), branchDayId)
        ensureClientExists()
        val secondClientId = SessionClientFixtures.insertTestClient()
        val voidedSessionId = TestFixtures.uuid()
        val activeSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = voidedSessionId,
            clientId = clientId,
            branchDayId = branchDayId,
            sessionStatus = SessionStatus.COMPLETED,
        )
        SessionClientFixtures.insertTestSession(
            id = activeSessionId,
            clientId = secondClientId,
            branchDayId = branchDayId,
            sessionStatus = SessionStatus.COMPLETED,
        )
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            NewRemittanceLine(
                id = TestFixtures.uuid(),
                type = RemittanceLineType.SESSION,
                sessionId = voidedSessionId,
                productSaleId = null,
                amount = BigDecimal("1000.00"),
            ),
        )
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            NewRemittanceLine(
                id = TestFixtures.uuid(),
                type = RemittanceLineType.SESSION,
                sessionId = activeSessionId,
                productSaleId = null,
                amount = BigDecimal("400.00"),
            ),
        )
        SessionService.voidSession(callerId, voidedSessionId, TestFixtures.uuid(), "Created in error")

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        val result = RemittanceService.submit(callerId, remittanceId, version)

        assertNotNull(result)
        assertEquals(BigDecimal("400.00"), result.grossIncome)
        assertEquals(BigDecimal("400.00"), result.netIncome)
        val snapshot = RemittanceService.getRemittance(remittanceId).snapshot
        assertNotNull(snapshot)
        assertEquals(BigDecimal("400.00"), snapshot.grossIncome)
    }

    @Test
    fun `submit snapshot gross matches daily summary for a controlled day`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val branchDayId = resolveCurrentBranchDay()
        addDayBreakdown(remittanceId, TestFixtures.uuid(), branchDayId)
        ensureClientExists()
        val completedSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = completedSessionId,
            clientId = clientId,
            branchDayId = branchDayId,
            sessionStatus = SessionStatus.COMPLETED,
            finalPrice = BigDecimal("2500.00"),
        )
        // Non-COMPLETED sessions on the same day carry no realizable income: the picker
        // hides them, the guard rejects them, and both gross authorities must exclude them.
        listOf(SessionStatus.PENDING, SessionStatus.NO_SHOW, SessionStatus.CANCELLED).forEach { status ->
            SessionClientFixtures.insertTestSession(
                id = TestFixtures.uuid(),
                clientId = SessionClientFixtures.insertTestClient(),
                branchDayId = branchDayId,
                sessionStatus = status,
                finalPrice = BigDecimal("9999.00"),
            )
        }
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            NewRemittanceLine(
                id = TestFixtures.uuid(),
                type = RemittanceLineType.SESSION,
                sessionId = completedSessionId,
                productSaleId = null,
                amount = BigDecimal("2500.00"),
            ),
        )

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        val result = RemittanceService.submit(callerId, remittanceId, version)

        assertEquals(BigDecimal("2500.00"), result.grossIncome)
        val summary = DailySalesSummaryService.getDailySummary(branchId, TestFixtures.today)
        assertEquals(0, BigDecimal("2500.00").compareTo(summary.grossIncome))
        assertEquals(0, result.grossIncome.compareTo(summary.grossIncome))
        assertEquals(
            listOf(completedSessionId),
            RemittanceService.findSessionsInRange(branchId, TestFixtures.today, TestFixtures.today).map { it.id },
        )
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
        // #866 — submit now requires non-empty coverage; the authz intent stays the same.
        addDayBreakdown(remittanceId, TestFixtures.uuid(), resolveBranchDay())

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        val result = RemittanceService.submit(callerId, remittanceId, version)

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
    fun `submit writes snapshot insert audit with frozen amounts`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createDraftRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, breakdownId, branchDayId)
        addSessionLine(remittanceId, lineId, BigDecimal("300.00"))

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val snapshot = RemittanceService.getRemittance(remittanceId).snapshot
        assertNotNull(snapshot)
        val audit =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.action eq AuditAction.INSERT) and
                            (AuditLogTable.auditTableName eq RemittanceFinancialSnapshotTable.tableName) and
                            (AuditLogTable.recordId eq remittanceId)
                    }.single()
            }
        val newValue = audit[AuditLogTable.newValue].orEmpty()
        assertTrue(newValue.contains(snapshot.grossIncome.toPlainString()))
        assertTrue(newValue.contains(snapshot.totalCompensation.toPlainString()))
        assertTrue(newValue.contains(snapshot.totalExpenses.toPlainString()))
        assertTrue(newValue.contains(snapshot.netIncome.toPlainString()))
    }

    @Test
    fun `submit PRODUCT remittance writes no snapshot audit`() {
        val remittanceId = TestFixtures.uuid()
        createDraftProductRemittance(remittanceId)
        val branchDayId = resolveBranchDay()
        addDayBreakdown(remittanceId, TestFixtures.uuid(), branchDayId)

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val snapshotAudits =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.auditTableName eq RemittanceFinancialSnapshotTable.tableName) and
                            (AuditLogTable.recordId eq remittanceId)
                    }.count()
            }
        assertEquals(0L, snapshotAudits)
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
    fun `submit with no day breakdowns is rejected before any state change`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val auditsBefore = callerAuditCount()

        // #866 — an empty submit would freeze a zero snapshot while consuming the day's
        // submitted slot and arming the SUBMITTED-only overlap exclusion.
        assertFailsWith<ValidationException> {
            RemittanceService.submit(callerId, remittanceId, 1)
        }
        assertEquals(RemittanceStatus.DRAFT, RemittanceService.getRemittance(remittanceId).remittance.status)
        assertNull(RemittanceService.getRemittance(remittanceId).snapshot)
        assertEquals(auditsBefore, callerAuditCount(), "rejected empty submit writes no audit rows")
    }

    private fun resolveCurrentBranchDay(): UUID {
        val bd = BranchDayService.resolveOrCreate(branchId, TestFixtures.today)
        return bd.id
    }

    // #595 single-scenario fixture helper keeps its full insert shape (mirrors production signature).
    @Suppress("SameParameterValue") // #595
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

    // #595 single-scenario fixture helper keeps its full insert shape (mirrors production signature).
    @Suppress("SameParameterValue") // #595
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
}
