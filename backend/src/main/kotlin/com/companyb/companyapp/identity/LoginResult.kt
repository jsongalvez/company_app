package com.companyb.companyapp.identity

/** Backend-only login outcome, beside the auth command that produces it. */
sealed class LoginResult {
    data class Success(
        val token: String,
        val userId: java.util.UUID,
    ) : LoginResult()

    data object RateLimited : LoginResult()

    data object InvalidCredentials : LoginResult()
}
