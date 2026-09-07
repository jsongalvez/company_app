package com.companyb.companyapp.contracts.identity

import kotlinx.serialization.Serializable

@Serializable
enum class UserStatus { ACTIVE, INACTIVE }

@Serializable
data class MeResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val status: UserStatus,
    val createdAt: String,
)

@Serializable
data class UserSummaryResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val status: UserStatus,
    val deactivatedAt: String? = null,
    val assignments: List<UserAssignmentResponse> = emptyList(),
    val roles: List<String> = emptyList(),
)

@Serializable
data class UserAssignmentResponse(
    val assignmentId: String,
    val branchId: String,
    val branchName: String,
    val slot: Short,
)

/**
 * #350 — admin mint of a single-use invite link (#346 decision 2). No password field:
 * the admin never knows a credential — the invitee sets their own password on accept.
 * [roles] follows the #344 create-user shape (full-replace bundle, SUPERUSER guarded).
 */
@Serializable
data class InviteMintRequest(
    val username: String,
    val email: String,
    val displayName: String,
    val roles: List<String> = emptyList(),
)

@Serializable
data class InviteMintResponse(
    val userId: String,
    val inviteCode: String,
    /** ISO-8601 instant after which the code is dead. */
    val expiresAt: String,
)

@Serializable
data class UserRoleReplaceRequest(
    val roles: List<String>,
)

@Serializable
data class RoleResponse(
    val name: String,
    val capabilities: List<String>,
)
