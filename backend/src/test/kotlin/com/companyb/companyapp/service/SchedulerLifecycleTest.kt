package com.companyb.companyapp.service

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
                task = {},
            )

        lifecycle.start()
        lifecycle.start()

        assertEquals(EXPECTED_EXECUTOR_COUNT, createdExecutors)
        lifecycle.stop()
        assertTrue(executor.isShutdown)
    }

    @Test
    fun `stop is safe when scheduler was never started`() {
        SchedulerLifecycle(now = { startTime }, task = {}).stop()
    }

    @Test
    fun `failed scheduling shuts down candidate executor`() {
        val executor = FailingExecutor()
        val lifecycle =
            SchedulerLifecycle(
                executorFactory = { executor },
                now = { startTime },
                task = {},
            )

        assertFailsWith<IllegalStateException> { lifecycle.start() }
        assertTrue(executor.isShutdown)
    }

    private class FailingExecutor : ScheduledThreadPoolExecutor(THREAD_COUNT) {
        override fun scheduleAtFixedRate(
            command: Runnable,
            initialDelay: Long,
            period: Long,
            unit: TimeUnit,
        ): ScheduledFuture<*> = error("schedule failed")
    }

    private companion object {
        private const val THREAD_COUNT = 1
        private const val EXPECTED_EXECUTOR_COUNT = 1
    }
}
