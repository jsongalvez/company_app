package com.companyb.companyapp.contracts.branch

import kotlinx.serialization.Serializable

@Serializable
enum class BranchType { CLINIC, PROVINCIAL_TOUR, MEDICAL_MISSION }

@Serializable
enum class BranchClockInStatus { CLOCKED_IN_HERE, CLOCKED_IN_ELSEWHERE, NOT_CLOCKED_IN }

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
data class MeBranchResponse(
    val branchId: String,
    val branchName: String,
    val branchType: BranchType,
    val clockInStatus: BranchClockInStatus,
    val isRelief: Boolean,
    val assignmentId: String? = null,
    // #381 — own-assignment slot (Branch Slot, 1 = senior); null for relief rows, which
    // have no branch assignment to order.
    val slot: Short? = null,
)
