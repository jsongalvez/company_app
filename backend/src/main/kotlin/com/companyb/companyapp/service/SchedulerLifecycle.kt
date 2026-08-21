package com.companyb.companyapp.service

import com.companyb.companyapp.service.branchday.BranchDayService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.Clock
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

class SchedulerLifecycle(
    private val executorFactory: () -> ScheduledExecutorService = ::createExecutor,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(BranchDayService.manilaZone) },
    private val task: () -> Unit = { NextAppointmentScheduler.run(Clock.system(BranchDayService.manilaZone)) },
) {
    private var executor: ScheduledExecutorService? = null

    @Suppress("TooGenericExceptionCaught")
    private fun runTask() {
        try {
            task()
        } catch (e: RuntimeException) {
            logger.error(e) { "[SCHEDULER] Notification task failed" }
        }
    }

    @Synchronized
    @Suppress("TooGenericExceptionCaught")
    fun start() {
        if (executor?.isShutdown == false) return

        val candidate = executorFactory()
        try {
            candidate.scheduleAtFixedRate(
                ::runTask,
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
        private const val PERIOD_HOURS = 24L

        private fun createExecutor(): ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor(
                ThreadFactory { runnable ->
                    Thread(runnable, "notification-scheduler").apply { isDaemon = true }
                },
            )
    }
}
