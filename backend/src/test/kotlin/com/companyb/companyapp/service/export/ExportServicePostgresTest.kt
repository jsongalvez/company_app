package com.companyb.companyapp.service.export
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.RemittanceLineType
import com.companyb.companyapp.domain.RemittanceMethod
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.domain.RemittanceType
import com.companyb.companyapp.domain.SessionStatus
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CommissionSplitTable
import com.companyb.companyapp.repository.model.RemittanceDayBreakdownTable
import com.companyb.companyapp.repository.model.RemittanceFinancialSnapshotTable
import com.companyb.companyapp.repository.model.RemittanceLineTable
import com.companyb.companyapp.repository.model.RemittanceTable
import com.companyb.companyapp.repository.model.SessionTable
import com.companyb.companyapp.service.CapabilityService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import com.companyb.companyapp.test.TestFixtures
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportServicePostgresTest : BasePostgresTest() {
    private val callerId = TestFixtures.uuid()
    private val sourceId = TestFixtures.uuid()
    private val branchId = TestFixtures.uuid()
    private val branchDayId = TestFixtures.uuid()
    private val today = TestFixtures.today

    override fun initTestData() {
        DatabaseTestHelper.insertUser(
            id = callerId,
            username = "export-user-${callerId.toString().take(8)}",
            passwordHash = "hash",
            email = "${callerId.toString().take(8)}@test.com",
            displayName = "Export User",
        )
        DatabaseTestHelper.insertTestBranch(branchId, "Export Test Branch ${TestFixtures.uuid()}", BranchType.CLINIC)
        insertBranchDay(branchDayId, branchId, today)
        grantViewBranchData(callerId)
    }

    @Test
    fun `daily export CSV returns valid CSV bytes`() {
        val result = ExportService.exportDaily(branchId, today, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("Gross Income"))
        assertTrue(csv.contains("0.00"))
        assertEquals("text/csv", result.contentType)
        assertTrue(result.fileName.endsWith(".csv"))
    }

    @Test
    fun `daily export PDF returns valid PDF bytes`() {
        val result = ExportService.exportDaily(branchId, today, ExportFormat.PDF)
        val pdfStart = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        assertContentEquals(pdfStart, result.bytes.take(4).toByteArray())
        assertEquals("application/pdf", result.contentType)
        assertTrue(result.fileName.endsWith(".pdf"))
    }

    @Test
    fun `daily export with session data includes correct totals`() {
        val testClientId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestClient(testClientId)
        val sessionId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestSession(
            id = sessionId,
            clientId = testClientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("2500.00"),
            finalPrice = BigDecimal("2500.00"),
        )
        val result = ExportService.exportDaily(branchId, today, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("2500.00"))
    }

    @Test
    fun `daily export throws 404 for missing data`() {
        val missingDate = today.plusDays(100)
        assertFailsWith<NotFoundException> {
            ExportService.exportDaily(branchId, missingDate, ExportFormat.CSV)
        }
    }

    @Test
    fun `daily export returns CSV without capability`() {
        val otherUserId = TestFixtures.uuid()
        DatabaseTestHelper.insertUser(
            id = otherUserId,
            username = "no-cap-${otherUserId.toString().take(8)}",
            passwordHash = "hash",
            email = "${otherUserId.toString().take(8)}@test.com",
            displayName = "No Cap User",
        )
        val result = ExportService.exportDaily(branchId, today, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("Gross Income"))
        assertTrue(csv.contains("0.00"))
    }

    @Test
    fun `daily export throws 404 for non-existent branch`() {
        val missingBranch = TestFixtures.uuid()
        assertFailsWith<NotFoundException> {
            ExportService.exportDaily(missingBranch, today, ExportFormat.CSV)
        }
    }

    @Test
    fun `range export CSV rolls up the window into one row`() {
        val day1 = TestFixtures.uuid()
        insertBranchDay(day1, branchId, today.minusDays(1))
        seedDayFinancials(
            day1,
            DayFinancials(
                gross = BigDecimal("2500.00"),
                comp = BigDecimal("500.00"),
                expense = BigDecimal("200.00"),
                productSales = BigDecimal("300.00"),
                commission = BigDecimal("150.0000"),
            ),
        )
        seedDayFinancials(
            branchDayId,
            DayFinancials(
                gross = BigDecimal("1500.00"),
                comp = BigDecimal("300.00"),
                expense = BigDecimal("100.00"),
                productSales = BigDecimal("100.00"),
                commission = BigDecimal("50.0000"),
            ),
        )

        val result = ExportService.exportRange(branchId, today.minusDays(1), today, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("Gross Income"), "expected header row but got: $csv")
        assertEquals(1, csv.lines().count { it.isNotBlank() } - 1, "expected header + one rollup row but got: $csv")
        assertEquals(
            "4000.00,800.00,300.00,2900.00,400.00,200.0000",
            csv.lines().first { it.isNotBlank() && !it.startsWith("Gross Income") },
            "unexpected rollup row in: $csv",
        )
        assertTrue(csv.contains("4000.00"), "expected gross sum 4000.00 but got: $csv")
        assertTrue(csv.contains("800.00"), "expected compensation sum 800.00 but got: $csv")
        assertTrue(csv.contains("300.00"), "expected expense sum 300.00 but got: $csv")
        assertTrue(csv.contains("2900.00"), "expected net sum 2900.00 but got: $csv")
        assertTrue(csv.contains("400.00"), "expected product sales sum 400.00 but got: $csv")
        assertTrue(csv.contains("200.0000"), "expected commission sum 200.0000 but got: $csv")
        assertEquals("text/csv", result.contentType)
        assertTrue(result.fileName.endsWith(".csv"))
    }

    @Test
    fun `range export PDF returns valid PDF bytes`() {
        seedDayFinancials(
            branchDayId,
            DayFinancials(
                gross = BigDecimal("1500.00"),
                comp = BigDecimal("300.00"),
                expense = BigDecimal("100.00"),
                productSales = BigDecimal.ZERO,
                commission = BigDecimal.ZERO,
            ),
        )
        val result = ExportService.exportRange(branchId, today, today, ExportFormat.PDF)
        val pdfStart = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        assertContentEquals(pdfStart, result.bytes.take(4).toByteArray())
        assertEquals("application/pdf", result.contentType)
        assertTrue(result.fileName.endsWith(".pdf"))
    }

    @Test
    fun `range export excludes days outside the window and keeps zero-activity days`() {
        val dayOutside = TestFixtures.uuid()
        insertBranchDay(dayOutside, branchId, today.minusDays(5))
        seedDayFinancials(
            dayOutside,
            DayFinancials(
                gross = BigDecimal("9999.00"),
                comp = BigDecimal("100.00"),
                expense = BigDecimal("100.00"),
                productSales = BigDecimal("100.00"),
                commission = BigDecimal("100.0000"),
            ),
        )

        val result = ExportService.exportRange(branchId, today, today, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertFalse(csv.contains("9999.00"), "day outside the window must be excluded but got: $csv")
        assertEquals(
            "0.00,0.00,0.00,0.00,0.00,0.0000",
            csv.lines().first { it.isNotBlank() && !it.startsWith("Gross Income") },
            "zero-activity day in the window must yield its zero rollup row: $csv",
        )
    }

    @Test
    fun `range export throws 404 when no data in range`() {
        assertFailsWith<NotFoundException> {
            ExportService.exportRange(branchId, today.plusDays(10), today.plusDays(20), ExportFormat.CSV)
        }
    }

    @Test
    fun `range export throws 404 for non-existent branch`() {
        assertFailsWith<NotFoundException> {
            ExportService.exportRange(TestFixtures.uuid(), today, today, ExportFormat.CSV)
        }
    }

    @Test
    fun `monthly export throws 404 when no remittance data`() {
        assertFailsWith<NotFoundException> {
            ExportService.exportMonthly(branchId, 2099, 1, ExportFormat.CSV)
        }
    }

    @Test
    fun `monthly export CSV returns valid CSV bytes`() {
        createSubmittedRemittance(BigDecimal("5000.00"), BigDecimal("1000.00"), BigDecimal("500.00"))
        val result = ExportService.exportMonthly(branchId, today.year, today.monthValue, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("Remittances"))
        assertTrue(csv.contains("5000.00"))
        assertEquals("text/csv", result.contentType)
    }

    @Test
    fun `monthly export PDF returns valid PDF bytes`() {
        createSubmittedRemittance(BigDecimal("3000.00"), BigDecimal("800.00"), BigDecimal("200.00"))
        val result = ExportService.exportMonthly(branchId, today.year, today.monthValue, ExportFormat.PDF)
        val pdfStart = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        assertContentEquals(pdfStart, result.bytes.take(4).toByteArray())
        assertEquals("application/pdf", result.contentType)
    }

    @Test
    fun `all-time export CSV returns monthly data rows`() {
        createSubmittedRemittance(BigDecimal("5000.00"), BigDecimal("1000.00"), BigDecimal("500.00"))
        val result = ExportService.exportAllTime(branchId, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("Year"))
        assertTrue(csv.contains("Month"))
        assertTrue(csv.contains("5000.00"))
        assertEquals("text/csv", result.contentType)
    }

    @Test
    fun `all-time export throws 404 when no data`() {
        assertFailsWith<NotFoundException> {
            ExportService.exportAllTime(branchId, ExportFormat.CSV)
        }
    }

    @Test
    fun `all-time export PDF returns valid PDF bytes`() {
        createSubmittedRemittance(BigDecimal("4000.00"), BigDecimal("900.00"), BigDecimal("300.00"))
        val result = ExportService.exportAllTime(branchId, ExportFormat.PDF)
        val pdfStart = byteArrayOf(0x25, 0x50, 0x44, 0x46)
        assertContentEquals(pdfStart, result.bytes.take(4).toByteArray())
        assertEquals("application/pdf", result.contentType)
    }

    @Test
    fun `provincial export CSV returns branch data`() {
        val provBranchId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(
            provBranchId,
            "Prov Branch ${TestFixtures.uuid()}",
            BranchType.PROVINCIAL_TOUR,
        )
        val provDayId = TestFixtures.uuid()
        insertBranchDay(provDayId, provBranchId, today)
        val remittanceId =
            createSubmittedRemittanceForBranch(
                provBranchId,
                BigDecimal("2000.00"),
                BigDecimal("400.00"),
                BigDecimal("100.00"),
            )
        val result =
            ExportService.exportByBranchType(
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
    fun `medical mission export CSV returns branch data`() {
        val mmBranchId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(mmBranchId, "MM Branch ${TestFixtures.uuid()}", BranchType.MEDICAL_MISSION)
        val mmDayId = TestFixtures.uuid()
        insertBranchDay(mmDayId, mmBranchId, today)
        val remittanceId =
            createSubmittedRemittanceForBranch(
                mmBranchId,
                BigDecimal("1000.00"),
                BigDecimal("200.00"),
                BigDecimal("50.00"),
            )
        val result =
            ExportService.exportByBranchType(
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
    fun `clinic branch type throws ValidationException for provincial export`() {
        assertFailsWith<ValidationException> {
            ExportService.exportByBranchType(BranchType.CLINIC, null, null, ExportFormat.CSV)
        }
    }

    @Test
    fun `provincial export with month filter returns filtered data`() {
        val provBranchId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestBranch(
            provBranchId,
            "Prov Month Branch ${TestFixtures.uuid()}",
            BranchType.PROVINCIAL_TOUR,
        )
        val provDayId = TestFixtures.uuid()
        insertBranchDay(provDayId, provBranchId, today)
        val remittanceId =
            createSubmittedRemittanceForBranch(
                provBranchId,
                BigDecimal("3000.00"),
                BigDecimal("600.00"),
                BigDecimal("200.00"),
            )
        val result =
            ExportService.exportByBranchType(
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

    private data class DayFinancials(
        val gross: BigDecimal,
        val comp: BigDecimal,
        val expense: BigDecimal,
        val productSales: BigDecimal,
        val commission: BigDecimal,
    )

    private fun seedDayFinancials(
        dayId: UUID,
        financials: DayFinancials,
    ) {
        val clientId = TestFixtures.uuid()
        val userId = TestFixtures.uuid()
        val categoryId = TestFixtures.uuid()
        val productId = TestFixtures.uuid()
        DatabaseTestHelper.insertTestClient(clientId)
        DatabaseTestHelper.insertTestUser(userId, "range-user")
        DatabaseTestHelper.insertTestCategory(categoryId)
        DatabaseTestHelper.insertTestProduct(productId, categoryId = categoryId)
        DatabaseTestHelper.insertTestSession(
            id = TestFixtures.uuid(),
            clientId = clientId,
            branchDayId = dayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = financials.gross,
            finalPrice = financials.gross,
        )
        DatabaseTestHelper.insertTestCompensation(dayId, userId, financials.comp, assignedBy = callerId)
        DatabaseTestHelper.insertTestExpense(dayId, userId, financials.expense)
        DatabaseTestHelper.insertTestProductSale(
            id = TestFixtures.uuid(),
            branchDayId = dayId,
            productId = productId,
            handledBy = userId,
            unitPrice = financials.productSales,
            totalAmount = financials.productSales,
        )
        transaction {
            CommissionSplitTable.insert {
                it[CommissionSplitTable.id] = TestFixtures.uuid()
                it[CommissionSplitTable.branchDayId] = dayId
                it[CommissionSplitTable.userId] = userId
                it[CommissionSplitTable.amount] = financials.commission
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

    private fun createSubmittedRemittance(
        grossIncome: BigDecimal,
        compensation: BigDecimal,
        expenses: BigDecimal,
    ): UUID = createSubmittedRemittanceForBranch(branchId, grossIncome, compensation, expenses)

    private fun createSubmittedRemittanceForBranch(
        targetBranchId: UUID,
        grossIncome: BigDecimal,
        compensation: BigDecimal,
        expenses: BigDecimal,
    ): UUID {
        val remittanceId = TestFixtures.uuid()
        val compensationUserId = TestFixtures.uuid()
        val targetDayId = findOrCreateBranchDay(targetBranchId)
        val sessionId = TestFixtures.uuid()
        val lineClientId = TestFixtures.uuid()

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
            DatabaseTestHelper.insertTestCompensation(
                targetDayId,
                compensationUserId,
                compensation,
                assignedBy = callerId,
            )
            DatabaseTestHelper.insertTestExpense(targetDayId, callerId, expenses)
            insertFinancialSnapshot(remittanceId, grossIncome, compensation, expenses)
        }
        return remittanceId
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
        val dayId = TestFixtures.uuid()
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
            it[RemittanceLineTable.id] = TestFixtures.uuid()
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
            it[RemittanceDayBreakdownTable.id] = TestFixtures.uuid()
            it[RemittanceDayBreakdownTable.remittanceId] = remittanceId
            it[RemittanceDayBreakdownTable.branchDayId] = targetDayId
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
}
