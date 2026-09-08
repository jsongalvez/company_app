package com.companyb.companyapp.network

import com.companyb.companyapp.dto.ErrorResponse
import kotlinx.serialization.json.Json

private val apiErrorJson =
    Json {
        ignoreUnknownKeys = true
    }

/**
 * Canonical backend `{"error": "<message>"}` decoder (#612 — moved from workforce/team).
 *
 * Pure so the decision is testable; null when the body isn't that shape (non-JSON,
 * missing field) so callers fall back to their status-code message. Decodes the shared
 * [ErrorResponse] wire shape; unknown fields are ignored so additive payload growth
 * never breaks the message.
 */
fun extractApiErrorMessage(body: String?): String? {
    if (body.isNullOrEmpty()) return null
    return runCatching { apiErrorJson.decodeFromString<ErrorResponse>(body).error }.getOrNull()
}
