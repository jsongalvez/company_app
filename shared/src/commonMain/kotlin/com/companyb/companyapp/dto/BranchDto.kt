package com.companyb.companyapp.dto

import com.companyb.companyapp.domain.BranchType
import kotlinx.serialization.Serializable

@Serializable
data class CreateBranchRequest(
    val id: String,
    val name: String,
    val branchType: BranchType,
)

@Serializable
data class BranchResponse(
    val id: String,
    val name: String,
    val branchType: BranchType,
)

@Serializable
data class CreateAssignmentRequest(
    val id: String,
    val userId: String,
    val slot: Short,
)

@Serializable
data class UpdateSlotRequest(
    val slot: Short,
)

@Serializable
data class SwapSlotsRequest(
    val userIdA: String,
    val userIdB: String,
)

@Serializable
data class AssignmentResponse(
    val id: String,
    val userId: String,
    val branchId: String,
    val slot: Short,
    val assignedBy: String,
    val assignedAt: String,
    val endedAt: String? = null,
)
