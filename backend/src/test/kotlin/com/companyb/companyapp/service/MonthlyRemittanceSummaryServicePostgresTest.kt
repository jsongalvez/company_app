package com.companyb.companyapp.service

import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.RemittanceRepository
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
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MonthlyRemittanceSummaryServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val clientId = UUID.randomUUID()

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertTestUser(callerId, "summary-caller")
        DatabaseTestHelper.insertTestBranch(branchId, "Monthly Summary Branch ${UUID.randomUUID()}")
        DatabaseTestHelper.insertTestClient(clientId)
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
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `returns 404 when no remittance data exists for given month`() {
        assertFailsWith<NotFoundResponse> {
            MonthlyRemittanceSummaryService.getMonthlySummary(callerId, branchId, 2026, 7)
        }
    }

    @Test
    fun `returns correct summary for a single SESSION remittance`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()

        createSubmittedSessionRemittance(remittanceId, lineId, breakdownId, BigDecimal("1000.00"))

        val summary = MonthlyRemittanceSummaryService.getMonthlySummary(callerId, branchId, 2026, 7)

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
        val branchDayId = resolveBranchDay(LocalDate.of(2026, 7, 10))

        createDraftRemittance(remittanceId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))
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

        val version = RemittanceRepository.findById(remittanceId)!!.version
        RemittanceService.submit(callerId, remittanceId, version)

        val summary = MonthlyRemittanceSummaryService.getMonthlySummary(callerId, branchId, 2026, 7)

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

        val summary = MonthlyRemittanceSummaryService.getMonthlySummary(callerId, branchId, 2026, 7)

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

        val summary = MonthlyRemittanceSummaryService.getMonthlySummary(callerId, branchId, 2026, 7)

        assertNotNull(summary)
        assertEquals(2, summary.totalRemittances)
        assertEquals(1, summary.sessionCount)
        assertEquals(1, summary.productCount)
        assertEquals(0, BigDecimal("500.00").compareTo(summary.grossIncome))
    }

    @Test
    fun `throws 403 without VIEW_BRANCH_DATA capability`() {
        transaction {
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq callerId }
        }

        assertFailsWith<ForbiddenResponse> {
            MonthlyRemittanceSummaryService.getMonthlySummary(callerId, branchId, 2026, 7)
        }
    }

    @Test
    fun `throws 404 for non-existent branch`() {
        assertFailsWith<NotFoundResponse> {
            MonthlyRemittanceSummaryService.getMonthlySummary(callerId, UUID.randomUUID(), 2026, 7)
        }
    }

    @Test
    fun `returns zero snapshot values for PRODUCT remittance with only counts`() {
        val remittanceId = UUID.randomUUID()
        val lineId = UUID.randomUUID()
        val breakdownId = UUID.randomUUID()

        createSubmittedProductRemittance(remittanceId, lineId, breakdownId)

        val summary = MonthlyRemittanceSummaryService.getMonthlySummary(callerId, branchId, 2026, 7)

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
        val branchDayId = resolveBranchDay(LocalDate.of(2026, 7, 10))
        createDraftRemittance(remittanceId, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31))

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

        val version = RemittanceRepository.findById(remittanceId)!!.version
        RemittanceService.submit(callerId, remittanceId, version)
    }

    private fun createSubmittedProductRemittance(
        remittanceId: UUID,
        lineId: UUID,
        breakdownId: UUID,
    ) {
        val branchDayId = resolveBranchDay(LocalDate.of(2026, 7, 10))

        RemittanceService.createDraft(
            callerId = callerId,
            id = remittanceId,
            type = RemittanceType.PRODUCT,
            branchId = branchId,
            method = RemittanceMethod.HANDED_TO_ACCOUNTANT,
            dateRangeStart = LocalDate.of(2026, 7, 1),
            dateRangeEnd = LocalDate.of(2026, 7, 31),
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

        val version = RemittanceRepository.findById(remittanceId)!!.version
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
        val conn = DatabaseConfig.dataSource.connection
        conn.createStatement().use { stmt ->
            stmt.execute(
                "INSERT INTO product_category (id, name) VALUES ('$productCategoryId', 'Test Cat $psId')",
            )
            stmt.execute(
                "INSERT INTO product (id, name, product_category_id, unit_price, commission_amount) " +
                    "VALUES ('$productId', 'Test Prod $psId', '$productCategoryId', 100.00, 10.00)",
            )
            stmt.execute(
                "INSERT INTO product_sale (id, branch_day_id, product_id, quantity, is_walk_in, " +
                    "client_id, handled_by, unit_price_at_time, " +
                    "total_amount_at_time, commission_amount_at_time, product_name) " +
                    "VALUES ('$psId', '$branchDayId', '$productId', 1, true, " +
                    "'$clientId', '$callerId', 100.00, 100.00, 10.00, 'Test Product')",
            )
        }
        conn.close()
        return psId
    }

    private fun disableSnapshotTrigger() {
        val conn = DatabaseConfig.dataSource.connection
        conn.createStatement().use { stmt ->
            stmt.execute(
                "ALTER TABLE remittance_financial_snapshot DISABLE TRIGGER trg_remittance_snapshot_immutable",
            )
        }
        conn.close()
    }

    private fun enableSnapshotTrigger() {
        val conn = DatabaseConfig.dataSource.connection
        conn.createStatement().use { stmt ->
            stmt.execute(
                "ALTER TABLE remittance_financial_snapshot ENABLE TRIGGER trg_remittance_snapshot_immutable",
            )
        }
        conn.close()
    }

    private fun deleteTestRows() {
        disableSnapshotTrigger()
        try {
            transaction {
                RemittanceFinancialSnapshotTable.deleteAll()
            }
        } finally {
            enableSnapshotTrigger()
        }
        transaction {
            AuditLogTable.deleteAll()
            CompensationTable.deleteAll()
            ExpenseTable.deleteAll()
            RemittanceDayBreakdownTable.deleteAll()
            RemittanceLineTable.deleteAll()
            RemittanceTable.deleteAll()
            SessionTable.deleteAll()
            ProductSaleTable.deleteAll()
            ProductTable.deleteAll()
            ProductCategoryTable.deleteAll()
            ClientTable.deleteWhere { ClientTable.id eq clientId }
            BranchDayTable.deleteWhere { BranchDayTable.branchId eq branchId }
            BranchTable.deleteWhere { BranchTable.id eq branchId }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq callerId }
            AppUserTable.deleteWhere { AppUserTable.id eq callerId }
        }
    }
}
