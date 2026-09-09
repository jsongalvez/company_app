package com.companyb.companyapp.reporting

import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.exception.NotFoundException
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.BranchWorkforceFixtures
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Monthly/all-time/branch-type export suite (RED repair split from
 * [ExportServicePostgresTest]): shares [ExportServicePostgresBase] so each
 * class stays under the LargeClass pin. No behavior change.
 */
class ExportServiceMonthlyBranchPostgresTest : ExportServicePostgresBase() {
    @Test
    fun `monthly export throws 404 when no remittance data`() {
        assertFailsWith<NotFoundException> {
            ExportService.exportMonthly(branchId, 2099, 1, ExportFormat.CSV)
        }
    }

    @Test
    fun `monthly export reports PRODUCT line revenue`() {
        createSubmittedProductRemittance(BigDecimal("750.00"))
        val result = ExportService.exportMonthly(branchId, today.year, today.monthValue, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("750.00"), "expected PRODUCT revenue 750.00 but got: $csv")
    }

    @Test
    fun `all-time export reports PRODUCT line revenue`() {
        createSubmittedProductRemittance(BigDecimal("750.00"))
        val result = ExportService.exportAllTime(branchId, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("750.00"), "expected PRODUCT revenue 750.00 but got: $csv")
    }

    @Test
    fun `monthly export reconciles SESSION snapshot with PRODUCT lines`() {
        createSubmittedRemittance(BigDecimal("5000.00"), BigDecimal("1000.00"), BigDecimal("500.00"))
        createSubmittedProductRemittance(BigDecimal("750.00"))
        val result = ExportService.exportMonthly(branchId, today.year, today.monthValue, ExportFormat.CSV)
        val csv = String(result.bytes, Charsets.UTF_8)
        // SESSION net 3500.00 + PRODUCT 750.00 = 4250.00; gross 5000.00 + 750.00 = 5750.00.
        assertTrue(csv.contains("5750.00"), "expected reconciled gross 5750.00 but got: $csv")
        assertTrue(csv.contains("4250.00"), "expected reconciled net 4250.00 but got: $csv")
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
    fun `provincial export reports PRODUCT line revenue`() {
        val provBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(
            provBranchId,
            "Prov Product Branch ${TestFixtures.uuid()}",
            BranchType.PROVINCIAL_TOUR,
        )
        insertBranchDay(TestFixtures.uuid(), provBranchId, today)
        createSubmittedProductRemittance(BigDecimal("750.00"), provBranchId)
        val result =
            ExportService.exportByBranchType(
                BranchType.PROVINCIAL_TOUR,
                null,
                null,
                ExportFormat.CSV,
            )
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("750.00"), "expected PRODUCT revenue 750.00 but got: $csv")
    }

    @Test
    fun `provincial export CSV returns branch data`() {
        val provBranchId = TestFixtures.uuid()
        BranchWorkforceFixtures.insertTestBranch(
            provBranchId,
            "Prov Branch ${TestFixtures.uuid()}",
            BranchType.PROVINCIAL_TOUR,
        )
        val provDayId = TestFixtures.uuid()
        insertBranchDay(provDayId, provBranchId, today)
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
        BranchWorkforceFixtures.insertTestBranch(
            mmBranchId,
            "MM Branch ${TestFixtures.uuid()}",
            BranchType.MEDICAL_MISSION,
        )
        val mmDayId = TestFixtures.uuid()
        insertBranchDay(mmDayId, mmBranchId, today)
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
        BranchWorkforceFixtures.insertTestBranch(
            provBranchId,
            "Prov Month Branch ${TestFixtures.uuid()}",
            BranchType.PROVINCIAL_TOUR,
        )
        val provDayId = TestFixtures.uuid()
        insertBranchDay(provDayId, provBranchId, today)
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

    @Test
    fun `branch type export neutralizes formula prefixes while keeping amounts numeric`() {
        listOf("=1+1", "+1+1", "@SUM(1)", "Com,ma \"Q\"", "lone\rcr", "crlf\r\nbreak")
            .forEach { seedFormulaBranch(it) }
        val result =
            ExportService.exportByBranchType(
                BranchType.PROVINCIAL_TOUR,
                null,
                null,
                ExportFormat.CSV,
            )
        val csv = String(result.bytes, Charsets.UTF_8)
        assertTrue(csv.contains("'=1+1"), "formula branch must be single-quote prefixed but got: $csv")
        assertTrue(csv.contains("'+1+1"), "formula branch must be single-quote prefixed but got: $csv")
        assertTrue(csv.contains("'@SUM(1)"), "formula branch must be single-quote prefixed but got: $csv")
        assertFalse(csv.contains("\n=1+1"), "unescaped formula cell must not remain but got: $csv")
        assertFalse(csv.contains("\n+1+1"), "unescaped formula cell must not remain but got: $csv")
        assertFalse(csv.contains("\n@SUM"), "unescaped formula cell must not remain but got: $csv")
        assertTrue(csv.contains("\"Com,ma \"\"Q\"\"\""), "comma/quote branch must stay quoted but got: $csv")
        assertTrue(csv.contains("\"lone\rcr\""), "lone CR must stay quoted but got: $csv")
        assertTrue(csv.contains("\"crlf\r\nbreak\""), "CRLF must stay quoted but got: $csv")
        assertTrue(csv.contains("1500.00"), "expected numeric net 1500.00 but got: $csv")
        assertTrue(csv.contains("-300.00"), "expected numeric negative net but got: $csv")
        assertFalse(csv.contains("'-300.00"), "negative amount must stay numeric but got: $csv")
        assertFalse(csv.contains("'1500.00"), "amount must stay numeric but got: $csv")
        val pdf =
            ExportService.exportByBranchType(
                BranchType.PROVINCIAL_TOUR,
                null,
                null,
                ExportFormat.PDF,
            )
        assertContentEquals(
            byteArrayOf(0x25, 0x50, 0x44, 0x46),
            pdf.bytes.take(4).toByteArray(),
        )
    }
}
