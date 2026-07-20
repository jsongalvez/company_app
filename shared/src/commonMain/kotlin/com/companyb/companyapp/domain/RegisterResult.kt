package com.companyb.companyapp.domain

sealed class RegisterResult {
    abstract val errorCode: ErrorCode?

    data object Success : RegisterResult() {
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

    data object InvalidEmail : RegisterResult() {
        override val errorCode = ErrorCode.INVALID_EMAIL
    }

    data object EmailTaken : RegisterResult() {
        override val errorCode = ErrorCode.EMAIL_TAKEN
    }
}
