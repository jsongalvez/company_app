package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class BranchDayTodayResponse(
    val branchDayId: String,
    val status: String,
)

@Serializable
data class BranchDayUserResponse(
    val userId: String,
    val displayName: String,
)
