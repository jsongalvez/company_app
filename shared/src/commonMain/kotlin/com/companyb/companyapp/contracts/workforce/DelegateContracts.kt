package com.companyb.companyapp.contracts.workforce

import kotlinx.serialization.Serializable

@Serializable
data class AssignDelegateRequest(
    val delegateId: String,
    val targetUserId: String,
    val branchId: String,
)

@Serializable
data class DelegateResponse(
    val id: String,
    val targetUser: String,
    val assignedAt: String,
    val assignedBy: String,
    val branchId: String,
    val endedAt: String? = null,
)
