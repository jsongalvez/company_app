package com.companyb.companyapp.service
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MonthlyRemittanceSummaryServicePostgresTest : BasePostgresTest() {
    private val currentMonth = TestFixtures.currentMonth
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val clientId = TestFixtures.uuid()

    override fun initTestData() {
        IdentityFixtures.insertTestUser(callerId, "summary-caller")
        BranchWorkforceFixtures.insertTestBranch(branchId, "Monthly Summary Branch ${TestFixtures.uuid()}")
        SessionClientFixtures.insertTestClient(clientId)
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
        IdentityFixtures.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.SUBMIT_REMITTANCE,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
    }

    @Test
    fun `returns 404 when no remittance data exists for given month`() {
        assertFailsWith<NotFoundException> {
            getCurrentMonthSummary(branchId)
        }
    }

    @Test
    fun `returns correct summary for a single SESSION remittance`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createSubmittedSessionRemittance(remittanceId, lineId, breakdownId, BigDecimal("1000.00"))

        val summary = getCurrentMonthSummary(branchId)

        assertEquals(1, summary.totalRemittances)
        assertEquals(1, summary.sessionCount)
        assertEquals(0, summary.productCount)
        assertEquals(0, BigDecimal("1000.00").compareTo(summary.grossIncome))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalCompensation))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalExpenses))
        assertEquals(0, BigDecimal("1000.00").compareTo(summary.netIncome))
    }

    @Test
    fun `returns correct summary with compensation and expenses`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()
        val branchDayId = resolveBranchDay(currentMonth.atDay(10))

        createDraftRemittance(remittanceId, currentMonth.atDay(1), currentMonth.atEndOfMonth())
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, branchDayId)

        CommerceFinanceFixtures.insertTestCompensation(
            branchDayId,
            callerId,
            BigDecimal("300.00"),
            assignedBy = callerId,
        )
        CommerceFinanceFixtures.insertTestExpense(branchDayId, callerId, BigDecimal("150.00"))

        val sId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = sId,
            clientId = clientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("2500.00"),
            finalPrice = BigDecimal("2500.00"),
        )
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sId,
            productSaleId = null,
            amount = BigDecimal("2000.00"),
        )

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)

        val summary = getCurrentMonthSummary(branchId)

        assertEquals(1, summary.totalRemittances)
        assertEquals(0, BigDecimal("2000.00").compareTo(summary.grossIncome))
        assertEquals(0, BigDecimal("300.00").compareTo(summary.totalCompensation))
        assertEquals(0, BigDecimal("150.00").compareTo(summary.totalExpenses))
        assertEquals(0, BigDecimal("1550.00").compareTo(summary.netIncome))
    }

    @Test
    fun `returns correct summary for a PRODUCT remittance`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createSubmittedProductRemittance(remittanceId, lineId, breakdownId)

        val summary = getCurrentMonthSummary(branchId)

        assertEquals(1, summary.totalRemittances)
        assertEquals(0, summary.sessionCount)
        assertEquals(1, summary.productCount)
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.grossIncome))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.netIncome))
    }

    @Test
    fun `aggregates multiple remittances in same month`() {
        val rem1Id = TestFixtures.uuid()
        val rem2Id = TestFixtures.uuid()
        createSubmittedSessionRemittance(rem1Id, TestFixtures.uuid(), TestFixtures.uuid(), BigDecimal("500.00"))
        createSubmittedProductRemittance(rem2Id, TestFixtures.uuid(), TestFixtures.uuid())

        val summary = getCurrentMonthSummary(branchId)

        assertEquals(2, summary.totalRemittances)
        assertEquals(1, summary.sessionCount)
        assertEquals(1, summary.productCount)
        assertEquals(0, BigDecimal("500.00").compareTo(summary.grossIncome))
    }

    @Test
    fun `returns 404 without VIEW_BRANCH_DATA capability`() {
        transaction {
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq callerId }
        }

        assertFailsWith<NotFoundException> {
            getCurrentMonthSummary(branchId)
        }
    }

    @Test
    fun `throws 404 for non-existent branch`() {
        assertFailsWith<NotFoundException> {
            getCurrentMonthSummary(TestFixtures.uuid())
        }
    }

    @Test
    fun `returns zero snapshot values for PRODUCT remittance with only counts`() {
        val remittanceId = TestFixtures.uuid()
        val lineId = TestFixtures.uuid()
        val breakdownId = TestFixtures.uuid()

        createSubmittedProductRemittance(remittanceId, lineId, breakdownId)

        val summary = getCurrentMonthSummary(branchId)

        assertEquals(1, summary.totalRemittances)
        assertEquals(0, summary.sessionCount)
        assertEquals(1, summary.productCount)
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.grossIncome))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalCompensation))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.totalExpenses))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.netIncome))
    }

    private fun createSubmittedSessionRemittance(
        remittanceId: UUID,
        lineId: UUID,
        breakdownId: UUID,
        lineAmount: BigDecimal,
    ) {
        val branchDayId = resolveBranchDay(currentMonth.atDay(10))
        createDraftRemittance(remittanceId, currentMonth.atDay(1), currentMonth.atEndOfMonth())

        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, branchDayId)

        val sId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
            id = sId,
            clientId = clientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("2500.00"),
            finalPrice = BigDecimal("2500.00"),
        )
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            id = lineId,
            type = RemittanceLineType.SESSION,
            sessionId = sId,
            productSaleId = null,
            amount = lineAmount,
        )

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)
    }

    private fun createSubmittedProductRemittance(
        remittanceId: UUID,
        lineId: UUID,
        breakdownId: UUID,
    ) {
        val branchDayId = resolveBranchDay(currentMonth.atDay(10))

        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
            dateRangeStart = currentMonth.atDay(1),
            dateRangeEnd = currentMonth.atEndOfMonth(),
        )

        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, branchDayId)

        val psId = createProductSale(branchDayId)
        RemittanceService.addLine(
            callerId = callerId,
            remittanceId = remittanceId,
            id = lineId,
            type = RemittanceLineType.PRODUCT_SALE,
            sessionId = null,
            productSaleId = psId,
            amount = BigDecimal("200.00"),
        )

        val version = RemittanceService.getRemittance(remittanceId).remittance.version
        RemittanceService.submit(callerId, remittanceId, version)
    }

    private fun createDraftRemittance(
        remittanceId: UUID,
        dateRangeStart: LocalDate,
        dateRangeEnd: LocalDate,
    ) {
        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.SESSION,
            branchId = branchId,
            method = RemittanceMethod.BANK_TRANSFER,
            dateRangeStart = dateRangeStart,
            dateRangeEnd = dateRangeEnd,
        )
    }

    private fun getCurrentMonthSummary(branchId: UUID) =
        MonthlyRemittanceSummaryService.getMonthlySummary(branchId, currentMonth.year, currentMonth.monthValue)

    private fun resolveBranchDay(date: LocalDate): UUID {
        val bd = BranchDayService.resolveOrCreate(branchId, date)
        return bd.id
    }

    private fun createProductSale(branchDayId: UUID): UUID {
        val psId = TestFixtures.uuid()
        val productCategoryId = TestFixtures.uuid()
        val productId = TestFixtures.uuid()
        CommerceFinanceFixtures.insertTestCategory(productCategoryId, "Test Cat $psId")
        CommerceFinanceFixtures.insertTestProduct(productId, "Test Prod $psId", productCategoryId)
        CommerceFinanceFixtures.insertTestProductSale(
            id = psId,
            branchDayId = branchDayId,
            productId = productId,
            handledBy = callerId,
        )
        return psId
    }
}
