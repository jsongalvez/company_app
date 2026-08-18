package com.companyb.companyapp.service

import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

@Suppress("TooGenericExceptionCaught")
class SchedulerLifecycle(
    private val executorFactory: () -> ScheduledExecutorService = ::createExecutor,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(MANILA_ZONE) },
    private val task: () -> Unit = { NextAppointmentScheduler.run(Clock.system(MANILA_ZONE)) },
) {
    private var executor: ScheduledExecutorService? = null

    @Synchronized
    fun start() {
        if (executor?.isShutdown == false) return

        val candidate = executorFactory()
        try {
            candidate.scheduleAtFixedRate(
                @Suppress("TooGenericExceptionCaught")
                {
                    try {
                        task()
                    } catch (e: Exception) {
                        logger.error(e) { "[SCHEDULER] Notification task failed" }
                    }
                },
                NextAppointmentScheduler.nextRunDelayMs(now()),
                PERIOD_HOURS,
                TimeUnit.HOURS,
            )
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
        private val MANILA_ZONE: ZoneId = ZoneId.of("Asia/Manila")
        private const val PERIOD_HOURS = 24L

        private fun createExecutor(): ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor(
                ThreadFactory { runnable ->
                    Thread(runnable, "notification-scheduler").apply { isDaemon = true }
                },
            )
    }
}
