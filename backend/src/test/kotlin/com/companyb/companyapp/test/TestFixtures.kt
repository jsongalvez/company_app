package com.companyb.companyapp.test

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

object TestFixtures {
    private const val TEST_YEAR = 2026
    private const val TEST_MONTH = 8
    private const val TEST_DAY = 20
    private const val UUID_COUNTER_SHIFT = 32
    private val nextUuid = AtomicLong(1)
    val today: LocalDate = LocalDate.of(TEST_YEAR, TEST_MONTH, TEST_DAY)
    val now: OffsetDateTime = today.atTime(12, 0).atOffset(ZoneOffset.UTC)
    val currentMonth: YearMonth = YearMonth.of(TEST_YEAR, TEST_MONTH)

    fun uuid(): UUID = UUID(nextUuid.getAndIncrement() shl UUID_COUNTER_SHIFT, 0L)

    fun realNow(): java.time.Instant =
        java.time.Clock
            .systemUTC()
            .instant()

    fun waitForNextSecond() {
        val boundary = realNow().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(1)
        while (realNow().isBefore(boundary)) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10))
        }
    }
}
