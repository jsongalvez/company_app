package com.companyb.companyapp.logging

import com.companyb.companyapp.api.middleware.TraceIdFilter
import com.companyb.companyapp.observability.RouteLabels
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
    val template: String,
    val status: Int,
    val elapsedMs: Long,
    val dbMs: Long,
    val queries: Int,
)

/**
 * Machine-readable per-request logs. Emission-shape decision made
 * in-ticket: manual JSON via kotlinx-serialization (already a dependency)
 * instead of an encoder library — zero new deps, all other log lines keep
 * their human-readable text pattern, and the per-request completed line is
 * field-filterable (`... | jq 'select(.route == "/api/clients/search")
 * | select(.elapsedMs >= 2000)'`). `route` is the concrete request path
 * (query string excluded, so search terms stay out of the logs); ID-bearing
 * paths vary per row, so filter those with `startswith`/`contains`. `template`
 * is the matched route pattern (`/api/branches/{branchId}/today-screen`, or
 * `UNMATCHED`), so one route groups by one value. `dbMs` and `queries` are the
 * request's statement time and count from [RequestQueryTally]; the same split
 * goes out as `Server-Timing: db;dur=<dbMs>, app;dur=<appMs>`.
 */
object RequestLog {
    const val COMPLETED_ATTRIBUTE = "requestLogCompleted"

    private const val SERVER_TIMING_HEADER = "Server-Timing"
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
        val elapsedMs = RequestElapsedConverter.currentElapsedMs()
        val db = RequestQueryTally.snapshot()
        // Signed-in calls only: the app's own telemetry reads it, and a stranger learns nothing from sign-in timings.
        if (context.attribute<String>("userId") != null) {
            context.header(SERVER_TIMING_HEADER, "db;dur=${db.dbMs}, app;dur=${(elapsedMs - db.dbMs).coerceAtLeast(0)}")
        }
        val event =
            RequestCompletedEvent(
                event = EVENT_COMPLETED,
                traceId = traceId,
                method = context.method().name,
                route = route,
                template = RouteLabels.routeLabel(context),
                status = status,
                elapsedMs = elapsedMs,
                dbMs = db.dbMs,
                queries = db.queries,
            )
        logger.info { Json.encodeToString(event) }
        RequestElapsedConverter.endRequest()
        DeltaTimeConverter.endRequest()
        MDC.clear()
    }
}
