package com.companyb.companyapp.observability

import java.net.URI

/**
 * optional phone alert for server errors, through an ntfy server (ntfy.sh or self-hosted). Absent until
 * both values are set. The topic works like a password for subscribing and publishing, so it is never logged.
 */
data class NtfyConfig(
    val url: String,
    val topic: String,
) {
    companion object {
        const val URL_ENV = "NTFY_URL"
        const val TOPIC_ENV = "NTFY_TOPIC"

        private val TOPIC_PATTERN = Regex("^[A-Za-z0-9_-]{1,64}$")

        fun fromLookup(get: (String) -> String?): NtfyConfig? =
            fromEnvironment(
                mapOf(
                    URL_ENV to get(URL_ENV),
                    TOPIC_ENV to get(TOPIC_ENV),
                ),
            )

        /** Returns null for absent or malformed configuration. */
        fun fromEnvironment(environment: Map<String, String?>): NtfyConfig? {
            val raw = environment[URL_ENV]?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
            val topic = environment[TOPIC_ENV]?.trim()?.takeIf { TOPIC_PATTERN.matches(it) } ?: return null
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            if (uri.host == null || (uri.scheme != "https" && uri.scheme != "http")) return null
            return NtfyConfig(raw, topic)
        }
    }
}
