package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class BranchDayTodayResponse(
    val branchDayId: String,
    val status: String,
)
