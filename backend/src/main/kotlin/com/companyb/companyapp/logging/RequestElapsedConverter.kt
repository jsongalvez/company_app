package com.companyb.companyapp.logging

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

class RequestElapsedConverter : ClassicConverter() {
    companion object {
        private val requestStartNanos = ThreadLocal<Long>()

        fun startRequest() {
            requestStartNanos.set(System.nanoTime())
        }

        fun endRequest() {
            requestStartNanos.remove()
        }

        fun currentElapsedMs(): Long {
            val start = requestStartNanos.get() ?: return -1
            return (System.nanoTime() - start) / 1_000_000
        }
    }

    override fun convert(event: ILoggingEvent): String {
        val start = requestStartNanos.get() ?: return "-"
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        return "$elapsedMs"
    }
}
