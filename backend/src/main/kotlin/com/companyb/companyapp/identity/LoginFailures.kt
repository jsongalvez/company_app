package com.companyb.companyapp.identity

import java.util.Locale
import kotlin.time.Duration.Companion.minutes

/**
 * Wrong passwords per account, whatever address they come from: [RateLimiter] counts per address, so many
 * addresses could keep guessing at one account. After [MAX_FAILURES] in a window the account refuses sign-in
 * until the window that began with the first failure ends, so a real person waits at most [WINDOW] and a
 * guesser gets [MAX_FAILURES] tries per [WINDOW]. Any name counts, existing or not, so a refusal says nothing
 * about whether the account exists.
 */
object LoginFailures {
    const val MAX_FAILURES = 10
    val WINDOW = 15.minutes

    private const val MAX_ENTRIES = 10_000
    private const val INITIAL_CAPACITY = 64
    private const val LOAD_FACTOR = 0.75f

    private class Window(
        val startedAt: Long,
        var failures: Int,
    )

    private val windows =
        object : LinkedHashMap<String, Window>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Window>): Boolean = size > MAX_ENTRIES
        }

    internal var clock: () -> Long = System::nanoTime

    private fun key(username: String) = username.trim().lowercase(Locale.ROOT)

    private fun live(key: String): Window? {
        val w = windows[key] ?: return null
        if (clock() - w.startedAt <= WINDOW.inWholeNanoseconds) return w
        windows.remove(key)
        return null
    }

    fun isLocked(username: String): Boolean =
        synchronized(windows) { (live(key(username))?.failures ?: 0) >= MAX_FAILURES }

    fun failed(username: String) {
        synchronized(windows) {
            val k = key(username)
            val w = live(k) ?: Window(clock(), 0).also { windows[k] = it }
            w.failures++
        }
    }

    fun succeeded(username: String) {
        synchronized(windows) { windows.remove(key(username)) }
    }

    internal fun resetForTest() {
        synchronized(windows) { windows.clear() }
        clock = System::nanoTime
    }
}
