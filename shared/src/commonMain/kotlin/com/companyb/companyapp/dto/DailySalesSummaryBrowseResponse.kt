package com.companyb.companyapp.dto

import kotlinx.serialization.Serializable

@Serializable
data class DailySalesSummaryBrowseResponse(
    val entries: List<DailySalesSummaryResponse>,
    val nextCursor: String? = null,
)
