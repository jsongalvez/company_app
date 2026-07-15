package com.companyb.companyapp.service.export

import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseCategory
import com.companyb.companyapp.repository.model.ExpenseTable
import com.companyb.companyapp.repository.model.ProductCategoryTable
import com.companyb.companyapp.repository.model.ProductSaleTable
import com.companyb.companyapp.repository.model.ProductTable
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceLineType
import com.companyb.companyapp.repository.model.RemittanceMethod
import com.companyb.companyapp.repository.model.RemittanceStatus
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.RemittanceType
import com.companyb.companyapp.repository.model.SessionStatus
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.repository.model.SessionVoidTable
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.CapabilityService
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExportServicePostgresTest {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val branchDayId = UUID.randomUUID()
    private val today = LocalDate.now(java.time.ZoneId.of("Asia/Manila"))
    private val testBranchIds = mutableListOf(branchId)

    @BeforeTest
    fun setUp() {
        DatabaseTestHelper.ensureDatabase()
        deleteTestRows()
        DatabaseTestHelper.insertUser(
            id = callerId,
            username = "export-user-${callerId.toString().take(8)}",
            passwordHash = "hash",
            email = "${callerId.toString().take(8)}@test.com",
            displayName = "Export User",
        )
        insertBranch(branchId, "Export Test Branch ${UUID.randomUUID()}", BranchType.CLINIC)
        insertBranchDay(branchDayId, branchId, today)
        grantViewBranchData(callerId)
    }

    @AfterTest
    fun tearDown() {
        if (DatabaseTestHelper.isDatabaseReady()) {
            deleteTestRows()
        }
    }

    @Test
    fun `daily export CSV returns valid CSV bytes`() {
        val result = ExportService.exportDaily(callerId, branchId, today, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("Gross Income"))
        assertTrue(csv.contains("0.00"))
        assertEquals("text/csv", result.contentType)
        assertTrue(result.fileName.endsWith(".csv"))
    }

    @Test
    fun `daily export PDF returns valid PDF bytes`() {
        val result = ExportService.exportDaily(callerId, branchId, today, ExportFormat.PDF)
        val pdfStart = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        assertContentEquals(pdfStart, result.bytes.take(4).toByteArray())
        assertEquals("application/pdf", result.contentType)
        assertTrue(result.fileName.endsWith(".pdf"))
    }

    @Test
    fun `daily export with session data includes correct totals`() {
        insertSession(branchDayId, SessionType.REGULAR, SessionStatus.COMPLETED, BigDecimal("2500.00"))
        val result = ExportService.exportDaily(callerId, branchId, today, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("2500.00"))
    }

    @Test
    fun `daily export throws 404 for missing data`() {
        val missingDate = today.plusDays(100)
        assertFailsWith<NotFoundResponse> {
            ExportService.exportDaily(callerId, branchId, missingDate, ExportFormat.CSV)
        }
    }

    @Test
    fun `daily export throws 403 without capability`() {
        val otherUserId = UUID.randomUUID()
        DatabaseTestHelper.insertUser(
            id = otherUserId,
            username = "no-cap-${otherUserId.toString().take(8)}",
            passwordHash = "hash",
            email = "${otherUserId.toString().take(8)}@test.com",
            displayName = "No Cap User",
        )
        assertFailsWith<ForbiddenResponse> {
            ExportService.exportDaily(otherUserId, branchId, today, ExportFormat.CSV)
        }
    }

    @Test
    fun `daily export throws 404 for non-existent branch`() {
        val missingBranch = UUID.randomUUID()
        assertFailsWith<NotFoundResponse> {
            ExportService.exportDaily(callerId, missingBranch, today, ExportFormat.CSV)
        }
    }

    @Test
    fun `monthly export throws 404 when no remittance data`() {
        assertFailsWith<NotFoundResponse> {
            ExportService.exportMonthly(callerId, branchId, 2099, 1, ExportFormat.CSV)
        }
    }

    @Test
    fun `monthly export CSV returns valid CSV bytes`() {
        createSubmittedRemittance(BigDecimal("5000.00"), BigDecimal("1000.00"), BigDecimal("500.00"))
        val result = ExportService.exportMonthly(callerId, branchId, today.year, today.monthValue, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("Remittances"))
        assertTrue(csv.contains("5000.00"))
        assertEquals("text/csv", result.contentType)
    }

    @Test
    fun `monthly export PDF returns valid PDF bytes`() {
        createSubmittedRemittance(BigDecimal("3000.00"), BigDecimal("800.00"), BigDecimal("200.00"))
        val result = ExportService.exportMonthly(callerId, branchId, today.year, today.monthValue, ExportFormat.PDF)
        val pdfStart = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        assertContentEquals(pdfStart, result.bytes.take(4).toByteArray())
        assertEquals("application/pdf", result.contentType)
    }

    @Test
    fun `all-time export CSV returns monthly data rows`() {
        createSubmittedRemittance(BigDecimal("5000.00"), BigDecimal("1000.00"), BigDecimal("500.00"))
        val result = ExportService.exportAllTime(callerId, branchId, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("Year"))
        assertTrue(csv.contains("Month"))
        assertTrue(csv.contains("5000.00"))
        assertEquals("text/csv", result.contentType)
    }

    @Test
    fun `all-time export throws 404 when no data`() {
        assertFailsWith<NotFoundResponse> {
            ExportService.exportAllTime(callerId, branchId, ExportFormat.CSV)
        }
    }

    @Test
    fun `all-time export PDF returns valid PDF bytes`() {
        createSubmittedRemittance(BigDecimal("4000.00"), BigDecimal("900.00"), BigDecimal("300.00"))
        val result = ExportService.exportAllTime(callerId, branchId, ExportFormat.PDF)
        val pdfStart = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        assertContentEquals(pdfStart, result.bytes.take(4).toByteArray())
        assertEquals("application/pdf", result.contentType)
    }

    @Test
    fun `provincial export throws 404 when no data`() {
        assertFailsWith<NotFoundResponse> {
            ExportService.exportByBranchType(callerId, BranchType.PROVINCIAL_TOUR, null, null, ExportFormat.CSV)
        }
    }

    @Test
    fun `provincial export CSV returns branch data`() {
        val provBranchId = UUID.randomUUID()
        insertBranch(provBranchId, "Prov Branch ${UUID.randomUUID()}", BranchType.PROVINCIAL_TOUR)
        val provDayId = UUID.randomUUID()
        insertBranchDay(provDayId, provBranchId, today)
        createSubmittedRemittanceForBranch(
            provBranchId,
            BigDecimal("2000.00"),
            BigDecimal("400.00"),
            BigDecimal("100.00"),
        )

        val result =
            ExportService.exportByBranchType(
                callerId,
                BranchType.PROVINCIAL_TOUR,
                null,
                null,
                ExportFormat.CSV,
            )
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("Branch"), "Expected CSV to contain 'Branch' but got: $csv")
        assertTrue(csv.contains("1500.00"), "Expected CSV to contain '1500.00' (net income) but got: $csv")
        assertEquals("text/csv", result.contentType)
    }

    @Test
    fun `medical mission export throws 404 when no data`() {
        assertFailsWith<NotFoundResponse> {
            ExportService.exportByBranchType(callerId, BranchType.MEDICAL_MISSION, null, null, ExportFormat.CSV)
        }
    }

    @Test
    fun `medical mission export CSV returns branch data`() {
        val mmBranchId = UUID.randomUUID()
        insertBranch(mmBranchId, "MM Branch ${UUID.randomUUID()}", BranchType.MEDICAL_MISSION)
        val mmDayId = UUID.randomUUID()
        insertBranchDay(mmDayId, mmBranchId, today)
        createSubmittedRemittanceForBranch(mmBranchId, BigDecimal("1000.00"), BigDecimal("200.00"), BigDecimal("50.00"))

        val result =
            ExportService.exportByBranchType(
                callerId,
                BranchType.MEDICAL_MISSION,
                null,
                null,
                ExportFormat.CSV,
            )
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("Branch"), "Expected CSV to contain 'Branch' but got: $csv")
        assertTrue(csv.contains("750.00"), "Expected CSV to contain '750.00' (net income) but got: $csv")
        assertEquals("text/csv", result.contentType)
    }

    @Test
    fun `clinic branch type throws BadRequest for provincial export`() {
        assertFailsWith<BadRequestResponse> {
            ExportService.exportByBranchType(callerId, BranchType.CLINIC, null, null, ExportFormat.CSV)
        }
    }

    @Test
    fun `parseFormat throws BadRequest for invalid format`() {
        assertFailsWith<BadRequestResponse> {
            ExportService.parseFormat("xlsx")
        }
    }

    @Test
    fun `parseFormat throws BadRequest for null format`() {
        assertFailsWith<BadRequestResponse> {
            ExportService.parseFormat(null)
        }
    }

    @Test
    fun `parseFormat returns CSV for csv string`() {
        assertEquals(ExportFormat.CSV, ExportService.parseFormat("csv"))
    }

    @Test
    fun `parseFormat returns PDF for pdf string`() {
        assertEquals(ExportFormat.PDF, ExportService.parseFormat("pdf"))
    }

    @Test
    fun `provincial export with month filter returns filtered data`() {
        val provBranchId = UUID.randomUUID()
        insertBranch(provBranchId, "Prov Month Branch ${UUID.randomUUID()}", BranchType.PROVINCIAL_TOUR)
        val provDayId = UUID.randomUUID()
        insertBranchDay(provDayId, provBranchId, today)
        createSubmittedRemittanceForBranch(
            provBranchId,
            BigDecimal("3000.00"),
            BigDecimal("600.00"),
            BigDecimal("200.00"),
        )

        val result =
            ExportService.exportByBranchType(
                callerId,
                BranchType.PROVINCIAL_TOUR,
                today.year,
                today.monthValue,
                ExportFormat.CSV,
            )
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("2200.00"), "Expected CSV to contain '2200.00' (net income) but got: $csv")
    }

    private fun grantViewBranchData(userId: UUID) {
        DatabaseTestHelper.grantCapability(
            userId = userId,
            capabilityCode = CapabilityCodes.VIEW_BRANCH_DATA,
            contextType = CapabilityContextType.GLOBAL,
            contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            sourceId = sourceId,
        )
    }

    private fun insertBranch(
        id: UUID,
        name: String,
        type: BranchType,
    ) {
        testBranchIds.add(id)
        transaction {
            BranchTable.insert {
                it[BranchTable.id] = id
                it[BranchTable.name] = name
                it[BranchTable.branchType] = type
            }
        }
    }

    private fun insertBranchDay(
        id: UUID,
        branchId: UUID,
        date: LocalDate,
    ) {
        transaction {
            BranchDayTable.insertIgnore {
                it[BranchDayTable.id] = id
                it[BranchDayTable.branchId] = branchId
                it[BranchDayTable.date] = date
            }
        }
    }

    private fun insertSession(
        branchDayId: UUID,
        sessionType: SessionType = SessionType.REGULAR,
        sessionStatus: SessionStatus = SessionStatus.COMPLETED,
        finalPrice: BigDecimal = BigDecimal("2500.00"),
    ) {
        val clientId = UUID.randomUUID()
        transaction {
            ClientTable.insert {
                it[ClientTable.id] = clientId
                it[ClientTable.firstName] = "Test"
                it[ClientTable.lastName] = "Client"
                it[ClientTable.gender] = "M"
                it[ClientTable.age] = 30
            }
            SessionTable.insert {
                it[SessionTable.id] = UUID.randomUUID()
                it[SessionTable.clientId] = clientId
                it[SessionTable.branchDayId] = branchDayId
                it[SessionTable.sessionType] = sessionType
                it[SessionTable.sessionStatus] = sessionStatus
                it[SessionTable.basePrice] = finalPrice
                it[SessionTable.finalPrice] = finalPrice
                it[SessionTable.isWalkIn] = false
            }
        }
    }

    private fun createSubmittedRemittance(
        grossIncome: BigDecimal,
        compensation: BigDecimal,
        expenses: BigDecimal,
    ) = createSubmittedRemittanceForBranch(branchId, grossIncome, compensation, expenses)

    private fun createSubmittedRemittanceForBranch(
        targetBranchId: UUID,
        grossIncome: BigDecimal,
        compensation: BigDecimal,
        expenses: BigDecimal,
    ) {
        val remittanceId = UUID.randomUUID()
        val compensationUserId = UUID.randomUUID()
        val targetDayId = findOrCreateBranchDay(targetBranchId)
        val sessionId = UUID.randomUUID()
        val lineClientId = UUID.randomUUID()

        transaction {
            DatabaseTestHelper.insertUser(
                id = compensationUserId,
                username = "comp-user-${compensationUserId.toString().take(8)}",
                passwordHash = "hash",
                email = "${compensationUserId.toString().take(8)}@test.com",
                displayName = "Comp User",
            )
            ClientTable.insert {
                it[ClientTable.id] = lineClientId
                it[ClientTable.firstName] = "Line"
                it[ClientTable.lastName] = "Client"
                it[ClientTable.gender] = "M"
                it[ClientTable.age] = 25
            }
            SessionTable.insert {
                it[SessionTable.id] = sessionId
                it[SessionTable.clientId] = lineClientId
                it[SessionTable.branchDayId] = targetDayId
                it[SessionTable.sessionType] = com.companyb.companyapp.domain.SessionType.REGULAR
                it[SessionTable.sessionStatus] = SessionStatus.COMPLETED
                it[SessionTable.basePrice] = grossIncome
                it[SessionTable.finalPrice] = grossIncome
                it[SessionTable.isWalkIn] = false
            }
            insertRemittance(remittanceId, targetBranchId)
            insertRemittanceLine(remittanceId, grossIncome, sessionId)
            insertRemittanceBreakdown(remittanceId, targetDayId)
            insertCompensation(compensationUserId, targetDayId, compensation)
            insertExpense(targetDayId, expenses)
            insertFinancialSnapshot(remittanceId, grossIncome, compensation, expenses)
        }
    }

    private fun findOrCreateBranchDay(targetBranchId: UUID): UUID {
        val existing =
            transaction {
                BranchDayTable
                    .selectAll()
                    .where {
                        (BranchDayTable.branchId eq targetBranchId) and (BranchDayTable.date eq today)
                    }.singleOrNull()
            }
        if (existing != null) {
            return existing[BranchDayTable.id]
        }
        val dayId = UUID.randomUUID()
        transaction {
            BranchDayTable.insertIgnore {
                it[BranchDayTable.id] = dayId
                it[BranchDayTable.branchId] = targetBranchId
                it[BranchDayTable.date] = today
            }
        }
        return dayId
    }

    private fun insertRemittance(
        remittanceId: UUID,
        targetBranchId: UUID,
    ) {
        RemittanceTable.insert {
            it[RemittanceTable.id] = remittanceId
            it[RemittanceTable.branchId] = targetBranchId
            it[RemittanceTable.type] = RemittanceType.SESSION
            it[RemittanceTable.method] = RemittanceMethod.BANK_TRANSFER
            it[RemittanceTable.status] = RemittanceStatus.SUBMITTED
            it[RemittanceTable.version] = 2
            it[RemittanceTable.submittedDate] = today
            it[RemittanceTable.submittedBy] = callerId
            it[RemittanceTable.dateRangeStart] = today
            it[RemittanceTable.dateRangeEnd] = today
        }
    }

    private fun insertRemittanceLine(
        remittanceId: UUID,
        grossIncome: BigDecimal,
        sessionId: UUID,
    ) {
        RemittanceLineTable.insert {
            it[RemittanceLineTable.id] = UUID.randomUUID()
            it[RemittanceLineTable.remittanceId] = remittanceId
            it[RemittanceLineTable.type] = RemittanceLineType.SESSION
            it[RemittanceLineTable.amount] = grossIncome
            it[RemittanceLineTable.sessionId] = sessionId
        }
    }

    private fun insertRemittanceBreakdown(
        remittanceId: UUID,
        targetDayId: UUID,
    ) {
        RemittanceDayBreakdownTable.insert {
            it[RemittanceDayBreakdownTable.id] = UUID.randomUUID()
            it[RemittanceDayBreakdownTable.remittanceId] = remittanceId
            it[RemittanceDayBreakdownTable.branchDayId] = targetDayId
        }
    }

    private fun insertCompensation(
        compensationUserId: UUID,
        targetDayId: UUID,
        compensation: BigDecimal,
    ) {
        CompensationTable.insert {
            it[CompensationTable.id] = UUID.randomUUID()
            it[CompensationTable.workBranchDayId] = targetDayId
            it[CompensationTable.payingBranchDayId] = targetDayId
            it[CompensationTable.userId] = compensationUserId
            it[CompensationTable.amount] = compensation
            it[CompensationTable.assignedBy] = callerId
        }
    }

    private fun insertExpense(
        targetDayId: UUID,
        expenses: BigDecimal,
    ) {
        ExpenseTable.insert {
            it[ExpenseTable.id] = UUID.randomUUID()
            it[ExpenseTable.branchDayId] = targetDayId
            it[ExpenseTable.amount] = expenses
            it[ExpenseTable.category] = ExpenseCategory.MISCELLANEOUS
            it[ExpenseTable.createdBy] = callerId
            it[ExpenseTable.notes] = "Test expense"
        }
    }

    private fun insertFinancialSnapshot(
        remittanceId: UUID,
        grossIncome: BigDecimal,
        compensation: BigDecimal,
        expenses: BigDecimal,
    ) {
        val netIncome = grossIncome - compensation - expenses
        RemittanceFinancialSnapshotTable.insert {
            it[RemittanceFinancialSnapshotTable.remittanceId] = remittanceId
            it[RemittanceFinancialSnapshotTable.grossIncome] = grossIncome
            it[RemittanceFinancialSnapshotTable.totalCompensation] = compensation
            it[RemittanceFinancialSnapshotTable.totalExpenses] = expenses
            it[RemittanceFinancialSnapshotTable.netIncome] = netIncome
        }
    }

    private fun deleteTestRows() {
        disableSnapshotTrigger()
        transaction {
            RemittanceFinancialSnapshotTable.deleteAll()
            RemittanceLineTable.deleteAll()
            RemittanceDayBreakdownTable.deleteAll()
            RemittanceTable.deleteAll()
            SessionVoidTable.deleteAll()
            SessionTable.deleteAll()
            CompensationTable.deleteAll()
            ExpenseTable.deleteAll()
            ProductSaleTable.deleteAll()
            ProductTable.deleteAll()
            ProductCategoryTable.deleteAll()
            ClientTable.deleteAll()
            for (bid in testBranchIds) {
                BranchDayTable.deleteWhere { BranchDayTable.branchId eq bid }
            }
            for (bid in testBranchIds) {
                BranchTable.deleteWhere { BranchTable.id eq bid }
            }
            UserCapabilityTable.deleteWhere { UserCapabilityTable.userId eq callerId }
            AppUserTable.deleteWhere { AppUserTable.id eq callerId }
        }
        enableSnapshotTrigger()
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
}
