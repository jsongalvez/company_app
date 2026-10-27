package com.companyb.companyapp.contracts.identity

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    /** The branch the phone last showed, and whether it opens on Today: [LoginResponse.start] then has its lists. */
    val branchId: String? = null,
    val withToday: Boolean = false,
)

@Serializable
data class LoginResponse(
    val token: String,
    /** What the app needs to open, so signing in is one request. Null if it could not be read: ask `GET /api/start`. */
    val start: StartResponse? = null,
)

/**
 * public accept-invite call. [token] is the raw invite code from the minted link;
 * the server stores only its hash, so this is the sole chance to redeem it.
 * [username], [email] and [displayName] are required only when the invite was minted
 * without them; otherwise they are ignored.
 */
@Serializable
data class AcceptInviteRequest(
    val token: String,
    val newPassword: String,
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
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
