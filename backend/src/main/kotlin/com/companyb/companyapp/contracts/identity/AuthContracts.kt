package com.companyb.companyapp.contracts.identity

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
 * public accept-invite call. [token] is the raw invite code from the minted link;
 * the server stores only its hash, so this is the sole chance to redeem it.
 */
@Serializable
data class AcceptInviteRequest(
    val token: String,
    val newPassword: String,
)

/**
 * public reset request. [identifier] matches username or email; the response is
 * uniform 204 whether or not an account exists (enumeration resistance).
 */
@Serializable
data class ForgotPasswordRequest(
    val identifier: String,
)

/** public reset redemption; same shape as [AcceptInviteRequest]. */
@Serializable
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String,
)
