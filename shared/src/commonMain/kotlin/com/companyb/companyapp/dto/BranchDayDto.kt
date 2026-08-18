package com.companyb.companyapp.dto

import com.companyb.companyapp.domain.DayStatus
import kotlinx.serialization.Serializable

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
