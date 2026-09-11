package com.companyb.companyapp.remittance

import com.companyb.companyapp.branchday.BranchDayTable
import com.companyb.companyapp.contracts.branchday.DayStatus
import com.companyb.companyapp.contracts.finance.ExpenseCategory
import com.companyb.companyapp.contracts.remittance.RemittanceLineType
import com.companyb.companyapp.contracts.remittance.RemittanceMethod
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceType
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.finance.CompensationTable
import com.companyb.companyapp.finance.ExpenseTable
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * #866 — submit-time lines↔breakdowns reconciliation. Gross is summed over remittance lines
 * while compensation/expenses are summed over day breakdowns; the two sets must describe
 * the same days or the immutable snapshot mixes different days' books.
 */
class RemittanceSubmitCoveragePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()

    private val rangeStart = LocalDate.of(2026, 7, 1)
    private val rangeEnd = LocalDate.of(2026, 7, 15)
    private val dayX = LocalDate.of(2026, 7, 10)
    private val dayY = LocalDate.of(2026, 7, 11)

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "remittance-coverage-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Coverage Branch ${TestFixtures.uuid()}")
        IdentityFixtures.grantSubmitRemittance(callerId, sourceId, branchId)
        SessionClientFixtures.insertTestClient(clientId)
    }

    @Test
    fun `submit with lines but no breakdowns is rejected before any state change`() {
        val remittanceId = createDraft()
        val dayIdX = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayX)
        val sessionX = insertSession(dayIdX)
        addSessionLine(remittanceId, sessionX, BigDecimal("500.00"))
        val version = currentVersion(remittanceId)

        assertFailsWith<ValidationException> {
            RemittanceService.submit(callerId, remittanceId, version)
        }
        assertEquals(RemittanceStatus.DRAFT, currentStatus(remittanceId))
        assertNull(RemittanceService.getRemittance(remittanceId).snapshot)
        assertEquals(DayStatus.OPEN, dbDayStatus(dayIdX))
    }

    @Test
    fun `submit with line source outside breakdowns is rejected before any state change`() {
        val remittanceId = createDraft()
        val dayIdX = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayX)
        val dayIdY = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayY)
        val sessionX = insertSession(dayIdX)
        addSessionLine(remittanceId, sessionX, BigDecimal("500.00"))
        RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayIdY)
        val version = currentVersion(remittanceId)

        assertFailsWith<ValidationException> {
            RemittanceService.submit(callerId, remittanceId, version)
        }
        assertEquals(RemittanceStatus.DRAFT, currentStatus(remittanceId))
        assertNull(RemittanceService.getRemittance(remittanceId).snapshot)
        assertEquals(DayStatus.OPEN, dbDayStatus(dayIdX))
        assertEquals(DayStatus.OPEN, dbDayStatus(dayIdY))
    }

    @Test
    fun `submit with lines subset of breakdowns still submits with verified snapshot math`() {
        val remittanceId = createDraft()
        val dayIdX = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayX)
        val dayIdY = BranchWorkforceFixtures.createBranchDayForDate(branchId, dayY)
        val sessionX = insertSession(dayIdX)
        addSessionLine(remittanceId, sessionX, BigDecimal("1000.00"))
        RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayIdX)
        RemittanceService.addDayBreakdown(callerId, remittanceId, TestFixtures.uuid(), dayIdY)
        addCompensation(dayIdX, BigDecimal("200.00"))
        addExpense(dayIdY, BigDecimal("150.00"))
        val version = currentVersion(remittanceId)

        val result = RemittanceService.submit(callerId, remittanceId, version)

        assertNotNull(result)
        assertEquals(RemittanceStatus.SUBMITTED, result.remittance.status)
        assertEquals(BigDecimal("1000.00"), result.grossIncome)
        assertEquals(BigDecimal("200.00"), result.totalCompensation)
        assertEquals(BigDecimal("150.00"), result.totalExpenses)
        assertEquals(BigDecimal("650.00"), result.netIncome)
        assertEquals(DayStatus.REMITTED, dbDayStatus(dayIdX))
        assertEquals(DayStatus.REMITTED, dbDayStatus(dayIdY))
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

    private fun insertSession(branchDayId: UUID): UUID {
        val sessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = sessionId,
            clientId = clientId,
            branchDayId = branchDayId,
        )
        return sessionId
    }

    private fun addSessionLine(
        remittanceId: UUID,
        sessionId: UUID,
        amount: BigDecimal,
    ) {
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            id = TestFixtures.uuid(),
            type = RemittanceLineType.SESSION,
            sessionId = sessionId,
            productSaleId = null,
            amount = amount,
        )
    }

    private fun addCompensation(
        payingBranchDayId: UUID,
        amount: BigDecimal,
    ) {
        transaction {
            CompensationTable.insert {
                it[CompensationTable.id] = TestFixtures.uuid()
                it[CompensationTable.workBranchDayId] = payingBranchDayId
                it[CompensationTable.payingBranchDayId] = payingBranchDayId
                it[CompensationTable.userId] = callerId
                it[CompensationTable.amount] = amount
                it[CompensationTable.assignedBy] = callerId
            }
        }
    }

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

    private fun currentVersion(remittanceId: UUID): Int =
        RemittanceService.getRemittance(remittanceId).remittance.version

    private fun currentStatus(remittanceId: UUID): RemittanceStatus =
        RemittanceService.getRemittance(remittanceId).remittance.status

    private fun dbDayStatus(dayId: UUID): DayStatus =
        transaction {
            BranchDayTable.selectAll().where { BranchDayTable.id eq dayId }.single()[BranchDayTable.status]
        }
}
