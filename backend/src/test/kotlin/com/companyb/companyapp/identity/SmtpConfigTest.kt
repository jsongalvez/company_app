package com.companyb.companyapp.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SmtpConfigTest {
    @Test
    fun `valid environment produces SMTP config`() {
        val config = SmtpConfig.fromEnvironment(environment())

        assertEquals(
            SmtpConfig(
                host = "smtp.example.com",
                port = 587,
                username = "mailer@example.com",
                password = "provider password",
                from = "CompanyApp <mailer@example.com>",
            ),
            config,
        )
    }

    @Test
    fun `missing or blank values disable SMTP`() {
        assertNull(SmtpConfig.fromEnvironment(environment(SmtpConfig.PASSWORD_ENV to "  ")))
        assertNull(SmtpConfig.fromEnvironment(environment(SmtpConfig.HOST_ENV to null)))
    }

    @Test
    fun `invalid port disables SMTP`() {
        assertNull(SmtpConfig.fromEnvironment(environment(SmtpConfig.PORT_ENV to "65536")))
        assertNull(SmtpConfig.fromEnvironment(environment(SmtpConfig.PORT_ENV to "not-a-port")))
    }

    @Test
    fun `invalid or grouped sender address disables SMTP`() {
        assertNull(SmtpConfig.fromEnvironment(environment(SmtpConfig.FROM_ENV to "not-an-email")))
        assertNull(
            SmtpConfig.fromEnvironment(
                environment(SmtpConfig.FROM_ENV to "first@example.com,second@example.com"),
            ),
        )
    }

    @Test
    fun `host username and from are trimmed`() {
        val config =
            SmtpConfig.fromEnvironment(
                environment(
                    SmtpConfig.HOST_ENV to " smtp.example.com ",
                    SmtpConfig.USERNAME_ENV to " mailer@example.com ",
                    SmtpConfig.FROM_ENV to " CompanyApp <mailer@example.com> ",
                ),
            )

        assertEquals("smtp.example.com", config?.host)
        assertEquals("mailer@example.com", config?.username)
        assertEquals("CompanyApp <mailer@example.com>", config?.from)
    }

    private fun environment(vararg overrides: Pair<String, String?>): Map<String, String?> {
        val values =
            mutableMapOf<String, String?>(
                SmtpConfig.HOST_ENV to "smtp.example.com",
                SmtpConfig.PORT_ENV to "587",
                SmtpConfig.USERNAME_ENV to "mailer@example.com",
                SmtpConfig.PASSWORD_ENV to "provider password",
                SmtpConfig.FROM_ENV to "CompanyApp <mailer@example.com>",
            )
        overrides.forEach { (key, value) -> values[key] = value }
        return values
    }
}
