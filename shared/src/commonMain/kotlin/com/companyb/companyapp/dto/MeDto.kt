package com.companyb.companyapp.dto

import com.companyb.companyapp.domain.BranchClockInStatus
import com.companyb.companyapp.domain.BranchType
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.domain.CapabilitySourceType
import com.companyb.companyapp.domain.UserStatus
import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val status: UserStatus,
    val createdAt: String,
)

@Serializable
data class UserCapabilityResponse(
    val capabilityCode: String,
    val contextType: CapabilityContextType,
    val contextId: String,
    val sourceType: CapabilitySourceType,
)

@Serializable
data class MeBranchResponse(
    val branchId: String,
    val branchName: String,
    val branchType: BranchType,
    val clockInStatus: BranchClockInStatus,
    val isRelief: Boolean,
    // #381 — own-assignment slot (Branch Slot, 1 = senior); null for relief rows, which
    // have no branch assignment to order.
    val slot: Short? = null,
)
