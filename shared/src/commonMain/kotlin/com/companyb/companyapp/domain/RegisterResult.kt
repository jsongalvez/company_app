package com.companyb.companyapp.domain

sealed class RegisterResult {
    data object Success : RegisterResult()

    data object UsernameTaken : RegisterResult()
}
