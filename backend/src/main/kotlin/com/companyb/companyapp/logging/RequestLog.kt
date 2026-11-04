package com.companyb.companyapp.logging

import com.companyb.companyapp.api.middleware.TraceIdFilter
import com.companyb.companyapp.observability.RequestOutcome
import com.companyb.companyapp.observability.RequestSpan
import com.companyb.companyapp.observability.RouteLabels
import com.companyb.companyapp.observability.className
import io.github.oshai.kotlinlogging.KotlinLogging
import io.javalin.http.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.MDC

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
    val user: String? = null,
    val branch: String? = null,
    val outcome: String? = null,
    val error: String? = null,
)

/**
 * One wide line per request, written when it is answered: route, status, outcome, masked user,
 * branch, statement count and time, and the error class behind a 500. The same fields close the
 * request's root span ([RequestSpan]), stored in `trace_span`. The line is
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

    /** The exception behind a 500, set by the catch-all handler in `Main.kt`. */
    const val SERVER_ERROR_ATTRIBUTE = "serverError"
    private const val EVENT_COMPLETED = "request_completed"
    private const val TRACE_UNKNOWN = "unknown"
    private const val STATUS_UNKNOWN = 0

    private val logger = KotlinLogging.logger {}

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
        val error = context.attribute<Throwable>(SERVER_ERROR_ATTRIBUTE)
        val outcome =
            RequestOutcome(
                method = context.method().name,
                template = RouteLabels.routeLabel(context),
                status = status,
                elapsedMs = elapsedMs,
                dbMs = db.dbMs,
                queries = db.queries,
                user = context.attribute<String>("userId")?.maskUUID(),
                branch = runCatching { context.pathParamMap()["branchId"] }.getOrNull(),
                viewAs = context.header("X-View-As") != null,
                error = error,
            )
        val event =
            RequestCompletedEvent(
                event = EVENT_COMPLETED,
                traceId = traceId,
                method = outcome.method,
                route = route,
                template = outcome.template,
                status = status,
                elapsedMs = elapsedMs,
                dbMs = db.dbMs,
                queries = db.queries,
                user = outcome.user,
                branch = outcome.branch,
                outcome = outcome.label,
                error = error?.className(),
            )
        logger.info { Json.encodeToString(event) }
        RequestSpan.end(context, outcome)
        RequestElapsedConverter.endRequest()
        DeltaTimeConverter.endRequest()
        MDC.clear()
    }
}
