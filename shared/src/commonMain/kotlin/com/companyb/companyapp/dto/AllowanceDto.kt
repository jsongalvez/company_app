package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateAllowanceRequest(
    val id: String,
    val branchDayId: String,
    val userId: String,
    val amount: String,
    val reason: String? = null,
)

@Serializable
data class AllowanceResponse(
    val id: String,
    val branchDayId: String,
    val userId: String,
    val amount: String,
    val assignedBy: String,
    val assignedAt: String,
)
