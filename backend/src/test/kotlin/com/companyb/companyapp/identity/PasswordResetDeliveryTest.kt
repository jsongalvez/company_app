package com.companyb.companyapp.identity

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.companyb.companyapp.identity.SmtpConfig
import org.slf4j.LoggerFactory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasswordResetDeliveryTest {
    @AfterTest
    fun resetDelivery() {
        PasswordResetDelivery.configure(null)
    }

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
    fun `unconfigured delivery never logs the code or the identifier`() {
        PasswordResetDelivery.configure(null)

        val events =
            capturePasswordResetLogs {
                PasswordResetDelivery.deliver(
                    identifier = RESET_IDENTIFIER,
                    recipient = "reset-target@example.com",
                    rawCode = RESET_CODE,
                    validityHours = 24,
                )
            }

        assertTrue(events.isNotEmpty(), "fail-closed leg must leave an operator-visible retry note")
        assertTrue(
            events.none { it.formattedMessage.contains(RESET_CODE) },
            "live reset code must never reach the logs",
        )
        assertTrue(
            events.none { it.formattedMessage.contains(RESET_IDENTIFIER) },
            "account identifier must not pair with the reset path in logs",
        )
    }

    @Test
    fun `log relay stays unreachable without the dev flag and emits only with it`() {
        PasswordResetDelivery.configure(null, allowLogRelay = false)
        val closedEvents =
            capturePasswordResetLogs {
                PasswordResetDelivery.deliver(
                    identifier = RESET_IDENTIFIER,
                    recipient = "reset-target@example.com",
                    rawCode = RESET_CODE,
                    validityHours = 24,
                )
            }
        assertTrue(
            closedEvents.none { it.formattedMessage.contains(RESET_CODE) },
            "default delivery must not emit the live code",
        )

        PasswordResetDelivery.configure(null, allowLogRelay = true)
        val relayEvents =
            capturePasswordResetLogs {
                PasswordResetDelivery.deliver(
                    identifier = RESET_IDENTIFIER,
                    recipient = "reset-target@example.com",
                    rawCode = RESET_CODE,
                    validityHours = 24,
                )
            }
        assertTrue(
            relayEvents.any { it.formattedMessage.contains(RESET_CODE) },
            "explicit dev opt-in must keep the console relay reachable",
        )
    }

    @Test
    fun `dev relay flag needs an explicit true`() {
        assertTrue(PasswordResetDelivery.parseDevRelay(mapOf(PasswordResetDelivery.DEV_RELAY_ENV to "true")))
        assertTrue(PasswordResetDelivery.parseDevRelay(mapOf(PasswordResetDelivery.DEV_RELAY_ENV to " TRUE ")))
        assertFalse(PasswordResetDelivery.parseDevRelay(mapOf(PasswordResetDelivery.DEV_RELAY_ENV to "1")))
        assertFalse(PasswordResetDelivery.parseDevRelay(mapOf(PasswordResetDelivery.DEV_RELAY_ENV to "yes")))
        assertFalse(PasswordResetDelivery.parseDevRelay(mapOf(PasswordResetDelivery.DEV_RELAY_ENV to "")))
        assertFalse(PasswordResetDelivery.parseDevRelay(mapOf(PasswordResetDelivery.DEV_RELAY_ENV to null)))
        assertFalse(PasswordResetDelivery.parseDevRelay(emptyMap()))
    }

    private fun capturePasswordResetLogs(action: () -> Unit): List<ILoggingEvent> {
        val logger = LoggerFactory.getLogger(PasswordResetDelivery::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        try {
            action()
        } finally {
            logger.detachAppender(appender)
        }
        return appender.list.toList()
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

    private companion object {
        const val RESET_IDENTIFIER = "reset-target-user"
        const val RESET_CODE = "live-reset-code-abc123"
    }
}
