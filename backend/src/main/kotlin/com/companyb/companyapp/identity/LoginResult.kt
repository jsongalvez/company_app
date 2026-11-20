package com.companyb.companyapp.identity

/** Backend-only login outcome, beside the auth command that produces it. */
sealed class LoginResult {
    data class Success(
        val token: String,
        val userId: java.util.UUID,
        /** Kept by the phone to sign itself in again after days without signal (`/auth/refresh`). */
        val replayToken: String,
    ) : LoginResult()

    /** Too many tries from one address in a minute (RateLimiter). */
    data object RateLimited : LoginResult()

    /** Too many wrong passwords for one username in [LoginFailures.WINDOW]. */
    data object AccountLocked : LoginResult()

    data object InvalidCredentials : LoginResult()
}
