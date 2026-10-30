package com.companyb.companyapp.reporting

import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.branch.BranchType
import com.companyb.companyapp.test.TestFixtures
import com.companyb.companyapp.testsupport.fixtures.CommerceFinanceFixtures
import com.companyb.companyapp.testsupport.fixtures.IdentityFixtures
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountingReportPostgresTest : ExportServicePostgresBase() {
    private fun report(): AccountingReport =
        AccountingReportBuilder.build(
            "Test",
            today,
            today,
            LedgerRepository.load(listOf(branchId), today, today),
            BranchDayService.manilaZone,
        )

    private fun AccountingReport.table(name: String) = tables.single { it.name == name }

    private fun ReportTable.amount(label: String): BigDecimal =
        (rows.single { (it[0] as? ReportCell.Text)?.value == label }[2] as ReportCell.Money).value

    private fun seed() {
        seedDayFinancials(
            branchDayId,
            DayFinancials(
                gross = BigDecimal("2500.00"),
                comp = BigDecimal("500.00"),
                expense = BigDecimal("300.00"),
                productSales = BigDecimal("400.00"),
                commission = BigDecimal("40.00"),
            ),
        )
        CommerceFinanceFixtures.insertTestExpense(branchDayId, callerId, BigDecimal("999.00"), deleted = true)
    }

    @Test
    fun `summary totals come from the line items`() {
        seed()
        val summary = report().table("Summary")
        assertEquals(BigDecimal("2500.00"), summary.amount("Sessions done"))
        assertEquals(BigDecimal("400.00"), summary.amount("Product sales"))
        assertEquals(BigDecimal("2900.00"), summary.amount("Total taken in"))
        assertEquals(BigDecimal("500.00"), summary.amount("Pay"))
        assertEquals(BigDecimal("300.00"), summary.amount("Expenses"))
        assertEquals(BigDecimal("1700.00"), summary.amount("Net from sessions (sessions done - pay - expenses)"))
        assertEquals(BigDecimal("999.00"), summary.amount("Deleted expenses"))
    }

    @Test
    fun `every line is listed and deleted expenses are not counted`() {
        seed()
        val r = report()
        assertEquals(1, r.table("Sessions").rows.size)
        assertEquals(1, r.table("Product sales").rows.size)
        val expenses = r.table("Expenses")
        assertEquals(2, expenses.rows.size)
        assertEquals(ReportCell.Money(BigDecimal("300.00")), expenses.totals!![7])
        val days = r.table("Days")
        assertEquals(1, days.rows.size)
        assertEquals(ReportCell.Money(BigDecimal("1700.00")), days.rows.single()[8])
        assertEquals(1, r.table("Pay").rows.size)
    }

    @Test
    fun `the report never shows commission, though the day has some`() {
        seed()
        val words =
            report().tables.flatMap { t ->
                listOf(t.title) + t.headers +
                    (t.rows + listOfNotNull(t.totals)).flatten().mapNotNull { (it as? ReportCell.Text)?.value }
            }
        assertEquals(emptyList(), words.filter { it.contains("commission", ignoreCase = true) })
    }

    @Test
    fun `excel and pdf files are produced`() {
        seed()
        val xlsx = ExportService.exportBranchReport(branchId, today, today, ExportFormat.XLSX)
        assertEquals("PK", String(xlsx.bytes.copyOfRange(0, 2)))
        assertTrue(xlsx.fileName.endsWith(".xlsx"))
        val pdf = ExportService.exportBranchReport(branchId, today, today, ExportFormat.PDF)
        assertEquals("%PDF", String(pdf.bytes.copyOfRange(0, 4)))
    }

    @Test
    fun `empty range still gives a report`() {
        val empty =
            ExportService.exportBranchReport(
                branchId,
                today.minusYears(1),
                today.minusYears(1),
                ExportFormat.XLSX,
            )
        assertEquals("PK", String(empty.bytes.copyOfRange(0, 2)))
    }

    @Test
    fun `multi-branch report leaves out branches the caller cannot read`() {
        val stranger = TestFixtures.uuid()
        IdentityFixtures.insertTestUser(stranger, "no-grants")
        val result = ExportService.exportMultiBranchReport(stranger, BranchType.CLINIC, today, today, ExportFormat.PDF)
        assertTrue(result.fileName.startsWith("report-all-clinics-0-"), result.fileName)
    }
}
