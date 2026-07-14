package com.companyb.companyapp.repository

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.repository.model.BranchTable
import com.companyb.companyapp.repository.model.MonthlyRemittanceSummary
import com.companyb.companyapp.repository.model.MonthlyRemittanceSummaryView
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
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

object ExportRepository {
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
            MonthlyRemittanceSummaryView
                .innerJoin(BranchTable, { MonthlyRemittanceSummaryView.branchId }, { BranchTable.id })
                .selectAll()
                .where { BranchTable.branchType eq branchType }
                .orderBy(
                    BranchTable.name to SortOrder.ASC,
                    MonthlyRemittanceSummaryView.year to SortOrder.ASC,
                    MonthlyRemittanceSummaryView.month to SortOrder.ASC,
                ).map {
                    BranchTypeMonthlySummary(
                        branchId = it[MonthlyRemittanceSummaryView.branchId],
                        branchName = it[BranchTable.name],
                        year = it[MonthlyRemittanceSummaryView.year],
                        month = it[MonthlyRemittanceSummaryView.month],
                        totalRemittances = it[MonthlyRemittanceSummaryView.totalRemittances],
                        sessionCount = it[MonthlyRemittanceSummaryView.sessionCount],
                        productCount = it[MonthlyRemittanceSummaryView.productCount],
                        netIncome = it[MonthlyRemittanceSummaryView.netIncome],
                    )
                }
        }

    fun findByBranchTypeAndMonth(
        branchType: BranchType,
        year: Int,
        month: Int,
    ): List<BranchTypeMonthlySummary> =
        transaction {
            MonthlyRemittanceSummaryView
                .innerJoin(BranchTable, { MonthlyRemittanceSummaryView.branchId }, { BranchTable.id })
                .selectAll()
                .where {
                    (BranchTable.branchType eq branchType) and
                        (MonthlyRemittanceSummaryView.year eq year) and
                        (MonthlyRemittanceSummaryView.month eq month)
                }.orderBy(BranchTable.name to SortOrder.ASC)
                .map {
                    BranchTypeMonthlySummary(
                        branchId = it[MonthlyRemittanceSummaryView.branchId],
                        branchName = it[BranchTable.name],
                        year = it[MonthlyRemittanceSummaryView.year],
                        month = it[MonthlyRemittanceSummaryView.month],
                        totalRemittances = it[MonthlyRemittanceSummaryView.totalRemittances],
                        sessionCount = it[MonthlyRemittanceSummaryView.sessionCount],
                        productCount = it[MonthlyRemittanceSummaryView.productCount],
                        netIncome = it[MonthlyRemittanceSummaryView.netIncome],
                    )
                }
        }

    private fun org.jetbrains.exposed.sql.ResultRow.toMonthlyRemittanceSummary(): MonthlyRemittanceSummary {
        val gross = this[MonthlyRemittanceSummaryView.grossIncome]
        val comp = this[MonthlyRemittanceSummaryView.totalCompensation]
        val exp = this[MonthlyRemittanceSummaryView.totalExpenses]
        return MonthlyRemittanceSummary(
            branchId = this[MonthlyRemittanceSummaryView.branchId],
            year = this[MonthlyRemittanceSummaryView.year],
            month = this[MonthlyRemittanceSummaryView.month],
            totalRemittances = this[MonthlyRemittanceSummaryView.totalRemittances],
            sessionCount = this[MonthlyRemittanceSummaryView.sessionCount],
            productCount = this[MonthlyRemittanceSummaryView.productCount],
            grossIncome = gross,
            totalCompensation = comp,
            totalExpenses = exp,
            netIncome = gross - comp - exp,
        )
    }
}
