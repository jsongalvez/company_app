package com.companyb.companyapp.domain

enum class ErrorCode(
    val label: String,
    val detail: String,
) {
    USERNAME_TAKEN(
        label = "Username Taken",
        detail = "This username has been taken.",
    ),
    WEAK_PASSWORD(
        label = "Weak Password",
        detail = "Password does not meet minimum requirements.",
    ),
    INVALID_EMAIL(
        label = "Invalid Email",
        detail = "This email is not a valid email.",
    ),
    EMAIL_TAKEN(
        label = "Email Taken",
        detail = "This email is taken.",
    ),
}
