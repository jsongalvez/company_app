package com.companyb.companyapp.reporting

import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import java.math.BigDecimal
import java.util.UUID

data class MonthlyRemittanceSummary(
    val branchId: UUID,
    val year: Int,
    val month: Int,
    val totalRemittances: Int,
    val sessionCount: Int,
    val productCount: Int,
    val grossIncome: BigDecimal,
    val totalCompensation: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal,
)

private const val FINANCIAL_PRECISION = 10
private const val FINANCIAL_SCALE = 2

internal object MonthlyRemittanceSummaryView : Table("monthly_remittance_summary") {
    val branchId = javaUUID("branch_id")
    val year = integer("year")
    val month = integer("month")
    val totalRemittances = integer("total_remittances")
    val sessionCount = integer("session_count")
    val productCount = integer("product_count")
    val grossIncome = decimal("gross_income", FINANCIAL_PRECISION, FINANCIAL_SCALE)
    val totalCompensation = decimal("total_compensation", FINANCIAL_PRECISION, FINANCIAL_SCALE)
    val totalExpenses = decimal("total_expenses", FINANCIAL_PRECISION, FINANCIAL_SCALE)
    val netIncome = decimal("net_income", FINANCIAL_PRECISION, FINANCIAL_SCALE)
}

// #547 — single monthly row mapping shared by the summary and export stores;
// the view owns net_income (SUM of snapshot nets), so readers take the column.
// Housed in an internal seam object: the mapping touches the view outside any
// `Table` body, so a top-level function would read as a public surface.
internal object MonthlyRemittanceSummaryMappers {
    fun ResultRow.toMonthlyRemittanceSummary(): MonthlyRemittanceSummary =
        MonthlyRemittanceSummary(
            branchId = this[MonthlyRemittanceSummaryView.branchId],
            year = this[MonthlyRemittanceSummaryView.year],
            month = this[MonthlyRemittanceSummaryView.month],
            totalRemittances = this[MonthlyRemittanceSummaryView.totalRemittances],
            sessionCount = this[MonthlyRemittanceSummaryView.sessionCount],
            productCount = this[MonthlyRemittanceSummaryView.productCount],
            grossIncome = this[MonthlyRemittanceSummaryView.grossIncome],
            totalCompensation = this[MonthlyRemittanceSummaryView.totalCompensation],
            totalExpenses = this[MonthlyRemittanceSummaryView.totalExpenses],
            netIncome = this[MonthlyRemittanceSummaryView.netIncome],
        )
}
