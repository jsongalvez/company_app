package com.companyb.companyapp.service
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.AuditLogTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.RemittanceType
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.branchday.BranchDayService
import com.companyb.companyapp.service.finance.remittance.RemittanceService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MonthlyRemittanceSummaryServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()

    override fun initTestData() {
        DatabaseTestHelper.insertTestUser(callerId, "summary-caller")
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        DatabaseTestHelper.insertTestBranch(branchId, "Monthly Summary Branch ${UUID.randomUUID()}")
        trackOwned(BranchTable, BranchTable.id, branchId)
        DatabaseTestHelper.insertTestClient(clientId)
        trackOwned(ClientTable, ClientTable.id, clientId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, branchId)
        DatabaseTestHelper.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
        DatabaseTestHelper.grantCapability(
            userId = callerId,
            capabilityCode = CapabilityCodes.SUBMIT_REMITTANCE,
            contextType = CapabilityContextType.BRANCH,
            contextId = branchId,
            sourceId = sourceId,
        )
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        trackOwned(AuditLogTable, AuditLogTable.changedBy, callerId)
        trackOwned(SessionTable, SessionTable.clientId, clientId)
        trackOwned(ProductSaleTable, ProductSaleTable.handledBy, callerId)
        trackOwned(CompensationTable, CompensationTable.userId, callerId)
        trackOwned(ExpenseTable, ExpenseTable.createdBy, callerId)
    }

    @Test
    fun `returns 404 when no remittance data exists for given month`() {
        assertFailsWith<NotFoundException> {
            MonthlyRemittanceSummaryService.getMonthlySummary(branchId, 2026, 8)
        }
    }

    @Test
    fun `returns correct summary for a single SESSION remittance`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()

        createSubmittedSessionRemittance(remittanceId, lineId, breakdownId, BigDecimal("1000.00"))

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)

        val summary = MonthlyRemittanceSummaryService.getMonthlySummary(branchId, 2026, 8)

        assertNotNull(summary)
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
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()
        val branchDayId = resolveBranchDay(LocalDate.of(2026, 8, 10))

        createDraftRemittance(remittanceId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, branchDayId)

        DatabaseTestHelper.insertTestCompensation(branchDayId, callerId, BigDecimal("300.00"), assignedBy = callerId)
        DatabaseTestHelper.insertTestExpense(branchDayId, callerId, BigDecimal("150.00"))

        val sId = UUID.randomUUID()
        DatabaseTestHelper.insertTestSession(
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

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, remittanceId)

        val summary = MonthlyRemittanceSummaryService.getMonthlySummary(branchId, 2026, 8)

        assertNotNull(summary)
        assertEquals(1, summary.totalRemittances)
        assertEquals(0, BigDecimal("2000.00").compareTo(summary.grossIncome))
        assertEquals(0, BigDecimal("300.00").compareTo(summary.totalCompensation))
        assertEquals(0, BigDecimal("150.00").compareTo(summary.totalExpenses))
        assertEquals(0, BigDecimal("1550.00").compareTo(summary.netIncome))
    }

    @Test
    fun `returns correct summary for a PRODUCT remittance`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()

        createSubmittedProductRemittance(remittanceId, lineId, breakdownId)

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)

        val summary = MonthlyRemittanceSummaryService.getMonthlySummary(branchId, 2026, 8)

        assertNotNull(summary)
        assertEquals(1, summary.totalRemittances)
        assertEquals(0, summary.sessionCount)
        assertEquals(1, summary.productCount)
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.grossIncome))
        assertEquals(0, BigDecimal.ZERO.compareTo(summary.netIncome))
    }

    @Test
    fun `aggregates multiple remittances in same month`() {
        val rem1Id = UUID.randomUUID()
        val rem2Id = UUID.randomUUID()
        createSubmittedSessionRemittance(rem1Id, UUID.randomUUID(), UUID.randomUUID(), BigDecimal("500.00"))
        createSubmittedProductRemittance(rem2Id, UUID.randomUUID(), UUID.randomUUID())

        trackOwned(RemittanceTable, RemittanceTable.id, rem1Id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, rem1Id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, rem1Id)
        trackOwned(RemittanceFinancialSnapshotTable, RemittanceFinancialSnapshotTable.remittanceId, rem1Id)
        trackOwned(RemittanceTable, RemittanceTable.id, rem2Id)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, rem2Id)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, rem2Id)

        val summary = MonthlyRemittanceSummaryService.getMonthlySummary(branchId, 2026, 8)

        assertNotNull(summary)
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
            MonthlyRemittanceSummaryService.getMonthlySummary(branchId, 2026, 8)
        }
    }

    @Test
    fun `throws 404 for non-existent branch`() {
        assertFailsWith<NotFoundException> {
            MonthlyRemittanceSummaryService.getMonthlySummary(UUID.randomUUID(), 2026, 8)
        }
    }

    @Test
    fun `returns zero snapshot values for PRODUCT remittance with only counts`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()

        createSubmittedProductRemittance(remittanceId, lineId, breakdownId)

        trackOwned(RemittanceTable, RemittanceTable.id, remittanceId)
        trackOwned(RemittanceLineTable, RemittanceLineTable.remittanceId, remittanceId)
        trackOwned(RemittanceDayBreakdownTable, RemittanceDayBreakdownTable.remittanceId, remittanceId)

        val summary = MonthlyRemittanceSummaryService.getMonthlySummary(branchId, 2026, 8)

        assertNotNull(summary)
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
        val branchDayId = resolveBranchDay(LocalDate.of(2026, 8, 10))
        createDraftRemittance(remittanceId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))

        RemittanceService.addDayBreakdown(callerId, remittanceId, breakdownId, branchDayId)

        val sId = UUID.randomUUID()
        DatabaseTestHelper.insertTestSession(
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
        val branchDayId = resolveBranchDay(LocalDate.of(2026, 8, 10))

        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
            dateRangeStart = LocalDate.of(2026, 8, 1),
            dateRangeEnd = LocalDate.of(2026, 8, 31),
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

    private fun resolveBranchDay(date: LocalDate): UUID {
        val bd = BranchDayService.resolveOrCreate(branchId, date)
        return bd.id
    }

    private fun createProductSale(branchDayId: UUID): UUID {
        val psId = UUID.randomUUID()
        val productCategoryId = UUID.randomUUID()
        val productId = UUID.randomUUID()
        DatabaseTestHelper.insertTestCategory(productCategoryId, "Test Cat $psId")
        DatabaseTestHelper.insertTestProduct(productId, "Test Prod $psId", productCategoryId)
        DatabaseTestHelper.insertTestProductSale(
            id = psId,
            branchDayId = branchDayId,
            productId = productId,
            handledBy = callerId,
        )
        trackOwned(ProductCategoryTable, ProductCategoryTable.id, productCategoryId)
        trackOwned(ProductTable, ProductTable.id, productId)
        return psId
    }
}
