package com.companyb.companyapp.contracts.telemetry

import kotlinx.serialization.Serializable

/**
 * One upload of what a page load did. [sessionId] is made by the page when it loads; [seq] numbers the
 * events within it, so a resent batch is stored once. [at] is the phone's clock in epoch milliseconds.
 * [kind] is boot, nav, tap, input, api or error; [data] is that event's fields as a JSON object.
 */
@Serializable
data class TelemetryBatchRequest(
    val sessionId: String,
    val events: List<TelemetryEventInput>,
)

@Serializable
data class TelemetryEventInput(
    val seq: Int,
    val at: Long,
    val kind: String,
    val data: String,
)
