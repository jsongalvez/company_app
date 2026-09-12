package com.companyb.companyapp.app

import com.companyb.companyapp.notification.NextAppointmentScheduler
import com.companyb.companyapp.workforce.relief.ReliefInviteReminderJob
import com.companyb.companyapp.workforce.relief.ReliefRequestExpiryJob
import java.io.IOException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SchedulerLifecycleTest {
    private val manilaZone = ZoneId.of("Asia/Manila")
    private val startTime = ZonedDateTime.of(2026, 8, 18, 6, 0, 0, 0, manilaZone)

    private fun productionJobs(): List<ScheduledJob> =
        listOf(
            ScheduledJob(
                name = "Notification",
                initialDelayMs = NextAppointmentScheduler::nextRunDelayMs,
                task = {},
            ),
            ScheduledJob(
                name = "Relief-expiry",
                initialDelayMs = ReliefRequestExpiryJob::nextRunDelayMs,
                task = {},
            ),
            ScheduledJob(
                name = "Relief-reminder",
                initialDelayMs = ReliefInviteReminderJob::nextRunDelayMs,
                task = {},
            ),
        )

    @Test
    fun `start is idempotent and stop shuts down executor`() {
        var createdExecutors = 0
        val executor = ScheduledThreadPoolExecutor(THREAD_COUNT)
        val lifecycle =
            SchedulerLifecycle(
                executorFactory = {
                    createdExecutors += 1
                    executor
                },
                now = { startTime },
                jobs = productionJobs(),
            )

        lifecycle.start()
        lifecycle.start()

        assertEquals(EXPECTED_EXECUTOR_COUNT, createdExecutors)
        lifecycle.stop()
        assertTrue(executor.isShutdown)
    }

    @Test
    fun `stop is safe when scheduler was never started`() {
        SchedulerLifecycle(now = { startTime }, jobs = productionJobs()).stop()
    }

    @Test
    fun `checked task exception is swallowed and never reaches the executor`() {
        val executor = RecordingExecutor()
        val lifecycle =
            SchedulerLifecycle(
                executorFactory = { executor },
                now = { startTime },
                jobs =
                    listOf(
                        ScheduledJob(
                            name = "Boom",
                            initialDelayMs = { NO_DELAY_MS },
                            task = { throw IOException("boom") },
                        ),
                    ),
            )

        lifecycle.start()
        try {
            assertEquals(EXPECTED_JOB_COUNT, executor.calls.size)
            // Must not throw: an escaping throw would complete the fixed-rate
            // future exceptionally and silently cancel all future runs. ref #914
            executor.commands.single().run()
        } finally {
            lifecycle.stop()
        }
    }

    @Test
    fun `failed scheduling shuts down candidate executor`() {
        val executor = FailingExecutor()
        val lifecycle =
            SchedulerLifecycle(
                executorFactory = { executor },
                now = { startTime },
                jobs = productionJobs(),
            )

        assertFailsWith<IllegalStateException> { lifecycle.start() }
        assertTrue(executor.isShutdown)
    }

    @Test
    fun `start wires millisecond delays and 24h periods through the production seam`() {
        val executor = RecordingExecutor()
        val lifecycle =
            SchedulerLifecycle(
                executorFactory = { executor },
                now = { startTime },
                jobs = productionJobs(),
            )

        lifecycle.start()
        try {
            assertEquals(3, executor.calls.size)
            val delaysMs = executor.calls.map { it.unit.toMillis(it.initialDelay) }
            val periodsMs = executor.calls.map { it.unit.toMillis(it.period) }

            assertEquals(
                java.time.Duration
                    .ofHours(1)
                    .toMillis(),
                delaysMs[0],
            )
            assertEquals(
                java.time.Duration
                    .ofHours(1)
                    .toMillis(),
                delaysMs[2],
            )
            assertEquals(
                java.time.Duration
                    .between(
                        startTime,
                        startTime
                            .plusDays(1)
                            .withHour(4)
                            .withMinute(5)
                            .withSecond(0)
                            .withNano(0),
                    ).toMillis(),
                delaysMs[1],
            )
            periodsMs.forEach {
                assertEquals(
                    java.time.Duration
                        .ofHours(24)
                        .toMillis(),
                    it,
                )
            }
        } finally {
            lifecycle.stop()
        }
    }

    @Test
    fun `start covers before at and after run boundaries`() {
        assertAppointmentBoundaries()
        assertExpiryBoundaries()
    }

    private fun assertAppointmentBoundaries() {
        val cases =
            listOf(
                ZonedDateTime.of(2026, 8, 18, 6, 59, 0, 0, manilaZone) to minutesToMs(1),
                ZonedDateTime.of(2026, 8, 18, 7, 0, 0, 0, manilaZone) to hoursToMs(24),
                ZonedDateTime.of(2026, 8, 18, 8, 0, 0, 0, manilaZone) to hoursToMs(23),
            )
        for ((now, expectedAppointmentMs) in cases) {
            val executor = RecordingExecutor()
            val lifecycle = SchedulerLifecycle(executorFactory = { executor }, now = { now }, jobs = productionJobs())
            lifecycle.start()
            try {
                val delaysMs = executor.calls.map { it.unit.toMillis(it.initialDelay) }
                assertEquals(expectedAppointmentMs, delaysMs[0], "now=$now")
                assertEquals(expectedAppointmentMs, delaysMs[2], "now=$now")
                executor.calls.map { it.unit.toMillis(it.period) }.forEach {
                    assertEquals(hoursToMs(24), it, "now=$now")
                }
            } finally {
                lifecycle.stop()
            }
        }
    }

    private fun assertExpiryBoundaries() {
        val expiryCases =
            listOf(
                ZonedDateTime.of(2026, 8, 18, 4, 4, 0, 0, manilaZone) to minutesToMs(1),
                ZonedDateTime.of(2026, 8, 18, 4, 5, 0, 0, manilaZone) to 0L,
                ZonedDateTime.of(2026, 8, 18, 4, 6, 0, 0, manilaZone) to
                    java.time.Duration
                        .ofHours(24)
                        .minusMinutes(1)
                        .toMillis(),
            )
        for ((now, expectedExpiryMs) in expiryCases) {
            val executor = RecordingExecutor()
            val lifecycle = SchedulerLifecycle(executorFactory = { executor }, now = { now }, jobs = productionJobs())
            lifecycle.start()
            try {
                val delaysMs = executor.calls.map { it.unit.toMillis(it.initialDelay) }
                assertEquals(expectedExpiryMs, delaysMs[1], "now=$now")
            } finally {
                lifecycle.stop()
            }
        }
    }

    private fun minutesToMs(minutes: Long): Long =
        java.time.Duration
            .ofMinutes(minutes)
            .toMillis()

    private fun hoursToMs(hours: Long): Long =
        java.time.Duration
            .ofHours(hours)
            .toMillis()

    private class FailingExecutor : ScheduledThreadPoolExecutor(THREAD_COUNT) {
        override fun scheduleAtFixedRate(
            command: Runnable,
            initialDelay: Long,
            period: Long,
            unit: TimeUnit,
        ): ScheduledFuture<*> = error("schedule failed")
    }

    private data class ScheduleCall(
        val initialDelay: Long,
        val period: Long,
        val unit: TimeUnit,
    )

    private class RecordingExecutor : ScheduledThreadPoolExecutor(THREAD_COUNT) {
        val calls = mutableListOf<ScheduleCall>()
        val commands = mutableListOf<Runnable>()

        override fun scheduleAtFixedRate(
            command: Runnable,
            initialDelay: Long,
            period: Long,
            unit: TimeUnit,
        ): ScheduledFuture<*> {
            calls.add(ScheduleCall(initialDelay, period, unit))
            commands.add(command)
            return object : ScheduledFuture<Any> {
                override fun compareTo(other: java.util.concurrent.Delayed): Int = 0

                override fun getDelay(unit: TimeUnit): Long = 0

                override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

                override fun isCancelled(): Boolean = false

                override fun isDone(): Boolean = true

                override fun get(): Any = error("no result")

                override fun get(
                    timeout: Long,
                    unit: TimeUnit,
                ): Any = error("no result")
            }
        }
    }

    private companion object {
        private const val THREAD_COUNT = 1
        private const val EXPECTED_EXECUTOR_COUNT = 1
        private const val EXPECTED_JOB_COUNT = 1
        private const val NO_DELAY_MS = 0L
    }
}
