package com.companyb.companyapp.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

class DeltaTimeConverter : ClassicConverter() {
    companion object {
        private const val NANOS_PER_MILLI = 1_000_000
        private val lastNanos = ThreadLocal<Long>()

        fun startRequest() {
            lastNanos.set(System.nanoTime())
        }

        fun endRequest() {
            lastNanos.remove()
        }
    }

    override fun convert(event: ILoggingEvent): String {
        val now = System.nanoTime()
        val last = lastNanos.get() ?: return "-"
        val deltaMs = (now - last) / NANOS_PER_MILLI
        lastNanos.set(now)
        return "$deltaMs"
    }
}
