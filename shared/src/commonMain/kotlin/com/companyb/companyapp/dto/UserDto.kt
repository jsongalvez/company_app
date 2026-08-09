package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserSummaryResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val status: String,
    val deactivatedAt: String? = null,
    val assignments: List<UserAssignmentResponse> = emptyList(),
)

@Serializable
data class UserAssignmentResponse(
    val branchId: String,
    val branchName: String,
    val slot: Short,
)
