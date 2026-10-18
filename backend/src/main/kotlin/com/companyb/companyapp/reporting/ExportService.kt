package com.companyb.companyapp.reporting

import com.companyb.companyapp.authorization.CapabilityService
import com.companyb.companyapp.branch.Branch
import com.companyb.companyapp.branch.BranchService
import com.companyb.companyapp.branchday.BranchDayService
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.branch.BranchType
import java.time.LocalDate
import java.util.UUID

/**
 * The accounting report: every session, product sale, expense, payout and remittance
 * behind a date range's totals, as an Excel workbook or a PDF.
 */
object ExportService {
    private val slugChars = Regex("[^a-z0-9]+")

    fun exportBranchReport(
        branchId: UUID,
        from: LocalDate,
        to: LocalDate,
        format: ExportFormat,
    ): ExportResult {
        val branch = BranchService.findById(branchId)
        return render(listOf(branch), branch.name, from, to, format)
    }

    /**
     * Report across the branches of [type] (all types when null) that [callerId] may read:
     * BRANCH or GLOBAL `VIEW_BRANCH_DATA`. Unreadable branches are left out, never refused.
     */
    fun exportMultiBranchReport(
        callerId: UUID,
        type: BranchType?,
        from: LocalDate,
        to: LocalDate,
        format: ExportFormat,
    ): ExportResult {
        val global =
            CapabilityService.hasCapability(
                callerId,
                CapabilityCodes.VIEW_BRANCH_DATA,
                CapabilityContextType.GLOBAL,
                CapabilityService.GLOBAL_CONTEXT_ID,
            )
        val branches =
            BranchService
                .findAll()
                .filter { type == null || it.branchType == type }
                .filter {
                    global ||
                        CapabilityService.hasCapability(
                            callerId,
                            CapabilityCodes.VIEW_BRANCH_DATA,
                            CapabilityContextType.BRANCH,
                            it.id,
                        )
                }.sortedBy { it.name }
        val label =
            when (type) {
                BranchType.PROVINCIAL_TOUR -> "All provincial tours"
                BranchType.MEDICAL_MISSION -> "All medical missions"
                BranchType.CLINIC -> "All clinics"
                else -> "All branches"
            }
        return render(branches, "$label (${branches.size})", from, to, format)
    }

    private fun render(
        branches: List<Branch>,
        label: String,
        from: LocalDate,
        to: LocalDate,
        format: ExportFormat,
    ): ExportResult {
        val ledger = LedgerRepository.load(branches.map { it.id }, from, to)
        val report = AccountingReportBuilder.build(label, from, to, ledger, BranchDayService.manilaZone)
        val bytes =
            when (format) {
                ExportFormat.XLSX -> AccountingReportWriter.xlsx(report)
                ExportFormat.PDF -> AccountingReportWriter.pdf(report)
            }
        val slug =
            label
                .lowercase()
                .replace(slugChars, "-")
                .trim('-')
                .ifEmpty { "branch" }
        return ExportResult(bytes, format.contentType, "report-$slug-$from-to-$to.${format.extension}")
    }
}
