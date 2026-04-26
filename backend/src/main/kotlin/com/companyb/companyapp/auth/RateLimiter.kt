package com.companyb.companyapp.auth

import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Collections
import kotlin.time.Duration.Companion.seconds

object RateLimiter {
    private val logger = KotlinLogging.logger {}

    private data class Window(
        var count: Int,
        var now: Long,
    )

    private const val MAX_REQUESTS = 10
    private const val MAX_ENTRIES = 1000 // Bound memory usage
    private val WINDOW_NANOSECONDS = 60.seconds.inWholeNanoseconds

    private val list: MutableMap<String, Window> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Window>(64, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, Window>): Boolean = size > MAX_ENTRIES
            },
        )

    fun isAllowed(ip: String): Boolean {
        val now = System.nanoTime()

        // Use synchronized on the list to ensure atomic read-modify-write
        synchronized(list) {
            val window =
                list.getOrPut(ip) {
                    Window(0, now)
                }

            val isWindowExpired = now - window.now > WINDOW_NANOSECONDS
            if (isWindowExpired) {
                window.count = 1
                window.now = now
                return true
            }

            window.count++
            val isAllowed = window.count <= MAX_REQUESTS
            if (!isAllowed) {
                logger.warn { "[RATE-LIMITER] Rate limiting ip ($ip), tries: ${window.count}" }
            }
            return isAllowed
        }
    }
}
