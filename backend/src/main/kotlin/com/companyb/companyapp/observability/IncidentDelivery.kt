package com.companyb.companyapp.observability

import com.companyb.companyapp.config.GithubIssueConfig
import com.companyb.companyapp.dto.IncidentPacket
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal fun interface IncidentSender {
    fun send(packet: IncidentPacket)
}

/**
 * #475 — async issue delivery for incident packets. Mirrors
 * [PasswordResetDelivery]: optional external delivery (GitHub needs-triage
 * issue when [GithubIssueConfig] is present), server-log relay otherwise, so
 * filing never needs a secret to run. Delivery failures are logged and never
 * break the request that filed.
 */
internal object IncidentDelivery {
    private val logger = KotlinLogging.logger {}

    private var senderExecutor: ThreadPoolExecutor? = null

    @Volatile
    private var configuredSender: IncidentSender? = null

    /** Selects GitHub delivery once during application startup. Null keeps the log relay active. */
    @Synchronized
    fun configure(config: GithubIssueConfig?) {
        senderExecutor?.shutdownNow()
        configuredSender = config?.let(::GithubIssueSender)
        senderExecutor = config?.let { createSenderExecutor() }
        if (config == null) {
            logger.warn {
                "[INCIDENT] GitHub issue delivery is not configured; using server-log relay. " +
                    "Set GITHUB_TOKEN and GITHUB_REPOSITORY."
            }
        }
    }

    /** Stops queued delivery during application shutdown; filed packets stay in the registry. */
    @Synchronized
    fun shutdown() {
        senderExecutor?.shutdownNow()
        senderExecutor = null
        configuredSender = null
    }

    fun deliver(
        packet: IncidentPacket,
        senderOverride: IncidentSender? = null,
    ) {
        val sender = senderOverride ?: configuredSender
        if (sender == null) {
            logRelay(packet)
            return
        }
        if (senderOverride != null) {
            sendSafely(sender, packet)
            return
        }
        val executor = synchronized(this) { senderExecutor }
        if (executor == null) {
            logger.error { "[INCIDENT] GitHub delivery is not active; packet remains in the server log relay" }
            return
        }
        try {
            executor.execute {
                sendSafely(sender, packet)
            }
        } catch (failure: RejectedExecutionException) {
            logger.error(failure) { "[INCIDENT] GitHub delivery was rejected; packet remains in the server log relay" }
        }
    }

    private fun logRelay(packet: IncidentPacket) {
        logger.info { Json.encodeToString(packet) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sendSafely(
        sender: IncidentSender,
        packet: IncidentPacket,
    ) {
        try {
            sender.send(packet)
        } catch (failure: Exception) {
            logger.error(failure) {
                "[INCIDENT] GitHub delivery failed; packet remains in the server log relay"
            }
        }
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
    private const val DELIVERY_THREAD_NAME = "incident-github"
}
