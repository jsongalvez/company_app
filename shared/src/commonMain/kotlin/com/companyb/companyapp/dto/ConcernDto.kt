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
    val reason: String? = null,
)

@Serializable
data class RemoveSessionConcernRequest(
    val reason: String? = null,
)

@Serializable
data class PromoteConcernRequest(
    val id: String,
    val label: String,
    val reason: String? = null,
)
