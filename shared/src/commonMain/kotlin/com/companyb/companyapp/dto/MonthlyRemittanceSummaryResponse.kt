package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

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
