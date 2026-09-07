package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.incident.PoolSnapshot
import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.maskUUID

/** #499 — field helpers extracted from IncidentService to stay under TooManyFunctions. */
internal object IncidentFields {
    fun validated(
        value: String,
        maxLength: Int,
        name: String,
    ): String {
        if (value.isBlank()) throw ValidationException("$name is required")
        if (value.length > maxLength) throw ValidationException("$name is too long (max $maxLength)")
        return value
    }

    fun parseMethod(endpoint: String): String {
        val candidate = endpoint.substringBefore(" ").uppercase()
        return candidate.takeIf { it in KNOWN_METHODS } ?: UNKNOWN
    }

    fun extractRoute(endpoint: String): String = endpoint.substringAfter(" ", endpoint)

    fun normalizedRoute(raw: String): String = RequestMetrics.normalizeRoute(raw.substringBefore("?")).maskUUID()

    fun maskReporter(raw: String?): String =
        raw?.let {
            runCatching {
                java.util.UUID
                    .fromString(it)
                    .toString()
            }.getOrNull()?.maskUUID()
        }
            ?: SERVER_REPORTER

    fun currentPool(): PoolSnapshot {
        val stats = DatabaseConfig.poolStats()
        return PoolSnapshot(
            active = stats.active,
            idle = stats.idle,
            awaiting = stats.awaiting,
            total = stats.total,
        )
    }

    private val KNOWN_METHODS = setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
    private const val UNKNOWN = "unknown"
    private const val SERVER_REPORTER = "server"
}
