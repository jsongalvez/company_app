package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClockInRequest(
    val id: String,
    val branchId: String,
)

@Serializable
data class ClockInResponse(
    val id: String,
    val branchDayId: String,
    val userId: String,
    val markedBy: String,
    val clockIn: String,
    val clockOut: String? = null,
    val isRelief: Boolean,
)
