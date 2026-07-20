package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

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
