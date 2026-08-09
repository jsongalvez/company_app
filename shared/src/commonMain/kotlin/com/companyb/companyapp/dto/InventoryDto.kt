package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class AddInventoryCardRequest(
    val productId: String,
)

@Serializable
data class RestockRequest(
    val id: String,
    val quantity: Int,
    val branchDayId: String,
    val editReason: String? = null,
)

@Serializable
data class BranchInventoryResponse(
    val id: String,
    val branchId: String,
    val productId: String,
    val productName: String,
    val currentStock: Int,
    val version: Int,
    val unitPrice: String,
    val commissionAmount: String,
)

@Serializable
data class InventoryMovementRequest(
    val movementId: String,
    val reason: String,
    val quantityChange: Int,
    val notes: String? = null,
    val branchDayId: String,
    val expectedVersion: Int,
    val editReason: String? = null,
)

@Serializable
data class InventoryMovementResponse(
    val id: String,
    val productId: String,
    val branchId: String,
    val branchDayId: String,
    val reason: String,
    val quantityChange: Int,
    val movedBy: String,
    val movedAt: String,
    val notes: String?,
)
