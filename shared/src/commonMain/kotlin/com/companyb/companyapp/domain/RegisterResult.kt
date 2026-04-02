package com.companyb.companyapp.domain

sealed class RegisterResult {
    abstract val errorCode: ErrorCode?

    data class Success(
        val userID: String,
    ) : RegisterResult() {
        override val errorCode: ErrorCode? = null
    }

    data object UsernameTaken : RegisterResult() {
        override val errorCode = ErrorCode.USERNAME_TAKEN
    }

    data class WeakPassword(
        val minimumLength: Int,
    ) : RegisterResult() {
        override val errorCode = ErrorCode.WEAK_PASSWORD
    }
}
