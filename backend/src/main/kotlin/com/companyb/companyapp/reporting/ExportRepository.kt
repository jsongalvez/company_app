package com.companyb.companyapp.reporting

import com.companyb.companyapp.branch.BranchTable
import com.companyb.companyapp.domain.BranchType
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.math.BigDecimal
import java.util.UUID

data class BranchTypeMonthlySummary(
    val branchId: UUID,
    val branchName: String,
    val year: Int,
    val month: Int,
    val totalRemittances: Int,
    val sessionCount: Int,
    val productCount: Int,
    val netIncome: java.math.BigDecimal,
)

internal object ExportRepository {
    fun findAllTimeByBranch(branchId: UUID): List<MonthlyRemittanceSummary> =
        transaction {
            MonthlyRemittanceSummaryView
                .selectAll()
                .where { MonthlyRemittanceSummaryView.branchId eq branchId }
                .orderBy(
                    MonthlyRemittanceSummaryView.year to SortOrder.ASC,
                    MonthlyRemittanceSummaryView.month to SortOrder.ASC,
                ).map { it.toMonthlyRemittanceSummary() }
        }

    fun findByBranchType(branchType: BranchType): List<BranchTypeMonthlySummary> =
        transaction {
            branchTypeSummaryQuery()
                .where { BranchTable.branchType eq branchType }
                .orderBy(
                    BranchTable.name to SortOrder.ASC,
                    MonthlyRemittanceSummaryView.year to SortOrder.ASC,
                    MonthlyRemittanceSummaryView.month to SortOrder.ASC,
                ).map { it.toBranchTypeMonthlySummary() }
        }

    fun findByBranchTypeAndMonth(
        branchType: BranchType,
        year: Int,
        month: Int,
    ): List<BranchTypeMonthlySummary> =
        transaction {
            branchTypeSummaryQuery()
                .where {
                    (BranchTable.branchType eq branchType) and
                        (MonthlyRemittanceSummaryView.year eq year) and
                        (MonthlyRemittanceSummaryView.month eq month)
                }.orderBy(BranchTable.name to SortOrder.ASC)
                .map { it.toBranchTypeMonthlySummary() }
        }

    private fun branchTypeSummaryQuery() =
        MonthlyRemittanceSummaryView
            .innerJoin(BranchTable, { MonthlyRemittanceSummaryView.branchId }, { BranchTable.id })
            .selectAll()

    private fun org.jetbrains.exposed.v1.core.ResultRow.toBranchTypeMonthlySummary() =
        BranchTypeMonthlySummary(
            branchId = this[MonthlyRemittanceSummaryView.branchId],
            branchName = this[BranchTable.name],
            year = this[MonthlyRemittanceSummaryView.year],
            month = this[MonthlyRemittanceSummaryView.month],
            totalRemittances = this[MonthlyRemittanceSummaryView.totalRemittances],
            sessionCount = this[MonthlyRemittanceSummaryView.sessionCount],
            productCount = this[MonthlyRemittanceSummaryView.productCount],
            netIncome = this[MonthlyRemittanceSummaryView.netIncome],
        )
}
