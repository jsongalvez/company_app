package com.companyb.companyapp.dto

import com.companyb.companyapp.domain.BranchClockInStatus
import com.companyb.companyapp.domain.BranchType
import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(
    val id: String,
    val username: String,
    val status: String,
    val createdAt: String,
)

@Serializable
data class UserCapabilityResponse(
    val capabilityCode: String,
    val contextType: String,
    val contextId: String,
    val sourceType: String,
)

@Serializable
data class MeBranchResponse(
    val branchId: String,
    val branchName: String,
    val branchType: BranchType,
    val clockInStatus: BranchClockInStatus,
    val isRelief: Boolean,
)
