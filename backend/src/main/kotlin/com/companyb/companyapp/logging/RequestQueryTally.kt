package com.companyb.companyapp.logging

import com.companyb.companyapp.api.middleware.TraceIdFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.MDC

@Serializable
data class SlowStatementEvent(
    val event: String,
    val traceId: String,
    val ms: Long,
    val sql: String,
)

/**
 * Database time of the request running on this thread. A request handler runs
 * start to finish on one thread, so a [ThreadLocal] is enough; it is replaced
 * by [startRequest] and removed with the rest of the request scope by
 * [RequestElapsedConverter.endRequest], so a pooled thread never carries the
 * last request's count into the next one. Threads outside a request (the
 * scheduler) have no tally and only get the slow-statement line.
 *
 * The JDBC wrapper in `database/JdbcTiming.kt` calls [record] once per executed
 * statement. A statement over [SLOW_STATEMENT_MS] also logs one `slow_statement`
 * line with the request's trace id and the SQL text. Prepared statements hold
 * `?` placeholders, never bound values, and quoted literals in plain statements
 * are masked.
 */
object RequestQueryTally {
    const val SLOW_STATEMENT_MS = 200L

    private const val EVENT_SLOW = "slow_statement"
    private const val TRACE_UNKNOWN = "unknown"
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val MAX_SQL_CHARS = 1_000

    private val logger = KotlinLogging.logger {}
    private val quotedLiteral = Regex("'(?:[^']|'')*'")

    private class Tally {
        var nanos = 0L
        var queries = 0
    }

    data class Snapshot(
        val dbMs: Long,
        val queries: Int,
    )

    private val current = ThreadLocal<Tally>()

    fun startRequest() {
        current.set(Tally())
    }

    fun endRequest() {
        current.remove()
    }

    fun snapshot(): Snapshot {
        val tally = current.get() ?: return Snapshot(dbMs = 0, queries = 0)
        return Snapshot(dbMs = tally.nanos / NANOS_PER_MILLI, queries = tally.queries)
    }

    fun record(
        sql: String,
        nanos: Long,
    ) {
        current.get()?.let {
            it.nanos += nanos
            it.queries++
        }
        val ms = nanos / NANOS_PER_MILLI
        if (ms >= SLOW_STATEMENT_MS) {
            val traceId = MDC.get(TraceIdFilter.ATTRIBUTE) ?: TRACE_UNKNOWN
            logger.warn { Json.encodeToString(SlowStatementEvent(EVENT_SLOW, traceId, ms, maskedSql(sql))) }
        }
    }

    private fun maskedSql(sql: String): String = sql.replace(quotedLiteral, "?").take(MAX_SQL_CHARS)
}
