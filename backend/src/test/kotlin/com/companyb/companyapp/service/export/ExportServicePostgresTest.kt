package com.companyb.companyapp.service.export

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.SessionType
import com.companyb.companyapp.repository.model.AppUserTable
import com.companyb.companyapp.repository.model.BranchDayTable
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.repository.model.ClientTable
import com.companyb.companyapp.repository.model.CompensationTable
import com.companyb.companyapp.repository.model.ExpenseTable
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
import com.companyb.companyapp.repository.model.UserCapabilityTable
import com.companyb.companyapp.service.CapabilityService
import com.companyb.companyapp.test.BasePostgresTest
import com.companyb.companyapp.test.DatabaseTestHelper
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExportServicePostgresTest : BasePostgresTest() {
    private val callerId = UUID.randomUUID()
    private val sourceId = UUID.randomUUID()
    private val branchId = UUID.randomUUID()
    private val branchDayId = UUID.randomUUID()
    private val today = LocalDate.now(java.time.ZoneId.of("Asia/Manila"))
    private val testBranchIds = mutableListOf(branchId)

    override fun initTestData() {
        DatabaseTestHelper.insertUser(
            id = callerId,
            username = "export-user-${callerId.toString().take(8)}",
            passwordHash = "hash",
            email = "${callerId.toString().take(8)}@test.com",
            displayName = "Export User",
        )
        trackOwned(AppUserTable, AppUserTable.id, callerId)
        testBranchIds.add(branchId)
        DatabaseTestHelper.insertTestBranch(branchId, "Export Test Branch ${UUID.randomUUID()}", BranchType.CLINIC)
        trackOwned(BranchTable, BranchTable.id, branchId)
        insertBranchDay(branchDayId, branchId, today)
        trackOwned(BranchDayTable, BranchDayTable.id, branchDayId)
        for (bid in testBranchIds) {
            trackOwned(BranchDayTable, BranchDayTable.branchId, bid)
        }
        grantViewBranchData(callerId)
        trackOwned(UserCapabilityTable, UserCapabilityTable.userId, callerId)
        trackOwned(CompensationTable, CompensationTable.assignedBy, callerId)
        trackOwned(ExpenseTable, ExpenseTable.createdBy, callerId)
    }

    @Suppress("MaxLineLength")
    @AfterTest
    override fun tearDownBase() {
        if (!DatabaseTestHelper.isDatabaseReady()) return
        DatabaseTestHelper.withSnapshotTriggerDisabled {
            for (bid in testBranchIds) {
                val bdIds = "SELECT id FROM branch_day WHERE branch_id = '$bid'"
                val remIds = "SELECT id FROM remittance WHERE branch_id = '$bid'"
                exec("DELETE FROM remittance_financial_snapshot WHERE remittance_id IN ($remIds)")
                exec("DELETE FROM remittance_day_breakdown WHERE remittance_id IN ($remIds)")
                exec("DELETE FROM remittance_line WHERE remittance_id IN ($remIds)")
                exec("DELETE FROM remittance WHERE branch_id = '$bid'")
                exec(
                    "DELETE FROM inventory_movement WHERE product_sale_id IN (SELECT id FROM product_sale WHERE branch_day_id IN ($bdIds))",
                )
                exec("DELETE FROM product_sale WHERE branch_day_id IN ($bdIds)")
                exec(
                    "DELETE FROM notification WHERE session_id IN (SELECT id FROM session WHERE branch_day_id IN ($bdIds))",
                )
                exec(
                    "DELETE FROM session_void WHERE session_id IN (SELECT id FROM session WHERE branch_day_id IN ($bdIds))",
                )
                exec(
                    "DELETE FROM session_practitioner WHERE session_id IN (SELECT id FROM session WHERE branch_day_id IN ($bdIds))",
                )
                exec(
                    "DELETE FROM session_concern WHERE session_id IN (SELECT id FROM session WHERE branch_day_id IN ($bdIds))",
                )
                exec("DELETE FROM session WHERE branch_day_id IN ($bdIds)")
                exec(
                    "DELETE FROM client WHERE id IN (SELECT client_id FROM (SELECT client_id FROM session WHERE branch_day_id IN ($bdIds)) AS cids)",
                )
                exec(
                    "DELETE FROM compensation WHERE work_branch_day_id IN ($bdIds) OR paying_branch_day_id IN ($bdIds)",
                )
                exec("DELETE FROM expense WHERE branch_day_id IN ($bdIds)")
                exec("DELETE FROM branch_day WHERE branch_id = '$bid'")
            }
            cleanTrackedRows()
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
        val testClientId = UUID.randomUUID()
        DatabaseTestHelper.insertTestClient(testClientId)
        trackOwned(ClientTable, ClientTable.id, testClientId)
        val sessionId = UUID.randomUUID()
        DatabaseTestHelper.insertTestSession(
            id = sessionId,
            clientId = testClientId,
            branchDayId = branchDayId,
            sessionType = SessionType.REGULAR,
            sessionStatus = SessionStatus.COMPLETED,
            basePrice = BigDecimal("2500.00"),
            finalPrice = BigDecimal("2500.00"),
        )
        trackOwned(SessionTable, SessionTable.branchDayId, branchDayId)
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
        trackOwned(AppUserTable, AppUserTable.id, otherUserId)
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
    fun `provincial export CSV returns branch data`() {
        val provBranchId = UUID.randomUUID()
        testBranchIds.add(provBranchId)
        DatabaseTestHelper.insertTestBranch(
            provBranchId,
            "Prov Branch ${UUID.randomUUID()}",
            BranchType.PROVINCIAL_TOUR,
        )
        trackOwned(BranchTable, BranchTable.id, provBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, provBranchId)
        val provDayId = UUID.randomUUID()
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
    fun `medical mission export CSV returns branch data`() {
        val mmBranchId = UUID.randomUUID()
        testBranchIds.add(mmBranchId)
        DatabaseTestHelper.insertTestBranch(mmBranchId, "MM Branch ${UUID.randomUUID()}", BranchType.MEDICAL_MISSION)
        trackOwned(BranchTable, BranchTable.id, mmBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, mmBranchId)
        val mmDayId = UUID.randomUUID()
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
        testBranchIds.add(provBranchId)
        DatabaseTestHelper.insertTestBranch(
            provBranchId,
            "Prov Month Branch ${UUID.randomUUID()}",
            BranchType.PROVINCIAL_TOUR,
        )
        trackOwned(BranchTable, BranchTable.id, provBranchId)
        trackOwned(BranchDayTable, BranchDayTable.branchId, provBranchId)
        val provDayId = UUID.randomUUID()
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
