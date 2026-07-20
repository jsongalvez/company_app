package com.companyb.companyapp.repository.model

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class DailySalesSummary(
    val branchDayId: UUID,
    val branchId: UUID,
    val date: LocalDate,
    val grossIncome: BigDecimal,
    val totalCompensation: BigDecimal,
    val totalExpenses: BigDecimal,
    val netIncome: BigDecimal,
    val totalProductSales: BigDecimal,
    val totalCommission: BigDecimal,
)

private const val STANDARD_PRECISION = 10
private const val STANDARD_SCALE = 2
private const val COMMISSION_PRECISION = 15
private const val COMMISSION_SCALE = 4

object DailySalesSummaryView : Table("daily_sales_summary") {
    val branchDayId = javaUUID("branch_day_id")
    val branchId = javaUUID("branch_id")
    val date = date("date")
    val grossIncome = decimal("gross_income", STANDARD_PRECISION, STANDARD_SCALE)
    val totalCompensation = decimal("total_compensation", STANDARD_PRECISION, STANDARD_SCALE)
    val totalExpenses = decimal("total_expenses", STANDARD_PRECISION, STANDARD_SCALE)
    val totalProductSales = decimal("total_product_sales", STANDARD_PRECISION, STANDARD_SCALE)
    val totalCommission = decimal("total_commission", COMMISSION_PRECISION, COMMISSION_SCALE)
}
