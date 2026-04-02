package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val token: String,
)

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
)

@Serializable
data class RegisterSuccessResponse(
    val userID: String,
)

@Serializable
data class RegisterErrorResponse(
    val code: String,
    val label: String,
    val detail: String,
)
