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

/** Kind of money a person got. [UNKNOWN] is the forward-compat sentinel; never sent. */
@Serializable
enum class EarningKind { PAY, ALLOWANCE, COMMISSION, UNKNOWN }

/** One clock-in of the caller. Times are ISO instants; [clockOut] is null while still clocked in. */
@Serializable
data class MyShift(
    val date: String,
    val branchName: String,
    val clockIn: String,
    val clockOut: String? = null,
)

/** One payout to the caller, dated by the day that paid it. */
@Serializable
data class MyEarning(
    val date: String,
    val branchName: String,
    val kind: EarningKind = EarningKind.UNKNOWN,
    val amount: String,
    val note: String? = null,
)

/** The caller's own shifts and money received between two dates (inclusive). */
@Serializable
data class MyHistoryResponse(
    val from: String,
    val to: String,
    val shifts: List<MyShift>,
    val earnings: List<MyEarning>,
    val totalEarned: String,
)
