package com.companyb.companyapp.repository.model

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

object MonthlyRemittanceSummaryView : Table("monthly_remittance_summary") {
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
