package com.companyb.companyapp.dto

import com.companyb.companyapp.domain.UserStatus
import kotlinx.serialization.Serializable

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
    val branchId: String,
    val branchName: String,
    val slot: Short,
)

@Serializable
data class UserCreateRequest(
    val username: String,
    val email: String,
    val displayName: String,
    val password: String,
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
