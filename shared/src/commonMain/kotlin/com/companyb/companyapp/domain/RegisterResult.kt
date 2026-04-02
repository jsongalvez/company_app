package com.companyb.companyapp.domain

sealed class RegisterResult {
    data class Success(
        val userID: String,
    ) : RegisterResult()

    data object UsernameTaken : RegisterResult()

    data class WeakPassword(
        val minimumLength: Int,
    ) : RegisterResult()
}
