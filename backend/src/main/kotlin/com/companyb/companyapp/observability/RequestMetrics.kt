package com.companyb.companyapp.observability

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.logging.RequestElapsedConverter
import io.javalin.http.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory per-route RED store + Prometheus exposition (#473).
 *
 * Zero-dep decision (mirrors #472): histogram buckets counted in-process with
 * atomics, P99 computed server-side by Prometheus/Grafana via
 * `histogram_quantile` — no Micrometer/Prometheus client dependency.
 * Recording is O(buckets) atomic increments, no allocation on the hot path
 * beyond the map lookup; `/metrics` itself is excluded so scrapes never move
 * the traffic leg. Routes are normalized (UUID/numeric segments → `{id}`) to
 * bound label cardinality; the query string never reaches here (`path()`
 * excludes it, so search terms stay out of metrics).
 */
object RequestMetrics {
    const val OBSERVED_ATTRIBUTE = "requestMetricsObserved"
    private const val STATUS_UNKNOWN = 0
    private const val SERVER_ERROR_THRESHOLD = 500
    private const val MILLIS_PER_SECOND = 1000.0
    private const val SELF_SCRAPE_PATH = "/metrics"
    private const val BUCKET_5MS = 0.005
    private const val BUCKET_10MS = 0.01
    private const val BUCKET_25MS = 0.025
    private const val BUCKET_50MS = 0.05
    private const val BUCKET_100MS = 0.1
    private const val BUCKET_250MS = 0.25
    private const val BUCKET_500MS = 0.5
    private const val BUCKET_1S = 1.0
    private const val BUCKET_2_5S = 2.5
    private const val BUCKET_5S = 5.0
    private const val BUCKET_10S = 10.0

    private val buckets =
        doubleArrayOf(
            BUCKET_5MS,
            BUCKET_10MS,
            BUCKET_25MS,
            BUCKET_50MS,
            BUCKET_100MS,
            BUCKET_250MS,
            BUCKET_500MS,
            BUCKET_1S,
            BUCKET_2_5S,
            BUCKET_5S,
            BUCKET_10S,
        )

    private val uuidSegment = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val numericSegment = Regex("/\\d+(?=/|\$)")

    private data class RouteKey(
        val method: String,
        val route: String,
    )

    private class RouteStats {
        val count = AtomicLong()
        val errors = AtomicLong()
        val sumMillis = AtomicLong()
        val bucketCounts = Array(buckets.size) { AtomicLong() }
    }

    private val stats = ConcurrentHashMap<RouteKey, RouteStats>()

    fun normalizeRoute(path: String): String {
        val noUuid = uuidSegment.replace(path, "{id}")
        return numericSegment.replace(noUuid, "/{id}")
    }

    fun observe(
        method: String,
        route: String,
        status: Int,
        elapsedMs: Long,
    ) {
        if (route == SELF_SCRAPE_PATH || route == ApiRoutes.METRICS) return
        val key = RouteKey(method, normalizeRoute(route))
        val entry = stats.computeIfAbsent(key) { RouteStats() }
        entry.count.incrementAndGet()
        if (status >= SERVER_ERROR_THRESHOLD) entry.errors.incrementAndGet()
        entry.sumMillis.addAndGet(elapsedMs.coerceAtLeast(0))
        val elapsedSec = elapsedMs.coerceAtLeast(0) / MILLIS_PER_SECOND
        for (i in buckets.indices) {
            if (elapsedSec <= buckets[i]) entry.bucketCounts[i].incrementAndGet()
        }
    }

    /**
     * Records one request exactly once: exception handlers and the global
     * `after` filter both call this, and whichever runs first wins (mirrors
     * `RequestLog.COMPLETED_ATTRIBUTE`).
     */
    fun observe(context: Context) {
        if (context.attribute<Boolean>(OBSERVED_ATTRIBUTE) == true) return
        context.attribute(OBSERVED_ATTRIBUTE, true)
        val status = runCatching { context.statusCode() }.getOrDefault(STATUS_UNKNOWN)
        observe(context.method().name, context.path(), status, RequestElapsedConverter.currentElapsedMs())
    }

    fun reset() = stats.clear()

    fun snapshot(): Map<Pair<String, String>, Long> =
        stats.mapKeys { (key, _) -> key.method to key.route }.mapValues { (_, v) -> v.count.get() }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    fun render(pool: DatabaseConfig.PoolStats = DatabaseConfig.poolStats()): String {
        val out = StringBuilder()
        appendCounters(out)
        appendHistogram(out)
        appendPool(out, pool)
        return out.toString()
    }

    private fun appendCounters(out: StringBuilder) {
        out.append("# HELP http_requests_total Total HTTP requests.\n# TYPE http_requests_total counter\n")
        stats.toSortedMap(compareBy({ it.method }, { it.route })).forEach { (key, entry) ->
            out.append(
                "http_requests_total{method=\"${escape(
                    key.method,
                )}\",route=\"${escape(key.route)}\"} ${entry.count.get()}\n",
            )
        }
        out.append("# HELP http_request_errors_total Total HTTP requests with 5xx status.\n")
        out.append("# TYPE http_request_errors_total counter\n")
        stats.toSortedMap(compareBy({ it.method }, { it.route })).forEach { (key, entry) ->
            out.append(
                "http_request_errors_total{method=\"${escape(
                    key.method,
                )}\",route=\"${escape(key.route)}\"} ${entry.errors.get()}\n",
            )
        }
    }

    private fun appendHistogram(out: StringBuilder) {
        out.append("# HELP http_request_duration_seconds Request latency histogram.\n")
        out.append("# TYPE http_request_duration_seconds histogram\n")
        stats.toSortedMap(compareBy({ it.method }, { it.route })).forEach { (key, entry) ->
            val count = entry.count.get()
            for (i in buckets.indices) {
                out.append(
                    "http_request_duration_seconds_bucket{method=\"${escape(
                        key.method,
                    )}\",route=\"${escape(key.route)}\",le=\"${buckets[i]}\"} ${entry.bucketCounts[i].get()}\n",
                )
            }
            out.append(
                "http_request_duration_seconds_bucket{method=\"${escape(
                    key.method,
                )}\",route=\"${escape(key.route)}\",le=\"+Inf\"} $count\n",
            )
            out.append(
                "http_request_duration_seconds_sum{method=\"${escape(
                    key.method,
                )}\",route=\"${escape(key.route)}\"} ${entry.sumMillis.get() / MILLIS_PER_SECOND}\n",
            )
            out.append(
                "http_request_duration_seconds_count{method=\"${escape(
                    key.method,
                )}\",route=\"${escape(key.route)}\"} $count\n",
            )
        }
    }

    private fun appendPool(
        out: StringBuilder,
        pool: DatabaseConfig.PoolStats,
    ) {
        out.append("# HELP hikaricp_connections_active Active HikariCP connections.\n")
        out.append("# TYPE hikaricp_connections_active gauge\n")
        out.append("hikaricp_connections_active ${pool.active}\n")
        out.append(
            "# HELP hikaricp_connections_idle Idle HikariCP connections.\n# TYPE hikaricp_connections_idle gauge\n",
        )
        out.append("hikaricp_connections_idle ${pool.idle}\n")
        out.append("# HELP hikaricp_connections_pending Threads awaiting a connection.\n")
        out.append("# TYPE hikaricp_connections_pending gauge\n")
        out.append("hikaricp_connections_pending ${pool.awaiting}\n")
        out.append(
            "# HELP hikaricp_connections_total Total HikariCP connections.\n# TYPE hikaricp_connections_total gauge\n",
        )
        out.append("hikaricp_connections_total ${pool.total}\n")
    }
}
