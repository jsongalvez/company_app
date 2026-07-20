package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateProductSaleRequest(
    val id: String,
    val branchDayId: String,
    val sessionId: String? = null,
    val clientId: String? = null,
    val isWalkIn: Boolean,
    val productId: String,
    val quantity: Int,
    val expectedVersion: Int,
)

@Serializable
data class ProductSaleResponse(
    val id: String,
    val branchDayId: String,
    val sessionId: String?,
    val clientId: String?,
    val isWalkIn: Boolean,
    val productId: String,
    val productName: String,
    val handledBy: String,
    val quantity: Int,
    val unitPriceAtTime: String,
    val totalAmountAtTime: String,
    val commissionAmountAtTime: String,
    val soldAt: String,
)
