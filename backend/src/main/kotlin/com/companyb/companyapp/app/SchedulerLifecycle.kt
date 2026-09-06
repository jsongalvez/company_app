package com.companyb.companyapp.app

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

/**
 * #551 — small executor owner for daily sweeps. Concrete jobs (tasks + initial-delay
 * functions) are supplied at application composition ([com.companyb.companyapp.main] wires
 * the production NextAppointment/Relief jobs); this owner keeps only executor lifecycle,
 * 24h periods (#503) and failure isolation. Delays stay in milliseconds.
 */
data class ScheduledJob(
    val name: String,
    val initialDelayMs: (ZonedDateTime) -> Long,
    val task: () -> Unit,
)

class SchedulerLifecycle(
    private val executorFactory: () -> ScheduledExecutorService = ::createExecutor,
    private val now: () -> ZonedDateTime,
    private val jobs: List<ScheduledJob>,
) {
    private var executor: ScheduledExecutorService? = null

    @Suppress("TooGenericExceptionCaught")
    private fun runTask(
        name: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: RuntimeException) {
            logger.error(e) { "[SCHEDULER] $name task failed" }
        }
    }

    @Synchronized
    @Suppress("TooGenericExceptionCaught")
    fun start() {
        if (executor?.isShutdown == false) return

        val candidate = executorFactory()
        try {
            for (job in jobs) {
                candidate.scheduleAtFixedRate(
                    { runTask(job.name, job.task) },
                    job.initialDelayMs(now()),
                    TimeUnit.HOURS.toMillis(PERIOD_HOURS),
                    TimeUnit.MILLISECONDS,
                )
            }
            executor = candidate
        } catch (e: Exception) {
            candidate.shutdownNow()
            throw e
        }
    }

    @Synchronized
    fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    private companion object {
        private val logger = KotlinLogging.logger {}
        private const val PERIOD_HOURS = 24L

        private fun createExecutor(): ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor(
                ThreadFactory { runnable ->
                    Thread(runnable, "notification-scheduler").apply { isDaemon = true }
                },
            )
    }
}
