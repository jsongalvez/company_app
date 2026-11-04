package com.companyb.companyapp.observability

import io.github.oshai.kotlinlogging.KotlinLogging
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanId
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

internal object TraceSpanTable : Table("trace_span") {
    private const val ID_LENGTH = 32
    private const val SPAN_ID_LENGTH = 16
    private const val NAME_LENGTH = 160

    val traceId = char("trace_id", ID_LENGTH)
    val spanId = char("span_id", SPAN_ID_LENGTH)
    val parentId = char("parent_id", SPAN_ID_LENGTH).nullable()
    val name = varchar("name", NAME_LENGTH)
    val startAt = timestampWithTimeZone("start_at")
    val durationUs = long("duration_us")
    val error = bool("error")
    val attrs = text("attrs")
    val receivedAt = timestampWithTimeZone("received_at").defaultExpression(CurrentTimestampWithTimeZone)

    override val primaryKey = PrimaryKey(traceId, spanId)
}

/**
 * Writes finished spans into [TraceSpanTable], our own store: no collector, no vendor. Runs on the
 * span processor's thread. Every [TRIM_EVERY_MS] it deletes rows older than [RETENTION_DAYS] and the
 * oldest rows past [ROW_CAP]. Swapping in another OpenTelemetry exporter (OTLP) is a one-line change
 * in `Main.kt`.
 */
internal class PostgresSpanExporter(
    private val nowMs: () -> Long = System::currentTimeMillis,
) : SpanExporter {
    private val logger = KotlinLogging.logger {}
    private var lastTrimMs = 0L

    override fun export(spans: Collection<SpanData>): CompletableResultCode =
        runCatching {
            transaction {
                TraceSpanTable.batchInsert(spans, ignore = true, shouldReturnGeneratedValues = false) { s ->
                    this[TraceSpanTable.traceId] = s.traceId
                    this[TraceSpanTable.spanId] = s.spanId
                    this[TraceSpanTable.parentId] = s.parentSpanId.takeIf { SpanId.isValid(it) }
                    this[TraceSpanTable.name] = s.name.take(NAME_MAX)
                    this[TraceSpanTable.startAt] = at(s.startEpochNanos)
                    this[TraceSpanTable.durationUs] = (s.endEpochNanos - s.startEpochNanos) / NANOS_PER_MICRO
                    this[TraceSpanTable.error] = s.status.statusCode == StatusCode.ERROR
                    this[TraceSpanTable.attrs] = attrsJson(s)
                }
            }
            trimIfDue()
        }.fold(
            onSuccess = { CompletableResultCode.ofSuccess() },
            onFailure = {
                logger.warn(it) { "[TRACING] ${spans.size} spans not stored" }
                CompletableResultCode.ofFailure()
            },
        )

    override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

    override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()

    private fun trimIfDue() {
        val now = nowMs()
        if (now - lastTrimMs < TRIM_EVERY_MS) return
        lastTrimMs = now
        trim(OffsetDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneOffset.UTC))
    }

    companion object {
        const val RETENTION_DAYS = 14L
        const val ROW_CAP = 2_000_000L
        private const val TRIM_EVERY_MS = 10 * 60 * 1000L
        private const val NAME_MAX = 160
        private const val NANOS_PER_MICRO = 1_000L

        fun trim(
            now: OffsetDateTime,
            cap: Long = ROW_CAP,
        ): Int =
            transaction {
                val expired = TraceSpanTable.deleteWhere { receivedAt less now.minusDays(RETENTION_DAYS) }
                val boundary =
                    TraceSpanTable
                        .select(TraceSpanTable.receivedAt)
                        .orderBy(TraceSpanTable.receivedAt to SortOrder.DESC)
                        .limit(1)
                        .offset(cap)
                        .singleOrNull()
                        ?.get(TraceSpanTable.receivedAt)
                expired + (boundary?.let { b -> TraceSpanTable.deleteWhere { receivedAt less b } } ?: 0)
            }

        private fun at(epochNanos: Long): OffsetDateTime =
            OffsetDateTime.ofInstant(Instant.ofEpochSecond(0, epochNanos), ZoneOffset.UTC)

        internal fun attrsJson(s: SpanData): String {
            val map = LinkedHashMap<String, JsonElement>()
            s.attributes.forEach { key: AttributeKey<*>, value: Any? ->
                map[key.key] =
                    when (value) {
                        is Boolean -> JsonPrimitive(value)
                        is Number -> JsonPrimitive(value)
                        else -> JsonPrimitive(value.toString())
                    }
            }
            return JsonObject(map).toString()
        }
    }
}
