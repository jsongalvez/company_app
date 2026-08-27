package com.companyb.companyapp.auth

import com.companyb.companyapp.config.SmtpConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.mail.Authenticator
import jakarta.mail.Message
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeMessage
import java.nio.charset.StandardCharsets
import java.util.Properties

internal fun interface PasswordResetSender {
    fun send(
        recipient: String,
        rawCode: String,
        validityHours: Long,
    )
}

internal object PasswordResetDelivery {
    private val logger = KotlinLogging.logger {}

    @Volatile
    private var configuredSender: PasswordResetSender? = null

    /** Selects SMTP delivery once during application startup. Null keeps the log relay active. */
    fun configure(config: SmtpConfig?) {
        configuredSender = config?.let(::SmtpPasswordResetSender)
        if (config == null) {
            logger.warn {
                "[PASSWORD-RESET] SMTP delivery is not configured; using server-log relay. " +
                    "Set SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, and SMTP_FROM."
            }
        }
    }

    /** Delivery happens after token transaction commits; failures never invalidate the token. */
    @Suppress("TooGenericExceptionCaught")
    fun deliver(
        identifier: String,
        recipient: String,
        rawCode: String,
        validityHours: Long,
        senderOverride: PasswordResetSender? = null,
    ) {
        val sender = senderOverride ?: configuredSender
        if (sender == null) {
            logRelay(identifier, rawCode, validityHours)
            return
        }
        try {
            sender.send(recipient, rawCode, validityHours)
        } catch (failure: Exception) {
            logger.error(failure) {
                "[PASSWORD-RESET] SMTP delivery failed; reset code remains valid for retry"
            }
        }
    }

    private fun logRelay(
        identifier: String,
        rawCode: String,
        validityHours: Long,
    ) {
        logger.warn { "[PASSWORD-RESET] Reset code for '$identifier': $rawCode (valid ${validityHours}h)" }
    }
}

internal object PasswordResetEmail {
    const val SUBJECT = "CompanyApp password reset code"

    fun render(
        rawCode: String,
        validityHours: Long,
    ): String =
        "You requested a password reset for your CompanyApp account.\n\n" +
            "Use this code to set a new password:\n" +
            "$rawCode\n\n" +
            "This code is valid for $validityHours hours. If you did not request this, you can ignore this email."
}

internal class SmtpPasswordResetSender(
    private val config: SmtpConfig,
) : PasswordResetSender {
    override fun send(
        recipient: String,
        rawCode: String,
        validityHours: Long,
    ) {
        val implicitTls = config.port == SMTPS_PORT
        val properties =
            Properties().apply {
                setProperty("mail.smtp.host", config.host)
                setProperty("mail.smtp.port", config.port.toString())
                setProperty("mail.smtp.auth", true.toString())
                setProperty("mail.smtp.starttls.enable", (!implicitTls).toString())
                setProperty("mail.smtp.starttls.required", (!implicitTls).toString())
                setProperty("mail.smtp.ssl.enable", implicitTls.toString())
                setProperty("mail.smtp.ssl.checkserveridentity", true.toString())
                setProperty("mail.smtp.connectiontimeout", SMTP_TIMEOUT_MS.toString())
                setProperty("mail.smtp.timeout", SMTP_TIMEOUT_MS.toString())
                setProperty("mail.smtp.writetimeout", SMTP_TIMEOUT_MS.toString())
            }
        val session =
            Session.getInstance(
                properties,
                object : Authenticator() {
                    override fun getPasswordAuthentication() =
                        PasswordAuthentication(
                            config.username,
                            config.password,
                        )
                },
            )
        val message =
            MimeMessage(session).apply {
                setFrom(singleAddress(config.from, "SMTP_FROM"))
                setRecipient(Message.RecipientType.TO, singleAddress(recipient, "recipient"))
                subject = PasswordResetEmail.SUBJECT
                setText(PasswordResetEmail.render(rawCode, validityHours), StandardCharsets.UTF_8.name())
            }
        Transport.send(message)
    }

    private fun singleAddress(
        value: String,
        field: String,
    ): InternetAddress {
        val address =
            InternetAddress
                .parse(value, true)
                .singleOrNull()
        require(address != null) { "$field must contain exactly one address" }
        require(!address.isGroup) { "$field must contain one mailbox, not a group" }
        address.validate()
        return address
    }

    private companion object {
        const val SMTPS_PORT = 465
        const val SMTP_TIMEOUT_MS = 10_000
    }
}
