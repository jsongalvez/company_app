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
