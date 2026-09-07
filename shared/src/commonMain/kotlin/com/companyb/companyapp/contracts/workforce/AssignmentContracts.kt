package com.companyb.companyapp.contracts.workforce

import kotlinx.serialization.Serializable

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
    val assignmentIdA: String,
    val assignmentIdB: String,
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

/**
 * #366 — one active member of a branch, the requested-practitioner picker's row shape.
 * Deliberately no username: practitioners without MANAGE_USERS must not get a directory
 * of credential identifiers, only display names.
 */
@Serializable
data class BranchMemberResponse(
    val id: String,
    val displayName: String,
)
