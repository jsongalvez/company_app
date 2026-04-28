package com.companyb.companyapp.domain

sealed class LoginResult {
    data class Success(
        val token: String,
    ) : LoginResult()

    data object RateLimited : LoginResult()

    data object InvalidCredentials : LoginResult()
}
