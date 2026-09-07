@file:Suppress("LargeClass")

package com.companyb.companyapp.remittance
import com.companyb.companyapp.audit.AuditLogTable
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.client.ClientTable
import com.companyb.companyapp.contracts.audit.AuditAction
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.remittance.RemittanceDayBreakdownTable
import com.companyb.companyapp.remittance.RemittanceService
import com.companyb.companyapp.remittance.RemittanceTable
import com.companyb.companyapp.session.SessionVoidTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemittanceReadBackServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val otherBranchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()
    private var sessionId: UUID? = null
    private var productSaleId: UUID? = null
    private var sessionCreated = false
    private var productSaleCreated = false

    private val rangeStart = LocalDate.of(2026, 7, 1)
    private val rangeEnd = LocalDate.of(2026, 7, 15)
    private val branchDayDate = LocalDate.of(2026, 7, 10)

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "remittance-read")

        BranchWorkforceFixtures.insertTestBranch(branchId, "Test Remittance Branch ${TestFixtures.uuid()}")

        BranchWorkforceFixtures.insertTestBranch(otherBranchId, "Other Remittance Branch ${TestFixtures.uuid()}")

        IdentityFixtures.grantSubmitRemittance(callerId, sourceId, branchId)
    }

    // ──────────────────────────────────────────────
    // G1 — draft list
    // ──────────────────────────────────────────────

    @Test
    fun `list returns only remittances of the branch`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val otherRemittanceId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = callerId,
            id = otherRemittanceId,
            type = RemittanceType.SESSION,
            branchId = otherBranchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )

        val result = RemittanceService.listRemittances(branchId, null)

        assertEquals(1, result.size)
        assertEquals(remittanceId, result[0].remittance.id)
    }

    @Test
    fun `list filters by status`() {
        val draftId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = callerId,
            id = draftId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        val submittedId = TestFixtures.uuid()
        createSubmittedSessionRemittance(submittedId, BigDecimal("100.00"))

        val drafts = RemittanceService.listRemittances(branchId, RemittanceStatus.DRAFT)
        val submitted = RemittanceService.listRemittances(branchId, RemittanceStatus.SUBMITTED)
        val all = RemittanceService.listRemittances(branchId, null)

        assertEquals(listOf(draftId), drafts.map { it.remittance.id })
        assertEquals(listOf(submittedId), submitted.map { it.remittance.id })
        assertEquals(2, all.size)
    }

    @Test
    fun `list orders by createdAt desc with id desc tiebreak`() {
        val older = TestFixtures.uuid()
        val newer = TestFixtures.uuid()
        val olderCreatedAt = OffsetDateTime.of(2026, 7, 1, 10, 0, 0, 0, ZoneOffset.UTC)
        val newerCreatedAt = olderCreatedAt.plusMinutes(5)
        insertDraftDirect(older, olderCreatedAt, LocalDate.of(2026, 7, 1))
        insertDraftDirect(newer, newerCreatedAt, LocalDate.of(2026, 7, 2))

        val result = RemittanceService.listRemittances(branchId, null)

        assertEquals(listOf(newer, older), result.map { it.remittance.id })
    }

    @Test
    fun `list shows net income for submitted SESSION rows only`() {
        val sessionRemittanceId = TestFixtures.uuid()
        val productRemittanceId = TestFixtures.uuid()
        createSubmittedSessionRemittance(sessionRemittanceId, BigDecimal("500.00"))
        createSubmittedProductRemittance(productRemittanceId, BigDecimal("200.00"))

        val result = RemittanceService.listRemittances(branchId, null).associateBy { it.remittance.id }

        assertEquals(BigDecimal("500.00"), result.getValue(sessionRemittanceId).netIncome)
        assertNull(result.getValue(productRemittanceId).netIncome)
    }

    @Test
    fun `list for missing branch returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.listRemittances(TestFixtures.uuid(), null)
        }
    }

    // ──────────────────────────────────────────────
    // G2 — sessions in range
    // ──────────────────────────────────────────────

    @Test
    fun `sessions in range returns client name price and time`() {
        val sId = createSession(branchDayDate)
        val otherBranchDayId = BranchWorkforceFixtures.createBranchDayForDate(otherBranchId, branchDayDate)
        val otherSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = otherSessionId,
            clientId = clientId,
            branchDayId = otherBranchDayId,
            sessionStatus = SessionStatus.COMPLETED,
        )

        val result = RemittanceService.findSessionsInRange(branchId, rangeStart, rangeEnd)

        assertEquals(1, result.size)
        assertEquals(sId, result[0].id)
        assertEquals("Test Client", result[0].clientName)
        assertEquals(BigDecimal("2500.00"), result[0].finalPrice)
    }

    @Test
    fun `sessions in range excludes out-of-range dates`() {
        createSession(branchDayDate)
        val outsideDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, LocalDate.of(2026, 8, 1))
        val outsideSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = outsideSessionId,
            clientId = clientId,
            branchDayId = outsideDayId,
            sessionStatus = SessionStatus.COMPLETED,
        )

        val result = RemittanceService.findSessionsInRange(branchId, rangeStart, rangeEnd)

        assertEquals(1, result.size)
    }

    @Test
    fun `sessions in range returns null client name for anonymized client`() {
        val anonymizedClientId = TestFixtures.uuid()
        SessionClientFixtures.insertTestClient(anonymizedClientId)
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        val anonSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = anonSessionId,
            clientId = anonymizedClientId,
            branchDayId = dayId,
        )
        transaction {
            ClientTable.update({ ClientTable.id eq anonymizedClientId }) {
                it[ClientTable.firstName] = null
                it[ClientTable.lastName] = null
            }
        }

        val result = RemittanceService.findSessionsInRange(branchId, rangeStart, rangeEnd)

        assertEquals(1, result.size)
        assertNull(result[0].clientName)
    }

    @Test
    fun `sessions in range excludes voided sessions`() {
        val sId = createSession(branchDayDate)
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        val voidedSessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = voidedSessionId,
            clientId = clientId,
            branchDayId = dayId,
            sessionStatus = SessionStatus.COMPLETED,
        )
        transaction {
            SessionVoidTable.insertIgnore {
                it[SessionVoidTable.sessionId] = voidedSessionId
                it[SessionVoidTable.voidedBy] = callerId
                it[SessionVoidTable.voidReason] = "Test void"
            }
        }

        val result = RemittanceService.findSessionsInRange(branchId, rangeStart, rangeEnd)

        assertEquals(listOf(sId), result.map { it.id })
    }

    @Test
    fun `sessions in range for missing branch returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.findSessionsInRange(TestFixtures.uuid(), rangeStart, rangeEnd)
        }
    }

    // ──────────────────────────────────────────────
    // G3 — product sales in range
    // ──────────────────────────────────────────────

    @Test
    fun `product sales in range returns product name and amount`() {
        val psId = createProductSale(branchDayDate)
        val otherBranchDayId = BranchWorkforceFixtures.createBranchDayForDate(otherBranchId, branchDayDate)
        val otherPsId = TestFixtures.uuid()
        val catId = TestFixtures.uuid()
        val prodId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestCategory(catId, "Cat ${TestFixtures.uuid()}")
        CommerceFinanceFixtures.insertTestProduct(prodId, "Other Product", catId)
        CommerceFinanceFixtures.insertTestProductSale(
            id = otherPsId,
            branchDayId = otherBranchDayId,
            productId = prodId,
            handledBy = callerId,
            productName = "Other Product",
        )

        val result = RemittanceService.findProductSalesInRange(branchId, rangeStart, rangeEnd)

        assertEquals(1, result.size)
        assertEquals(psId, result[0].id)
        assertEquals("Test Product", result[0].productName)
        assertEquals(BigDecimal("100.00"), result[0].totalAmountAtTime)
    }

    @Test
    fun `product sales in range for missing branch returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.findProductSalesInRange(TestFixtures.uuid(), rangeStart, rangeEnd)
        }
    }

    // ──────────────────────────────────────────────
    // G4 — branch days in range
    // ──────────────────────────────────────────────

    @Test
    fun `branch days in range applies lazy effective status`() {
        val today = TestFixtures.today
        BranchWorkforceFixtures.createBranchDayForDate(branchId, today.plusDays(10))
        BranchWorkforceFixtures.createBranchDayForDate(branchId, today.minusDays(5))
        val remittedDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, today.minusDays(10))
        val submittedId = TestFixtures.uuid()
        // #483 — the covered day must sit inside the draft range; use the query window itself.
        RemittanceService.createDraft(
            callerId = callerId,
            id = submittedId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = today.minusDays(15),
            dateRangeEnd = today.plusDays(15),
        )
        val breakdownId = TestFixtures.uuid()
        RemittanceService.addDayBreakdown(callerId, submittedId, breakdownId, remittedDayId)
        val version = RemittanceService.getRemittance(submittedId).remittance.version
        RemittanceService.submit(callerId, submittedId, version)

        val result =
            RemittanceService
                .findBranchDaysInRange(
                    branchId,
                    today.minusDays(15),
                    today.plusDays(15),
                ).associateBy { it.date }

        assertEquals(DayStatus.REMITTED, result.getValue(today.minusDays(10)).status)
        assertEquals(DayStatus.PAST, result.getValue(today.minusDays(5)).status)
        assertEquals(DayStatus.OPEN, result.getValue(today.plusDays(10)).status)
    }

    @Test
    fun `branch days in range excludes days outside range and other branches`() {
        val outsideDayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, LocalDate.of(2026, 9, 1))
        val otherBranchDayId = BranchWorkforceFixtures.createBranchDayForDate(otherBranchId, branchDayDate)

        val result = RemittanceService.findBranchDaysInRange(branchId, rangeStart, rangeEnd)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `branch days in range for missing branch returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.findBranchDaysInRange(TestFixtures.uuid(), rangeStart, rangeEnd)
        }
    }

    // ──────────────────────────────────────────────
    // G5 — day-breakdown DELETE
    // ──────────────────────────────────────────────

    @Test
    fun `remove day breakdown succeeds and writes audit row`() {
        val remittanceId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)

        val removed = RemittanceService.removeDayBreakdown(callerId, remittanceId, breakdownId)

        assertEquals(breakdownId, removed.id)
        assertTrue(RemittanceService.getRemittance(remittanceId).dayBreakdowns.isEmpty())
        val auditCount =
            transaction {
                AuditLogTable
                    .selectAll()
                    .where {
                        (AuditLogTable.changedBy eq callerId) and
                            (AuditLogTable.action eq AuditAction.DELETE) and
                            (AuditLogTable.auditTableName eq RemittanceDayBreakdownTable.tableName) and
                            (AuditLogTable.recordId eq breakdownId)
                    }.count()
            }
        assertTrue(auditCount == 1L)
    }

    @Test
    fun `remove day breakdown on submitted remittance throws validation`() {
        val remittanceId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)
        addSessionLine(remittanceId, lineId, BigDecimal("100.00"))
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        assertFailsWith<ValidationException> {
            RemittanceService.removeDayBreakdown(callerId, remittanceId, breakdownId)
        }
    }

    @Test
    fun `remove day breakdown with wrong parent returns not found`() {
        val remittanceId = TestFixtures.uuid()
        val otherRemittanceId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        RemittanceService.createDraft(
            callerId = callerId,
            id = otherRemittanceId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)

        assertFailsWith<NotFoundException> {
            RemittanceService.removeDayBreakdown(callerId, otherRemittanceId, breakdownId)
        }
    }

    @Test
    fun `remove missing day breakdown returns not found`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)

        assertFailsWith<NotFoundException> {
            RemittanceService.removeDayBreakdown(callerId, remittanceId, TestFixtures.uuid())
        }
    }

    @Test
    fun `remove day breakdown on missing remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.removeDayBreakdown(callerId, TestFixtures.uuid(), TestFixtures.uuid())
        }
    }

    // ──────────────────────────────────────────────
    // G6 — snapshot block in detail
    // ──────────────────────────────────────────────

    @Test
    fun `detail includes snapshot for submitted SESSION remittance`() {
        val remittanceId = TestFixtures.uuid()
        createSubmittedSessionRemittance(remittanceId, BigDecimal("500.00"))

        val detail = RemittanceService.getRemittance(remittanceId)

        assertNotNull(detail.snapshot)
        assertEquals(BigDecimal("500.00"), detail.snapshot.grossIncome)
        assertEquals(BigDecimal("500.00"), detail.snapshot.netIncome)
    }

    @Test
    fun `detail has no snapshot for draft or PRODUCT remittance`() {
        val draftId = TestFixtures.uuid()
        createDraftRemittance(draftId)
        val productId = TestFixtures.uuid()
        createSubmittedProductRemittance(productId, BigDecimal("200.00"))

        assertNull(RemittanceService.getRemittance(draftId).snapshot)
        assertNull(RemittanceService.getRemittance(productId).snapshot)
    }

    // ──────────────────────────────────────────────
    // G7 — drift
    // ──────────────────────────────────────────────

    @Test
    fun `drift equals frozen when nothing changed after submit`() {
        val remittanceId = TestFixtures.uuid()
        createSubmittedSessionRemittance(remittanceId, BigDecimal("1000.00"))

        val drift = RemittanceService.getDrift(remittanceId)

        assertEquals(BigDecimal("1000.00"), drift.frozen.grossIncome)
        assertTrue(drift.frozen.totalCompensation.compareTo(drift.currentCompensation) == 0)
        assertTrue(drift.frozen.totalExpenses.compareTo(drift.currentExpenses) == 0)
        assertTrue(drift.frozen.netIncome.compareTo(drift.currentNet) == 0)
    }

    @Test
    fun `drift reflects later compensation and expense changes`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)
        addSessionLine(remittanceId, lineId, BigDecimal("1000.00"))
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val compId = TestFixtures.uuid()
        transaction {
            CompensationTable.insert {
                it[CompensationTable.id] = compId
                it[CompensationTable.workBranchDayId] = dayId
                it[CompensationTable.payingBranchDayId] = dayId
                it[CompensationTable.userId] = callerId
                it[CompensationTable.amount] = BigDecimal("200.00")
                it[CompensationTable.assignedBy] = callerId
            }
            ExpenseTable.insert {
                it[ExpenseTable.id] = TestFixtures.uuid()
                it[ExpenseTable.branchDayId] = dayId
                it[ExpenseTable.amount] = BigDecimal("150.00")
                it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                it[ExpenseTable.createdBy] = callerId
            }
        }

        val drift = RemittanceService.getDrift(remittanceId)

        assertEquals(BigDecimal("200.00"), drift.currentCompensation)
        assertEquals(BigDecimal("150.00"), drift.currentExpenses)
        assertEquals(BigDecimal("650.00"), drift.currentNet)
        assertEquals(BigDecimal("1000.00"), drift.frozen.netIncome)
    }

    @Test
    fun `drift excludes soft-deleted expenses`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)
        addSessionLine(remittanceId, lineId, BigDecimal("1000.00"))
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val expenseId = TestFixtures.uuid()
        transaction {
            ExpenseTable.insert {
                it[ExpenseTable.id] = expenseId
                it[ExpenseTable.branchDayId] = dayId
                it[ExpenseTable.amount] = BigDecimal("300.00")
                it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
                it[ExpenseTable.createdBy] = callerId
                it[ExpenseTable.deletedBy] = callerId
                it[ExpenseTable.deletedAt] = CurrentTimestampWithTimeZone
            }
        }

        val drift = RemittanceService.getDrift(remittanceId)

        assertEquals(BigDecimal.ZERO, drift.currentExpenses)
        assertEquals(BigDecimal("1000.00"), drift.currentNet)
    }

    @Test
    fun `drift returns not found without snapshot`() {
        val remittanceId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)

        assertFailsWith<NotFoundException> {
            RemittanceService.getDrift(remittanceId)
        }
    }

    @Test
    fun `drift on missing remittance returns not found`() {
        assertFailsWith<NotFoundException> {
            RemittanceService.getDrift(TestFixtures.uuid())
        }
    }

    // ──────────────────────────────────────────────
    // helpers
    // ──────────────────────────────────────────────

    private fun createDraftRemittance(remittanceId: UUID) {
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
    }

    @Suppress("LongMethod")
    private fun createSubmittedSessionRemittance(
        remittanceId: UUID,
        lineAmount: BigDecimal,
    ) {
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()
        createDraftRemittance(remittanceId)
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)
        addSessionLine(remittanceId, lineId, lineAmount)
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)
    }

    private fun createSubmittedProductRemittance(
        remittanceId: UUID,
        lineAmount: BigDecimal,
    ) {
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
            dateRangeStart = rangeStart,
            dateRangeEnd = rangeEnd,
        )
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, branchDayDate)
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, dayId)
        addProductLine(remittanceId, lineId, lineAmount)
        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)
    }

    private fun insertDraftDirect(
        remittanceId: UUID,
        createdAt: OffsetDateTime,
        submittedDate: LocalDate,
    ) {
        // locals dodge the Exposed insert/update table receiver, which shadows test fields
        // that share names with columns (branchId, submittedDate, createdAt)
        val targetBranchId = branchId
        val targetSubmittedDate = submittedDate
        transaction {
            RemittanceTable.insert {
                it[RemittanceTable.id] = remittanceId
                it[RemittanceTable.type] = RemittanceType.SESSION
                it[RemittanceTable.branchId] = targetBranchId
                it[RemittanceTable.method] = RemittanceMethod.BANK_TRANSFER
                it[RemittanceTable.submittedDate] = targetSubmittedDate
                it[RemittanceTable.dateRangeStart] = rangeStart
                it[RemittanceTable.dateRangeEnd] = rangeEnd
                it[RemittanceTable.submittedBy] = callerId
            }
            val targetCreatedAt = createdAt
            RemittanceTable.update({ RemittanceTable.id eq remittanceId }) {
                it[RemittanceTable.createdAt] = targetCreatedAt
            }
        }
    }

    private fun addSessionLine(
        remittanceId: UUID,
        lineId: UUID,
        amount: BigDecimal,
    ) {
        val sId = createSession(branchDayDate)
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
        val psId = createProductSale(branchDayDate)
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

    private fun createSession(dayDate: LocalDate): UUID {
        if (sessionCreated) return sessionId!!
        SessionClientFixtures.insertTestClient(clientId)
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayDate)
        val sId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = sId,
            clientId = clientId,
            branchDayId = dayId,
        )
        sessionId = sId
        sessionCreated = true
        return sId
    }

    private fun createProductSale(dayDate: LocalDate): UUID {
        if (productSaleCreated) return productSaleId!!
        val psId = TestFixtures.uuid()
        val catId = TestFixtures.uuid()
        val prodId = TestFixtures.uuid()
        val dayId = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayDate)
        CommerceFinanceFixtures.insertTestCategory(catId, "Cat ${psId.toString().take(8)}")
        CommerceFinanceFixtures.insertTestProduct(prodId, "Prod ${psId.toString().take(8)}", catId)
        CommerceFinanceFixtures.insertTestProductSale(
            id = psId,
            branchDayId = dayId,
            productId = prodId,
            handledBy = callerId,
        )
        productSaleId = psId
        productSaleCreated = true
        return psId
    }
}
