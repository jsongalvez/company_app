package com.companyb.companyapp.contracts.commission

import kotlinx.serialization.Serializable

@Serializable
data class CreateCommissionInclusionRequest(
    val id: String,
    val productSaleId: String,
    val userId: String,
    val isIncluded: Boolean,
    val reason: String? = null,
)

@Serializable
data class CommissionInclusionResponse(
    val id: String,
    val productSaleId: String,
    val userId: String,
    val isIncluded: Boolean,
    val reason: String?,
    val assignedBy: String,
    val assignedAt: String,
)

@Serializable
data class CommissionSplitResponse(
    val id: String,
    val branchDayId: String,
    val userId: String,
    val amount: String,
)
