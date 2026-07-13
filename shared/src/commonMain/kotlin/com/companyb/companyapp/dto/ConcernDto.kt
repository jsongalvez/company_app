package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConcernResponse(
    val id: String,
    val label: String,
    val createdBy: String? = null,
    val createdAt: String? = null,
)

@Serializable
data class AddSessionConcernRequest(
    val concernId: String,
)

@Serializable
data class PromoteConcernRequest(
    val label: String,
)
