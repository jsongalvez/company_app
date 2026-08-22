package com.companyb.companyapp.dto

import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.ReliefAccessStatus
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
    val branchId: String,
    /** ISO yyyy-MM-dd; null = the current operational day (Asia/Manila). Future dates allowed. */
    val date: String? = null,
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
    val requestStatus: ReliefAccessStatus,
    val grantedBy: String? = null,
    val grantedAt: String? = null,
    /** Branch context — populated on the mine list only (the per-day read implies it). */
    val branchId: String? = null,
    val branchName: String? = null,
    val date: String? = null,
)

@Serializable
data class ReliefBranchOptionResponse(
    val branchId: String,
    val branchName: String,
    val branchType: BranchType,
)
