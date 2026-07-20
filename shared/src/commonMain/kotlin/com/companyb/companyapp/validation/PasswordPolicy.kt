package com.companyb.companyapp.validation

object PasswordPolicy {
    const val MIN_LENGTH = 8

    fun isValid(password: String): Boolean = password.length >= MIN_LENGTH
}
