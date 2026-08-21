package com.companyb.companyapp.test

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport

object TestFixtures {
    private const val UUID_COUNTER_SHIFT = 32
    private val manilaZone = ZoneId.of("Asia/Manila")
    private val nextUuid = AtomicLong(1)
    val today: LocalDate = LocalDate.now(manilaZone)
    val now: OffsetDateTime = today.atTime(12, 0).atOffset(ZoneOffset.UTC)
    val currentMonth: YearMonth = YearMonth.from(today)

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
