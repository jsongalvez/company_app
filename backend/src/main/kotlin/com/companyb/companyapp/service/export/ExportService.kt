package com.companyb.companyapp.service.export

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.BranchRepository
import com.companyb.companyapp.repository.ExportRepository
import com.companyb.companyapp.repository.model.Branch
import com.companyb.companyapp.repository.model.CapabilityContextType
import com.companyb.companyapp.service.CapabilityService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.BadRequestResponse
import io.javalin.http.ForbiddenResponse
import io.javalin.http.NotFoundResponse
import java.time.LocalDate
import java.time.Month
import java.util.UUID

@Suppress("TooManyFunctions")
object ExportService {
    private val logger = KotlinLogging.logger {}

    private const val VIEW_BRANCH_DATA = "VIEW_BRANCH_DATA"

    fun exportDaily(
        callerId: UUID,
        branchId: UUID,
        date: LocalDate,
        format: ExportFormat,
    ): ExportResult {
        val branch = authorizeAndFindBranch(callerId, branchId)

        val summary =
            com.companyb.companyapp.repository.DailySalesSummaryRepository
                .findByBranchAndDate(branchId, date)
                ?: throw NotFoundResponse("No data for this branch and date")

        val title = "Daily Sales Summary - ${branch.name} - ${summary.date}"
        val headers = dailyHeaders()
        val rows =
            listOf(
                listOf(
                    summary.grossIncome.toPlainString(),
                    summary.totalCompensation.toPlainString(),
                    summary.totalExpenses.toPlainString(),
                    summary.netIncome.toPlainString(),
                    summary.totalProductSales.toPlainString(),
                    summary.totalCommission.toPlainString(),
                ),
            )
        return buildResult(title, headers, rows, format, "daily-sales-${branch.name}-$date")
    }

    fun exportMonthly(
        callerId: UUID,
        branchId: UUID,
        year: Int,
        month: Int,
        format: ExportFormat,
    ): ExportResult {
        val branch = authorizeAndFindBranch(callerId, branchId)

        val summary =
            com.companyb.companyapp.repository.MonthlyRemittanceSummaryRepository.findByBranchYearMonth(
                branchId,
                year,
                month,
            )
                ?: throw NotFoundResponse("No remittance data for this branch, year, and month")

        val monthName = Month.of(month)
        val title = "Monthly Remittance Summary - ${branch.name} - $year $monthName"
        val headers = monthlyHeaders()
        val rows =
            listOf(
                listOf(
                    summary.totalRemittances.toString(),
                    summary.sessionCount.toString(),
                    summary.productCount.toString(),
                    summary.grossIncome.toPlainString(),
                    summary.totalCompensation.toPlainString(),
                    summary.totalExpenses.toPlainString(),
                    summary.netIncome.toPlainString(),
                ),
            )
        return buildResult(title, headers, rows, format, "monthly-remittance-${branch.name}-$year-$month")
    }

    fun exportAllTime(
        callerId: UUID,
        branchId: UUID,
        format: ExportFormat,
    ): ExportResult {
        val branch = authorizeAndFindBranch(callerId, branchId)

        val summaries = ExportRepository.findAllTimeByBranch(branchId)
        if (summaries.isEmpty()) {
            throw NotFoundResponse("No remittance data for this branch")
        }

        val title = "All-Time Remittance Summary - ${branch.name}"
        val headers = allTimeHeaders()
        val rows = summaries.map { s -> allTimeRow(s) }
        return buildResult(title, headers, rows, format, "all-time-remittance-${branch.name}")
    }

    fun exportByBranchType(
        callerId: UUID,
        branchType: BranchType,
        year: Int?,
        month: Int?,
        format: ExportFormat,
    ): ExportResult {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = VIEW_BRANCH_DATA,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[EXPORT] User $callerId lacks $VIEW_BRANCH_DATA capability" }
            throw ForbiddenResponse("VIEW_BRANCH_DATA capability required")
        }

        val typeName = branchTypeName(branchType)
        val summaries = fetchBranchTypeSummaries(branchType, year, month)

        val titleSuffix =
            if (year != null && month != null) {
                " - $year ${Month.of(month)}"
            } else {
                ""
            }
        val title = "$typeName Reports$titleSuffix"
        val headers = branchTypeHeaders()
        val rows = summaries.map { s -> branchTypeRow(s) }
        val fileBase = "${typeName.lowercase().replace(" ", "-")}-reports"
        return buildResult(title, headers, rows, format, fileBase)
    }

    fun parseFormat(formatParam: String?): ExportFormat =
        when (formatParam?.lowercase()) {
            "csv" -> ExportFormat.CSV
            "pdf" -> ExportFormat.PDF
            else -> throw BadRequestResponse("format query param is required (csv or pdf)")
        }

    private fun authorizeAndFindBranch(
        callerId: UUID,
        branchId: UUID,
    ): Branch {
        val authorized =
            CapabilityService.hasCapability(
                userId = callerId,
                capabilityCode = VIEW_BRANCH_DATA,
                contextType = CapabilityContextType.GLOBAL,
                contextId = CapabilityService.GLOBAL_CONTEXT_ID,
            )
        if (!authorized) {
            logger.warn { "[EXPORT] User $callerId lacks $VIEW_BRANCH_DATA capability" }
            throw ForbiddenResponse("VIEW_BRANCH_DATA capability required")
        }
        return BranchRepository.findById(branchId)
            ?: throw NotFoundResponse("Branch not found")
    }

    private fun buildResult(
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
        format: ExportFormat,
        fileBase: String,
    ): ExportResult =
        when (format) {
            ExportFormat.CSV -> {
                ExportResult(
                    bytes = CsvExporter.generate(headers, rows),
                    contentType = format.contentType,
                    fileName = "$fileBase.${format.extension}",
                )
            }

            ExportFormat.PDF -> {
                ExportResult(
                    bytes = PdfExporter.generate(title, headers, rows),
                    contentType = format.contentType,
                    fileName = "$fileBase.${format.extension}",
                )
            }
        }

    private fun branchTypeName(branchType: BranchType): String =
        when (branchType) {
            BranchType.PROVINCIAL_TOUR -> "Provincial Tour"
            BranchType.MEDICAL_MISSION -> "Medical Mission"
            BranchType.CLINIC -> throw BadRequestResponse("Use branch-specific export for clinic branches")
        }

    private fun fetchBranchTypeSummaries(
        branchType: BranchType,
        year: Int?,
        month: Int?,
    ): List<com.companyb.companyapp.repository.BranchTypeMonthlySummary> {
        val summaries =
            if (year != null && month != null) {
                ExportRepository.findByBranchTypeAndMonth(branchType, year, month)
            } else {
                ExportRepository.findByBranchType(branchType)
            }
        if (summaries.isEmpty()) {
            val typeName = branchTypeName(branchType)
            throw NotFoundResponse("No remittance data for $typeName branches")
        }
        return summaries
    }

    private fun dailyHeaders(): List<String> =
        listOf("Gross Income", "Total Compensation", "Total Expenses", "Net Income", "Product Sales", "Commission")

    private fun monthlyHeaders(): List<String> =
        listOf("Remittances", "Sessions", "Products", "Gross Income", "Compensation", "Expenses", "Net Income")

    private fun allTimeHeaders(): List<String> =
        listOf(
            "Year",
            "Month",
            "Remittances",
            "Sessions",
            "Products",
            "Gross Income",
            "Compensation",
            "Expenses",
            "Net Income",
        )

    private fun allTimeRow(s: com.companyb.companyapp.repository.model.MonthlyRemittanceSummary): List<String> =
        listOf(
            s.year.toString(),
            Month.of(s.month).name,
            s.totalRemittances.toString(),
            s.sessionCount.toString(),
            s.productCount.toString(),
            s.grossIncome.toPlainString(),
            s.totalCompensation.toPlainString(),
            s.totalExpenses.toPlainString(),
            s.netIncome.toPlainString(),
        )

    private fun branchTypeHeaders(): List<String> =
        listOf("Branch", "Year", "Month", "Remittances", "Sessions", "Products", "Net Income")

    private fun branchTypeRow(s: com.companyb.companyapp.repository.BranchTypeMonthlySummary): List<String> =
        listOf(
            s.branchName,
            s.year.toString(),
            Month.of(s.month).name,
            s.totalRemittances.toString(),
            s.sessionCount.toString(),
            s.productCount.toString(),
            s.netIncome.toPlainString(),
        )
}
