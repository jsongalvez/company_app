package com.companyb.companyapp.observability

import java.net.URI

/**
 * optional error-tracker target (Sentry, or GlitchTip: same wire format). Absent until the
 * operator sets [DSN_ENV]; unset keeps the server exactly as it was.
 */
data class ErrorTrackerConfig(
    val envelopeUrl: String,
    val publicKey: String,
    val environment: String,
) {
    companion object {
        const val DSN_ENV = "SENTRY_DSN"
        const val ENVIRONMENT_ENV = "SENTRY_ENVIRONMENT"
        private const val DEFAULT_ENVIRONMENT = "production"

        /** Returns null for an absent or malformed DSN (`https://KEY@host/PROJECT`). */
        fun fromEnvironment(environment: Map<String, String?>): ErrorTrackerConfig? {
            val raw = environment[DSN_ENV]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            val key = uri.userInfo?.substringBefore(':')?.takeIf { it.isNotEmpty() } ?: return null
            val host = uri.host ?: return null
            val project = uri.path?.trim('/')?.takeIf { it.isNotEmpty() && '/' !in it } ?: return null
            if (uri.scheme != "https" && uri.scheme != "http") return null
            val port = if (uri.port > 0) ":${uri.port}" else ""
            return ErrorTrackerConfig(
                envelopeUrl = "${uri.scheme}://$host$port/api/$project/envelope/",
                publicKey = key,
                environment = environment[ENVIRONMENT_ENV]?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_ENVIRONMENT,
            )
        }
    }
}
