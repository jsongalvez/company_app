package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.incident.IncidentPacket
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal fun interface IncidentSender {
    fun send(packet: IncidentPacket)
}

/** #526 — what happened to a filed packet. Pre-send outcomes never touched the network. */
internal enum class IncidentDeliveryOutcome {
    RELAYED,
    QUEUED,
    SENT,
    FAILED_PRE_SEND,
    FAILED_AMBIGUOUS,
}

/**
 * #475 — async issue delivery for incident packets. Mirrors
 * [PasswordResetDelivery]: optional external delivery (GitHub needs-triage
 * issue when [GithubIssueConfig] is present), server-log relay otherwise, so
 * filing never needs a secret to run. Delivery failures are logged and never
 * break the request that filed.
 *
 * #526 — receipt, queueing, and delivery are distinct outcomes: the sanitized
 * packet is written to the structured log before risky async dispatch (so a
 * rejected queue, a send failure, or shutdown never loses it), and [deliver]
 * reports which outcome happened. Pre-send failures (absent/rejected
 * executor — nothing reached the network) are safe to retry; ambiguous
 * post-send failures keep the registry receipt so a retry cannot blindly file
 * a second issue.
 */
internal object IncidentDelivery {
    private val logger = KotlinLogging.logger {}

    private var senderExecutor: ThreadPoolExecutor? = null

    @Volatile
    private var configuredSender: IncidentSender? = null

    /**
     * #526 — test probe for the durable relay write. When set, [logRelay]
     * captures instead of logging, so tests prove packet survival without
     * network calls or log scraping.
     */
    @Volatile
    internal var relayProbe: ((IncidentPacket) -> Unit)? = null

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

    /**
     * Stops queued delivery during application shutdown; filed packets stay in the registry.
     * #526 — every queued packet already has its durable relay write (see [deliver]), so
     * shutdown drops at most redelivery, never diagnostics; the drop count is logged.
     */
    @Synchronized
    fun shutdown() {
        val dropped = senderExecutor?.shutdownNow()?.size ?: 0
        if (dropped > 0) {
            logger.warn {
                "[INCIDENT] dropped $dropped queued packet(s) at shutdown; " +
                    "each was preserved in the server log before queueing"
            }
        }
        senderExecutor = null
        configuredSender = null
    }

    /** Test seam (#526): installs a sender/executor pair without environment config. */
    internal fun installForTest(
        sender: IncidentSender?,
        executor: ThreadPoolExecutor?,
    ) {
        synchronized(this) {
            val previous = senderExecutor
            senderExecutor = executor
            configuredSender = sender
            if (previous !== executor) previous?.shutdownNow()
        }
    }

    /** Test seam (#526): clears installed delivery state and the relay probe. */
    internal fun resetForTest() {
        synchronized(this) {
            senderExecutor?.shutdownNow()
            senderExecutor = null
            configuredSender = null
            relayProbe = null
        }
    }

    /**
     * Files one packet for delivery without blocking the caller (#526 keeps the
     * async contract: production sends still run on the sender executor).
     * Returns the outcome so [IncidentService] can evict pre-send failures for
     * a truthful retry while ambiguous post-send failures stay deduped.
     */
    fun deliver(
        packet: IncidentPacket,
        senderOverride: IncidentSender? = null,
    ): IncidentDeliveryOutcome {
        val sender = senderOverride ?: configuredSender
        logRelay(packet)
        if (sender == null) return IncidentDeliveryOutcome.RELAYED
        return resolveDispatch(packet, sender, senderOverride != null)
    }

    private fun resolveDispatch(
        packet: IncidentPacket,
        sender: IncidentSender,
        synchronous: Boolean,
    ): IncidentDeliveryOutcome {
        if (synchronous) {
            return if (sendSafely(sender, packet)) {
                IncidentDeliveryOutcome.SENT
            } else {
                IncidentDeliveryOutcome.FAILED_AMBIGUOUS
            }
        }
        val executor = synchronized(this) { senderExecutor }
        return if (executor == null) {
            logger.error {
                "[INCIDENT] GitHub delivery is not active; " +
                    "packet preserved in the server log (trace=${packet.traceId})"
            }
            IncidentDeliveryOutcome.FAILED_PRE_SEND
        } else {
            try {
                executor.execute {
                    sendSafely(sender, packet)
                }
                logger.info { "[INCIDENT] packet queued for GitHub delivery (trace=${packet.traceId})" }
                IncidentDeliveryOutcome.QUEUED
            } catch (failure: RejectedExecutionException) {
                logger.error(failure) {
                    "[INCIDENT] GitHub delivery was rejected; " +
                        "packet preserved in the server log (trace=${packet.traceId})"
                }
                IncidentDeliveryOutcome.FAILED_PRE_SEND
            }
        }
    }

    private fun logRelay(packet: IncidentPacket) {
        relayProbe?.invoke(packet) ?: logger.info { Json.encodeToString(packet) }
    }

    private fun sendSafely(
        sender: IncidentSender,
        packet: IncidentPacket,
    ): Boolean =
        try {
            sender.send(packet)
            true
        } catch (failure: IOException) {
            logDeliveryFailure(failure, packet)
            false
        } catch (failure: InterruptedException) {
            Thread.currentThread().interrupt()
            logDeliveryFailure(failure, packet)
            false
        } catch (failure: IllegalArgumentException) {
            logDeliveryFailure(failure, packet)
            false
        }

    private fun logDeliveryFailure(
        failure: Throwable,
        packet: IncidentPacket,
    ) {
        logger.error(failure) {
            "[INCIDENT] GitHub delivery failed; " +
                "packet preserved in the server log (trace=${packet.traceId})"
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
