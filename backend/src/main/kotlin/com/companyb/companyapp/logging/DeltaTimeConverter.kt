package com.companyb.companyapp.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

class DeltaTimeConverter : ClassicConverter() {
    companion object {
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
        val deltaMs = (now - last) / 1_000_000
        lastNanos.set(now)
        return "$deltaMs"
    }
}
