package com.companyb.companyapp.identity

/** Backend-only login outcome, beside the auth command that produces it (map #533 #537). */
sealed class LoginResult {
    data class Success(
        val token: String,
    ) : LoginResult()

    data object RateLimited : LoginResult()

    data object InvalidCredentials : LoginResult()
}
