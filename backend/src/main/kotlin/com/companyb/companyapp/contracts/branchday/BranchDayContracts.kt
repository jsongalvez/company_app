package com.companyb.companyapp.contracts.branchday

import kotlinx.serialization.Serializable

@Serializable
enum class DayStatus { OPEN, PAST, REMITTED }

@Serializable
data class BranchDayTodayResponse(
    val branchDayId: String,
    val status: DayStatus,
)

@Serializable
data class BranchDayUserResponse(
    val userId: String,
    val displayName: String,
)
