package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
)

/**
 * #350 — public accept-invite call. [token] is the raw invite code from the minted link;
 * the server stores only its hash, so this is the sole chance to redeem it.
 */
@Serializable
data class AcceptInviteRequest(
    val token: String,
    val newPassword: String,
)
