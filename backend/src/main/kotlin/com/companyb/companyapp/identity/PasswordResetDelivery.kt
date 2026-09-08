package com.companyb.companyapp.identity

import com.companyb.companyapp.identity.SmtpConfig
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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal fun interface PasswordResetSender {
    fun send(
        recipient: String,
        rawCode: String,
        validityHours: Long,
    )
}

internal object PasswordResetDelivery {
    private val logger = KotlinLogging.logger {}
    private var senderExecutor: ThreadPoolExecutor? = null

    @Volatile
    private var configuredSender: PasswordResetSender? = null

    /** Selects SMTP delivery once during application startup. Null keeps the log relay active. */
    @Synchronized
    fun configure(config: SmtpConfig?) {
        senderExecutor?.shutdownNow()
        configuredSender = config?.let(::SmtpPasswordResetSender)
        senderExecutor = config?.let { createSenderExecutor() }
        if (config == null) {
            logger.warn {
                "[PASSWORD-RESET] SMTP delivery is not configured; using server-log relay. " +
                    "Set SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, and SMTP_FROM."
            }
        }
    }

    /** Stops queued delivery during application shutdown; reset tokens remain redeemable. */
    @Synchronized
    fun shutdown() {
        senderExecutor?.shutdownNow()
        senderExecutor = null
        configuredSender = null
    }

    /** Delivery happens after token transaction commits; failures never invalidate the token. */
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
        if (senderOverride != null) {
            sendSafely(sender, recipient, rawCode, validityHours)
            return
        }
        val executor = synchronized(this) { senderExecutor }
        if (executor == null) {
            logger.error { "[PASSWORD-RESET] SMTP delivery is not active; reset code remains valid for retry" }
            return
        }
        try {
            executor.execute {
                sendSafely(sender, recipient, rawCode, validityHours)
            }
        } catch (failure: RejectedExecutionException) {
            logger.error(failure) { "[PASSWORD-RESET] SMTP delivery was rejected; reset code remains valid for retry" }
        }
    }

    // #599 best-effort delivery must not invalidate the token — failures stay logged.
    @Suppress("TooGenericExceptionCaught") // #599
    private fun sendSafely(
        sender: PasswordResetSender,
        recipient: String,
        rawCode: String,
        validityHours: Long,
    ) {
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

    private fun createSenderExecutor() =
        ThreadPoolExecutor(
            DELIVERY_THREADS,
            DELIVERY_THREADS,
            DELIVERY_KEEP_ALIVE_MS,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(DELIVERY_QUEUE_CAPACITY),
            ThreadFactory { runnable ->
                Thread(runnable, DELIVERY_THREAD_NAME).apply { isDaemon = true }
            },
        )

    private const val DELIVERY_THREADS = 1
    private const val DELIVERY_KEEP_ALIVE_MS = 0L
    private const val DELIVERY_QUEUE_CAPACITY = 100
    private const val DELIVERY_THREAD_NAME = "password-reset-smtp"
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
