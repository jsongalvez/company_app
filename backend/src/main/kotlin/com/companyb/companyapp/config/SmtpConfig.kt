package com.companyb.companyapp.config

import jakarta.mail.internet.InternetAddress

data class SmtpConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val from: String,
) {
    companion object {
        const val HOST_ENV = "SMTP_HOST"
        const val PORT_ENV = "SMTP_PORT"
        const val USERNAME_ENV = "SMTP_USERNAME"
        const val PASSWORD_ENV = "SMTP_PASSWORD"
        const val FROM_ENV = "SMTP_FROM"

        private const val MIN_PORT = 1
        private const val MAX_PORT = 65_535

        /** Returns null for absent, incomplete, or invalid SMTP configuration. */
        @Suppress("ReturnCount")
        internal fun fromEnvironment(environment: Map<String, String?>): SmtpConfig? {
            val host = environment[HOST_ENV]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val port =
                environment[PORT_ENV]
                    ?.trim()
                    ?.toIntOrNull()
                    ?.takeIf { it in MIN_PORT..MAX_PORT }
                    ?: return null
            val username = environment[USERNAME_ENV]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val password = environment[PASSWORD_ENV]?.takeIf { it.isNotBlank() } ?: return null
            val from = environment[FROM_ENV]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (!isSingleMailbox(from)) return null
            return SmtpConfig(host, port, username, password, from)
        }

        private fun isSingleMailbox(value: String): Boolean =
            runCatching {
                val address = InternetAddress.parse(value, true).singleOrNull() ?: return@runCatching false
                if (address.isGroup) return@runCatching false
                address.validate()
                true
            }.getOrDefault(false)
    }
}
