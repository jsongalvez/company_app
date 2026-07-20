package com.companyb.companyapp.dto

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
