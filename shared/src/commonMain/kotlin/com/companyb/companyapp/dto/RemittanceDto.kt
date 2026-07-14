package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateRemittanceDraftRequest(
    val id: String,
    val type: String,
    val branchId: String,
    val method: String,
    val dateRangeStart: String,
    val dateRangeEnd: String,
)

@Serializable
data class RemittanceResponse(
    val id: String,
    val type: String,
    val status: String,
    val branchId: String,
    val method: String,
    val submittedDate: String,
    val submittedBy: String,
    val dateRangeStart: String,
    val dateRangeEnd: String,
    val createdAt: String,
    val version: Int,
)

@Serializable
data class CreateRemittanceLineRequest(
    val id: String,
    val type: String,
    val sessionId: String? = null,
    val productSaleId: String? = null,
    val amount: String,
)

@Serializable
data class RemittanceLineResponse(
    val id: String,
    val remittanceId: String,
    val type: String,
    val sessionId: String?,
    val productSaleId: String?,
    val createdBy: String,
    val createdAt: String,
    val deletedBy: String?,
    val deletedAt: String?,
    val amount: String,
)

@Serializable
data class AddDayBreakdownRequest(
    val id: String,
    val branchDayId: String,
)

@Serializable
data class RemittanceDayBreakdownResponse(
    val id: String,
    val remittanceId: String,
    val branchDayId: String,
)

@Serializable
data class RemittanceDetailResponse(
    val id: String,
    val type: String,
    val status: String,
    val branchId: String,
    val method: String,
    val submittedDate: String,
    val submittedBy: String,
    val dateRangeStart: String,
    val dateRangeEnd: String,
    val createdAt: String,
    val version: Int,
    val lines: List<RemittanceLineResponse>,
    val totalAmount: String,
    val dayBreakdowns: List<RemittanceDayBreakdownResponse>,
)
