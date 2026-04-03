package com.companyb.companyapp.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

object RateLimiter {
    private val logger = KotlinLogging.logger {}

    private data class Window(
        var count: Int,
        var now: Long,
    )

    const val MAX_REQUESTS = 10
    val WINDOW_NANOSECONDS = 60.seconds.inWholeNanoseconds

    private val list = ConcurrentHashMap<String, Window>()

    fun isAllowed(ip: String): Boolean {
        val now = System.nanoTime()

        val window =
            list.computeIfAbsent(ip) {
                Window(0, now).also { logger.info { "[RATE-LIMITER] Add new entry" } }
            }

        val isWindowExpired = now - window.now > WINDOW_NANOSECONDS
        if (isWindowExpired) {
            window.count = 1
            window.now = now
            return true.also { logger.info { "[RATE-LIMITER] Reset limits; Request count: ${window.count}" } }
        }

        window.count++
        val isAllowed = window.count <= MAX_REQUESTS
        when (isAllowed) {
            true -> logger.info { "[RATE-LIMITER] Request count: ${window.count}" }
            false -> logger.warn { "[RATE-LIMITER] rate limiting ip ($ip), tries: ${window.count}" }
        }
        return isAllowed
    }
}
