package com.companyb.companyapp.branchday

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The hub certificate's one server-side step. A site hub laptop gets its own Let's Encrypt
 * certificate for hub.kiel.party by DNS-01 (`hub/cert.go`); the server, which holds the DNS token,
 * writes the `_acme-challenge` record for a laptop that shows a live laptop token. The laptop's
 * private key never comes here. No DNS token configured: every write answers [Result.NotSetUp].
 */
object SiteHubCerts {
    const val RECORD = "_acme-challenge.hub.kiel.party"

    /** A certificate order makes one write; a few retries fit, a loop doesn't. */
    const val PUTS_PER_HOUR = 10
    private const val HOUR_SECONDS = 3600L
    private val VALUE = Regex("^[A-Za-z0-9_-]{43}$")

    /** Writes and deletes TXT records in the kiel.party zone. */
    interface Txt {
        fun put(
            name: String,
            value: String,
        )

        fun remove(
            name: String,
            value: String,
        )
    }

    @Volatile
    private var txt: Txt? = null
    private val puts = ConcurrentHashMap<UUID, MutableList<Long>>()

    fun configure(value: Txt?) {
        txt = value
    }

    /** Clears the per-laptop write counts (tests). */
    fun forgetCounts() = puts.clear()

    sealed interface Result {
        data class Done(
            val token: String,
        ) : Result

        data object BadToken : Result

        data object BadValue : Result

        data object TooMany : Result

        data object NotSetUp : Result
    }

    fun put(
        token: String,
        value: String,
        now: Instant = Instant.now(),
    ): Result {
        val laptop = SiteHub.laptop(token, now) ?: return Result.BadToken
        if (!VALUE.matches(value)) return Result.BadValue
        val dns = txt ?: return Result.NotSetUp
        val times = puts.computeIfAbsent(laptop) { mutableListOf() }
        synchronized(times) {
            times.removeAll { now.epochSecond - it >= HOUR_SECONDS }
            if (times.size >= PUTS_PER_HOUR) return Result.TooMany
            times += now.epochSecond
        }
        dns.put(RECORD, value)
        return Result.Done(SiteHub.laptopToken(laptop, now).token)
    }

    fun remove(
        token: String,
        value: String,
        now: Instant = Instant.now(),
    ): Result {
        SiteHub.laptop(token, now) ?: return Result.BadToken
        if (!VALUE.matches(value)) return Result.BadValue
        val dns = txt ?: return Result.NotSetUp
        dns.remove(RECORD, value)
        return Result.Done(token)
    }
}
