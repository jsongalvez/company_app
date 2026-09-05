package com.companyb.companyapp.logging

import com.companyb.companyapp.api.middleware.TraceIdFilter
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.MDC

@Serializable
data class RequestStartedEvent(
    val event: String,
    val traceId: String,
    val method: String,
    val route: String,
)

@Serializable
data class RequestCompletedEvent(
    val event: String,
    val traceId: String,
    val method: String,
    val route: String,
    val status: Int,
    val elapsedMs: Long,
)

/**
 * Machine-readable per-request logs (#472). Emission-shape decision made
 * in-ticket: manual JSON via kotlinx-serialization (already a dependency)
 * instead of an encoder library — zero new deps, all other log lines keep
 * their human-readable text pattern, and the per-request completed line is
 * field-filterable (`... | jq 'select(.route == "/api/clients/search")
 * | select(.elapsedMs >= 2000)'`). `route` is the concrete request path
 * (query string excluded, so search terms stay out of the logs); ID-bearing
 * paths vary per row, so filter those with `startswith`/`contains`.
 */
object RequestLog {
    const val COMPLETED_ATTRIBUTE = "requestLogCompleted"

    private const val EVENT_STARTED = "request_started"
    private const val EVENT_COMPLETED = "request_completed"
    private const val TRACE_UNKNOWN = "unknown"
    private const val STATUS_UNKNOWN = 0

    private val logger = KotlinLogging.logger {}

    fun formatStart(
        traceId: String,
        method: String,
        route: String,
    ): String = Json.encodeToString(RequestStartedEvent(EVENT_STARTED, traceId, method, route))

    fun formatComplete(
        traceId: String,
        method: String,
        route: String,
        status: Int,
        elapsedMs: Long,
    ): String = Json.encodeToString(RequestCompletedEvent(EVENT_COMPLETED, traceId, method, route, status, elapsedMs))

    fun start(context: Context) {
        val traceId =
            MDC.get(TraceIdFilter.ATTRIBUTE) ?: context.attribute<String>(TraceIdFilter.ATTRIBUTE) ?: TRACE_UNKNOWN
        logger.info { formatStart(traceId, context.method().name, context.path()) }
    }

    /**
     * Logs the completed line exactly once per request: exception handlers
     * and the global `after` filter both call this, and whichever runs first
     * wins. Owns the request-scope cleanup (converters + MDC) so error paths
     * cannot leak ThreadLocal state onto pooled threads.
     */
    fun complete(context: Context) {
        if (context.attribute<Boolean>(COMPLETED_ATTRIBUTE) == true) return
        context.attribute(COMPLETED_ATTRIBUTE, true)
        val traceId =
            MDC.get(TraceIdFilter.ATTRIBUTE) ?: context.attribute<String>(TraceIdFilter.ATTRIBUTE) ?: TRACE_UNKNOWN
        // Concrete request path (query string excluded by path()): endpoint()
        // is the wildcard after-filter itself here, so its template ("*") is useless.
        val route = context.path()
        val status = runCatching { context.statusCode() }.getOrDefault(STATUS_UNKNOWN)
        logger.info {
            formatComplete(traceId, context.method().name, route, status, RequestElapsedConverter.currentElapsedMs())
        }
        RequestElapsedConverter.endRequest()
        DeltaTimeConverter.endRequest()
        MDC.clear()
    }
}
