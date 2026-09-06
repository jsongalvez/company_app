package com.companyb.companyapp.identity

import com.companyb.companyapp.identity.SmtpConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PasswordResetDeliveryTest {
    @Test
    fun `reset message is plain text with code validity and request framing`() {
        val message = PasswordResetEmail.render("reset-code", 24)

        assertTrue(message.contains("You requested a password reset"))
        assertTrue(message.contains("reset-code"))
        assertTrue(message.contains("valid for 24 hours"))
        assertTrue(message.contains("did not request this"))
    }

    @Test
    fun `missing SMTP falls back without throwing`() {
        PasswordResetDelivery.configure(null)

        PasswordResetDelivery.deliver(
            identifier = "reset-user",
            recipient = "reset-user@example.com",
            rawCode = "reset-code",
            validityHours = 24,
        )
    }

    @Test
    fun `delivery passes recipient and code to configured sender`() {
        var delivered: Triple<String, String, Long>? = null
        val sender =
            PasswordResetSender { recipient, rawCode, validityHours ->
                delivered = Triple(recipient, rawCode, validityHours)
            }

        PasswordResetDelivery.deliver(
            identifier = "reset-user",
            recipient = "reset-user@example.com",
            rawCode = "reset-code",
            validityHours = 24,
            senderOverride = sender,
        )

        assertEquals(Triple("reset-user@example.com", "reset-code", 24L), delivered)
    }

    @Test
    fun `SMTP sender rejects multiple recipients`() {
        val sender =
            SmtpPasswordResetSender(
                config =
                    SmtpConfig(
                        host = "smtp.example.com",
                        port = 587,
                        username = "mailer@example.com",
                        password = "provider password",
                        from = "CompanyApp <mailer@example.com>",
                    ),
            )

        assertFailsWith<IllegalArgumentException> {
            sender.send("first@example.com,second@example.com", "reset-code", 24)
        }
    }
}
