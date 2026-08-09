package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class ClockInRequest(
    val attendanceId: String,
    val branchId: String,
)

@Serializable
data class ClockOutRequest(
    val attendanceId: String,
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

@Serializable
data class ClockOutResponse(
    val id: String,
    val branchDayId: String,
    val userId: String,
    val markedBy: String,
    val clockIn: String,
    val clockOut: String?,
    val isRelief: Boolean,
)

@Serializable
data class ReliefAccessRequest(
    val requestId: String,
    val branchDayId: String,
    val targetUserId: String,
    val reason: String? = null,
)

@Serializable
data class GrantReliefAccessRequest(
    val reason: String? = null,
)

@Serializable
data class DenyReliefAccessRequest(
    val reason: String? = null,
)

@Serializable
data class ReliefAccessResponse(
    val id: String,
    val branchDayId: String,
    val requestedBy: String,
    val requestStatus: String,
    val targetUser: String,
    val grantedBy: String? = null,
    val grantedAt: String? = null,
)
