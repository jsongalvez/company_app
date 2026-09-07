package com.companyb.companyapp.contracts.reporting

import kotlinx.serialization.Serializable

@Serializable
data class DailySalesSummaryResponse(
    val branchDayId: String,
    val branchId: String,
    val date: String,
    val grossIncome: String,
    val totalCompensation: String,
    val totalExpenses: String,
    val netIncome: String,
    val totalProductSales: String,
    val totalCommission: String,
)

@Serializable
data class DailySalesSummaryBrowseResponse(
    val entries: List<DailySalesSummaryResponse>,
    val nextCursor: String? = null,
)

@Serializable
data class MonthlyRemittanceSummaryResponse(
    val branchId: String,
    val year: Int,
    val month: Int,
    val totalRemittances: Int,
    val sessionCount: Int,
    val productCount: Int,
    val grossIncome: String,
    val totalCompensation: String,
    val totalExpenses: String,
    val netIncome: String,
)
