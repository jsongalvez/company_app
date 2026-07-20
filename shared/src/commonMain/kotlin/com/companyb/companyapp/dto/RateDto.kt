package com.companyb.companyapp.dto

import com.companyb.companyapp.domain.SessionType
import kotlinx.serialization.Serializable

@Serializable
data class SetRateRequest(
    val id: String,
    val sessionType: SessionType,
    val rate: String,
)

@Serializable
data class RateResponse(
    val id: String,
    val branchId: String,
    val sessionType: SessionType,
    val rate: String,
    val effectiveFrom: String,
    val effectiveUntil: String,
)
