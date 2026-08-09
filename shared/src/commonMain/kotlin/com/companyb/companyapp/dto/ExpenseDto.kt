package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateExpenseRequest(
    val id: String,
    val branchDayId: String,
    val amount: String,
    val category: String,
    val notes: String? = null,
    val reason: String? = null,
)

@Serializable
data class DeleteExpenseRequest(
    val reason: String,
)

@Serializable
data class UpdateExpenseRequest(
    val amount: String,
    val category: String,
    val notes: String? = null,
    val expectedVersion: Int,
    val reason: String? = null,
)

@Serializable
data class ExpenseResponse(
    val id: String,
    val branchDayId: String,
    val amount: String,
    val category: String,
    val notes: String?,
    val createdBy: String,
    val createdAt: String,
    val deletedBy: String?,
    val deletedAt: String?,
    val version: Int,
)
