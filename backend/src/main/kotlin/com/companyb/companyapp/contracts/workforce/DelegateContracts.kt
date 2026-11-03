package com.companyb.companyapp.contracts.workforce

import kotlinx.serialization.Serializable

/**
 * What a helper works as at their mission or tour. A Manager's branch rights are a
 * Coordinator's, so these two cover every choice.
 */
@Serializable
enum class DelegateRole { PRACTITIONER, COORDINATOR }

@Serializable
data class AssignDelegateRequest(
    val delegateId: String,
    val targetUserId: String,
    val branchId: String,
    val role: DelegateRole = DelegateRole.PRACTITIONER,
)

@Serializable
data class DelegateResponse(
    val id: String,
    val targetUser: String,
    val assignedAt: String,
    val assignedBy: String,
    val branchId: String,
    val endedAt: String? = null,
    val role: DelegateRole,
)
