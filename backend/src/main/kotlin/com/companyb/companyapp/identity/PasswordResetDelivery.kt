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
    /** Explicit dev-only opt-in for the server-log relay; never enable where logs ship. */
    const val DEV_RELAY_ENV = "PASSWORD_RESET_DEV_RELAY"

    private val logger = KotlinLogging.logger {}
    private var senderExecutor: ThreadPoolExecutor? = null

    @Volatile
    private var configuredSender: PasswordResetSender? = null

    @Volatile
    private var allowLogRelay = false

    /**
     * #897 — selects SMTP delivery once during application startup. Null keeps delivery
     * fail-closed: reset codes stay valid for retry but neither the code nor the account
     * identifier reaches the logs. The server-log relay is dev-only and needs the explicit
     * [allowLogRelay] opt-in (`PASSWORD_RESET_DEV_RELAY=true`); SMTP takes precedence when set.
     */
    @Synchronized
    fun configure(
        config: SmtpConfig?,
        allowLogRelay: Boolean = false,
    ) {
        senderExecutor?.shutdownNow()
        configuredSender = config?.let(::SmtpPasswordResetSender)
        senderExecutor = config?.let { createSenderExecutor() }
        this.allowLogRelay = allowLogRelay
        if (config == null) {
            if (allowLogRelay) {
                logger.warn {
                    "[PASSWORD-RESET] SMTP delivery is not configured; using dev-only server-log relay " +
                        "($DEV_RELAY_ENV=true). Never enable where logs are shipped or retained."
                }
            } else {
                logger.warn {
                    "[PASSWORD-RESET] SMTP delivery is not configured; reset codes stay valid for retry. " +
                        "Set SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, and SMTP_FROM."
                }
            }
        }
    }

    /** Returns true only for an explicit opt-in value; absent or anything else stays fail-closed. */
    internal fun parseDevRelay(environment: Map<String, String?>): Boolean =
        environment[DEV_RELAY_ENV]?.trim().equals("true", ignoreCase = true)

    /** Stops queued delivery during application shutdown; reset tokens remain redeemable. */
    @Synchronized
    fun shutdown() {
        senderExecutor?.shutdownNow()
        senderExecutor = null
        configuredSender = null
        allowLogRelay = false
    }

    /** Delivery happens after token transaction commits; failures never invalidate the token. */
    fun deliver(
        identifier: String,
        recipient: String,
        rawCode: String,
        validityHours: Long,
        senderOverride: PasswordResetSender? = null,
    ) {
        // #601 max-2: fail-closed/dev-relay, override-direct, and executor-queued legs share one if-else exit.
        val sender = senderOverride ?: configuredSender
        if (sender == null) {
            if (allowLogRelay) {
                logRelay(identifier, rawCode, validityHours)
            } else {
                logFailClosed()
            }
        } else if (senderOverride != null) {
            sendSafely(sender, recipient, rawCode, validityHours)
        } else {
            val executor = synchronized(this) { senderExecutor }
            if (executor == null) {
                logger.error { "[PASSWORD-RESET] SMTP delivery is not active; reset code remains valid for retry" }
            } else {
                try {
                    executor.execute {
                        sendSafely(sender, recipient, rawCode, validityHours)
                    }
                } catch (failure: RejectedExecutionException) {
                    logger.error(failure) {
                        "[PASSWORD-RESET] SMTP delivery was rejected; reset code remains valid for retry"
                    }
                }
            }
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

    /**
     * #897 — fail-closed leg: the minted code stays redeemable via a later SMTP retry, but
     * neither the code nor the account identifier reaches the logs (log lines cannot be
     * consumed or revoked, unlike the token row).
     */
    private fun logFailClosed() {
        logger.error { "[PASSWORD-RESET] SMTP delivery is not configured; reset code remains valid for retry" }
    }

    // #897 — dev-only console relay behind the explicit PASSWORD_RESET_DEV_RELAY opt-in: the
    // only log line that may carry a live code, never enabled where logs are shipped.
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
