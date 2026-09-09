package com.companyb.companyapp.reporting

import com.companyb.companyapp.contracts.session.SessionStatus
import com.companyb.companyapp.contracts.session.SessionType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import com.companyb.companyapp.testsupport.fixtures.SessionClientFixtures
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportServicePostgresTest : ExportServicePostgresBase() {
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
        SessionClientFixtures.insertTestClient(testClientId)
        val sessionId = TestFixtures.uuid()
        SessionClientFixtures.insertTestSession(
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
        IdentityFixtures.insertUser(
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
}
