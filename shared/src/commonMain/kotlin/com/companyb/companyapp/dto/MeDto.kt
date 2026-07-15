package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class MeResponse(
    val id: String,
    val username: String,
    val status: String,
    val createdAt: String? = null,
)

@Serializable
data class UserCapabilityResponse(
    val capabilityCode: String,
    val contextType: String,
    val contextId: String,
    val sourceType: String,
)
