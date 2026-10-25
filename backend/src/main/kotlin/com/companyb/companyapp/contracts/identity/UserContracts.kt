package com.companyb.companyapp.contracts.identity

import kotlinx.serialization.Serializable

/**
 * [UNKNOWN] is the forward-compat sentinel (see "Wire enum evolution policy" in
 * `docs/architecture.md`). Never persisted, never sent.
 */
@Serializable
enum class UserStatus { ACTIVE, INACTIVE, UNKNOWN }

@Serializable
data class MeResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val email: String = "",
    val status: UserStatus = UserStatus.UNKNOWN,
    val createdAt: String,
    /** True for a SUPERUSER (developer), who may "view as" other people. */
    val canViewAs: Boolean = false,
)

@Serializable
data class ViewAsRequest(
    val userId: String,
    val allowChanges: Boolean = false,
)

@Serializable
data class StopViewAsRequest(
    val userId: String,
    /** "stopped", "changes allowed" or "changes off". */
    val event: String = "stopped",
)

@Serializable
data class ViewAsResponse(
    val userId: String,
    val username: String,
    val displayName: String,
    val roles: List<String>,
    val allowChanges: Boolean,
)

@Serializable
data class UserSummaryResponse(
    val id: String,
    val username: String,
    val displayName: String,
    val status: UserStatus = UserStatus.UNKNOWN,
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
 * admin mint of a single-use invite link (decision 2). No password field:
 * the admin never knows a credential — the invitee sets their own password on accept.
 * [roles] follows the create-user shape (full-replace bundle, SUPERUSER guarded).
 * [username], [email] and [displayName] are all filled or all blank; blank means the
 * invitee supplies them when they accept.
 */
@Serializable
data class InviteMintRequest(
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
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

/** Self-service profile edit: the caller changes their own name and email. */
@Serializable
data class UpdateProfileRequest(
    val displayName: String,
    val email: String,
)

/** Self-service password change: proves the current password first. */
@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)
