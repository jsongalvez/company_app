package com.companyb.companyapp.contracts.batch

import kotlinx.serialization.Serializable

/** Reads to run together: each path is an `/api/...` GET with its query string. */
@Serializable
data class BatchRequest(
    val paths: List<String>,
)

/** One read's answer exactly as it would have come alone: its status, body text and trace id. */
@Serializable
data class BatchItem(
    val status: Int,
    val body: String,
    val traceId: String? = null,
)

@Serializable
data class BatchResponse(
    val results: List<BatchItem>,
)
