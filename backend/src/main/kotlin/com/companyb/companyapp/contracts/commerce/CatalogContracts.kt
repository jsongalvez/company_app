package com.companyb.companyapp.contracts.commerce

import kotlinx.serialization.Serializable

@Serializable
data class CreateProductRequest(
    val id: String,
    val name: String,
    val productCategoryId: String,
    val unitPrice: String,
    val commissionAmount: String,
)

@Serializable
data class UpdateProductRequest(
    val name: String? = null,
    val productCategoryId: String? = null,
    val unitPrice: String? = null,
    val commissionAmount: String? = null,
    val isActive: Boolean? = null,
)

@Serializable
data class ProductResponse(
    val id: String,
    val name: String,
    val productCategoryId: String,
    val isActive: Boolean,
    val unitPrice: String,
    val commissionAmount: String,
)

@Serializable
data class CreateProductCategoryRequest(
    val id: String,
    val name: String,
)

@Serializable
data class ProductCategoryResponse(
    val id: String,
    val name: String,
)
