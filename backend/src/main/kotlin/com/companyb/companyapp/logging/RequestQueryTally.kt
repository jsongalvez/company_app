package com.companyb.companyapp.logging

import com.companyb.companyapp.api.middleware.TraceIdFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.MDC
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

@Serializable
data class SlowStatementEvent(
    val event: String,
    val traceId: String,
    val ms: Long,
    val sql: String,
    val at: String? = null,
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

    /**
     * `SLOW_STATEMENT_MS=0` in the environment logs every statement with its trace id:
     * a local way to see which queries a request repeats (`tools/perf/sql-trace.py`).
     */
    private val slowMs: Long = System.getenv("SLOW_STATEMENT_MS")?.toLongOrNull() ?: SLOW_STATEMENT_MS

    /** `SLOW_STATEMENT_STACK=1` adds the app frames that ran each logged statement (`sql-trace.py --at`). */
    private val withStack: Boolean = System.getenv("SLOW_STATEMENT_STACK") == "1"
    private const val APP_PACKAGE = "com.companyb.companyapp."
    private const val STACK_FRAMES = 4
    private val plumbing = listOf("logging.", "database.")

    private const val EVENT_SLOW = "slow_statement"
    private const val TRACE_UNKNOWN = "unknown"
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val MAX_SQL_CHARS = 1_000

    private val logger = KotlinLogging.logger {}
    private val quotedLiteral = Regex("'(?:[^']|'')*'")
    private val changesNoTable = Regex("^\\s*(?:SELECT|SET|SHOW)\\b", RegexOption.IGNORE_CASE)
    private val writeTarget =
        Regex("^\\s*(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM)\\s+\"?(\\w+)\"?[\\s(]", RegexOption.IGNORE_CASE)

    private class Tally {
        var nanos = 0L
        var queries = 0
        val changed = HashSet<String>()
        val untilWrite = HashMap<ReadKey, Held>()
    }

    /** Stands in [changed] for a write whose statement text names no table, such as a batch. */
    const val UNNAMED_WRITE = "*"

    /** Names one request-scoped read kept by [untilWrite]; implement it with a data class. */
    interface ReadKey

    private class Held(
        val reads: Set<String>,
        val value: Any,
    )

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

    /**
     * The tables this request's statements wrote, rolled back or not, with [UNNAMED_WRITE] for a
     * write whose table the text does not name. Empty outside a request.
     */
    fun changed(): Set<String> =
        current
            .get()
            ?.changed
            ?.toSet()
            .orEmpty()

    /**
     * [load]'s answer for [key], kept for the rest of this request until a statement writes one of
     * the tables in [reads], or one this can't name (a batch, a rollback), so a read never outlives a
     * write that could change it. Outside a request it just loads.
     */
    fun <T : Any> untilWrite(
        key: ReadKey,
        type: KClass<T>,
        reads: Set<String>,
        load: () -> T,
    ): T {
        val memo = current.get()?.untilWrite ?: return load()
        return type.safeCast(memo[key]?.value) ?: load().also { memo[key] = Held(reads, it) }
    }

    private class Maybe(
        val value: Any?,
    )

    /** [untilWrite] for a read that may find nothing: a missing row is kept as missing too. */
    fun <T : Any> untilWriteOrNull(
        key: ReadKey,
        type: KClass<T>,
        reads: Set<String>,
        load: () -> T?,
    ): T? {
        val memo = current.get()?.untilWrite ?: return load()
        val held = memo[key]?.value as? Maybe ?: return load().also { memo[key] = Held(reads, Maybe(it)) }
        return held.value?.let { type.safeCast(it) }
    }

    fun dropReads() {
        current.get()?.untilWrite?.clear()
    }

    /** The table [sql] writes: null for a read or a setting, [UNNAMED_WRITE] when the text names none. */
    private fun writtenBy(sql: String): String? =
        if (changesNoTable.containsMatchIn(sql)) null else writeTarget.find(sql)?.groupValues?.get(1) ?: UNNAMED_WRITE

    private fun Tally.wrote(table: String) {
        changed += table
        if (table == UNNAMED_WRITE) untilWrite.clear() else untilWrite.values.removeIf { table in it.reads }
    }

    fun record(
        sql: String,
        nanos: Long,
    ) {
        current.get()?.let {
            it.nanos += nanos
            it.queries++
            writtenBy(sql)?.let { table -> it.wrote(table) }
        }
        val ms = nanos / NANOS_PER_MILLI
        if (ms >= slowMs) {
            val traceId = MDC.get(TraceIdFilter.ATTRIBUTE) ?: TRACE_UNKNOWN
            val at = if (withStack) callers() else null
            logger.warn { Json.encodeToString(SlowStatementEvent(EVENT_SLOW, traceId, ms, maskedSql(sql), at)) }
        }
    }

    private fun callers(): String =
        Thread
            .currentThread()
            .stackTrace
            .asSequence()
            .filter { it.className.startsWith(APP_PACKAGE) }
            .filter { f -> plumbing.none { f.className.removePrefix(APP_PACKAGE).startsWith(it) } }
            .take(STACK_FRAMES)
            .joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }

    private fun maskedSql(sql: String): String = sql.replace(quotedLiteral, "?").take(MAX_SQL_CHARS)
}
