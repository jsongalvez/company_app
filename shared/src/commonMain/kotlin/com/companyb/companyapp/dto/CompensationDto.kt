package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateCompensationRequest(
    val id: String,
    val workBranchDayId: String,
    val payingBranchDayId: String,
    val userId: String,
    val amount: String,
    val note: String? = null,
)

@Serializable
data class UpdateCompensationRequest(
    val amount: String,
    val note: String? = null,
)

@Serializable
data class CompensationResponse(
    val id: String,
    val workBranchDayId: String,
    val payingBranchDayId: String,
    val userId: String,
    val amount: String,
    val assignedBy: String,
    val assignedAt: String,
    val note: String?,
)
