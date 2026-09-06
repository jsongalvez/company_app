package com.companyb.companyapp.observability

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.middleware.TraceIdFilter
import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.RequestElapsedConverter
import com.companyb.companyapp.logging.RequestLog
import io.javalin.Javalin
import io.javalin.http.BadRequestResponse
import io.javalin.http.HttpStatus
import io.javalin.http.UnauthorizedResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

class RequestMetricsTest {
    @AfterTest
    fun clear() = RequestMetrics.reset()

    @Test
    fun `normalize collapses uuid and numeric segments`() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        assertEquals("/api/clients/{id}", RequestMetrics.normalizeRoute("/api/clients/$uuid"))
        assertEquals("/api/branches/{id}/inventory", RequestMetrics.normalizeRoute("/api/branches/42/inventory"))
        assertEquals("/api/clients/search", RequestMetrics.normalizeRoute("/api/clients/search"))
    }

    @Test
    fun `record counts requests and classifies 5xx as errors only`() {
        RequestMetrics.observe("GET", "/api/branches", 200, 10)
        RequestMetrics.observe("GET", "/api/branches", 200, 20)
        RequestMetrics.observe("GET", "/api/branches", 400, 5)
        RequestMetrics.observe("GET", "/api/branches", 500, 5)
        val body = RequestMetrics.render(DatabaseConfig.PoolStats.empty())
        assertContains(body, "http_requests_total{method=\"GET\",route=\"/api/branches\"} 4")
        assertContains(body, "http_request_errors_total{method=\"GET\",route=\"/api/branches\"} 1")
    }

    @Test
    fun `buckets are cumulative so a slow search spikes only high les`() {
        RequestMetrics.observe("GET", "/api/clients/search", 200, FAST_MS)
        RequestMetrics.observe("GET", "/api/clients/search", 200, SLOW_MS)
        val body = RequestMetrics.render(DatabaseConfig.PoolStats.empty())
        assertContains(
            body,
            "http_request_duration_seconds_bucket{method=\"GET\",route=\"/api/clients/search\",le=\"0.05\"} 1",
        )
        assertContains(
            body,
            "http_request_duration_seconds_bucket{method=\"GET\",route=\"/api/clients/search\",le=\"+Inf\"} 2",
        )
        assertContains(body, "http_request_duration_seconds_count{method=\"GET\",route=\"/api/clients/search\"} 2")
    }

    @Test
    fun `self scrape never moves the traffic leg`() {
        RequestMetrics.observe("GET", "/metrics", 200, 1)
        RequestMetrics.observe("GET", ApiRoutes.METRICS, 200, 1)
        assertTrue(RequestMetrics.snapshot().isEmpty())
    }

    @Test
    fun `render exposes pool gauges distinguishing leak from query slowness`() {
        val leak = DatabaseConfig.PoolStats(active = 3, idle = 0, awaiting = 7, total = 3)
        val leakBody = RequestMetrics.render(leak)
        assertContains(leakBody, "hikaricp_connections_active 3")
        assertContains(leakBody, "hikaricp_connections_pending 7")
        val querySlow = DatabaseConfig.PoolStats(active = 0, idle = 3, awaiting = 0, total = 3)
        val queryBody = RequestMetrics.render(querySlow)
        assertContains(queryBody, "hikaricp_connections_active 0")
        assertContains(queryBody, "hikaricp_connections_idle 3")
    }

    @Test
    fun `metrics endpoint serves prometheus text without auth`() {
        val app =
            Javalin.create { cfg ->
                cfg.routes.after(RequestMetrics::observe)
                cfg.routes.get("/api/clients/search") { it.result("ok") }
                cfg.routes.get("/slow") {
                    Thread.sleep(SLOW_MS)
                    it.result("ok")
                }
                com.companyb.companyapp.api.routes.MetricsRoutes
                    .register(cfg)
            }
        app.start(0)
        try {
            val client = HttpClient.newHttpClient()
            client.get(app.port(), "/api/clients/search")
            client.get(app.port(), "/slow")
            val metrics = client.get(app.port(), ApiRoutes.METRICS)
            assertEquals(200, metrics.statusCode())
            assertTrue(
                metrics
                    .headers()
                    .firstValue("Content-Type")
                    .orElse("")
                    .contains("text/plain"),
            )
            assertContains(metrics.body(), "http_requests_total{method=\"GET\",route=\"/api/clients/search\"} 1")
            assertContains(metrics.body(), "http_requests_total{method=\"GET\",route=\"/slow\"} 1")
            assertContains(metrics.body(), "hikaricp_connections_active")
        } finally {
            app.stop()
        }
    }

    @Test
    fun `handler plus after records each error exactly once`() {
        val app =
            Javalin.create { cfg ->
                cfg.routes.before { RequestElapsedConverter.startRequest() }
                cfg.routes.after {
                    TraceIdFilter.echo(it)
                    RequestMetrics.observe(it)
                    RequestLog.complete(it)
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    TraceIdFilter.echo(ctx)
                    ctx.status(HTTP_BAD_REQUEST).json(mapOf("error" to (e.message ?: "Bad Request")))
                    RequestMetrics.observe(ctx)
                    RequestLog.complete(ctx)
                }
                cfg.routes.error(HttpStatus.INTERNAL_SERVER_ERROR) { ctx ->
                    TraceIdFilter.echo(ctx)
                    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(mapOf("error" to "Internal Server Error"))
                    RequestMetrics.observe(ctx)
                    RequestLog.complete(ctx)
                }
                cfg.routes.get("/boom-4xx") {
                    Thread.sleep(ERROR_SLEEP_MS)
                    throw ValidationException("bad")
                }
                cfg.routes.get("/boom-5xx") {
                    Thread.sleep(ERROR_SLEEP_MS)
                    error("boom")
                }
                cfg.routes.get("/ok") {
                    Thread.sleep(ERROR_SLEEP_MS)
                    it.result("ok")
                }
            }
        app.start(0)
        try {
            val client = HttpClient.newHttpClient()
            assertEquals(HTTP_BAD_REQUEST, client.get(app.port(), "/boom-4xx").statusCode())
            assertEquals(HTTP_INTERNAL_ERROR, client.get(app.port(), "/boom-5xx").statusCode())
            assertEquals(HTTP_OK, client.get(app.port(), "/ok").statusCode())
            val counts = RequestMetrics.snapshot()
            assertEquals(1, counts["GET" to "/boom-4xx"])
            assertEquals(1, counts["GET" to "/boom-5xx"])
            assertEquals(1, counts["GET" to "/ok"])
            val body = RequestMetrics.render(DatabaseConfig.PoolStats.empty())
            assertContains(body, "http_request_errors_total{method=\"GET\",route=\"/boom-5xx\"} 1")
            // #501 — handler-first observe keeps real elapsed; after-only recorded 0.0 on error paths.
            assertTrue(durationSum(body, "/boom-4xx") > 0.0, "4xx latency lost")
            assertTrue(durationSum(body, "/boom-5xx") > 0.0, "5xx latency lost")
            assertTrue(durationSum(body, "/ok") > 0.0, "ok latency lost")
        } finally {
            app.stop()
        }
    }

    @Test
    fun `unknown methods collapse to one bucket`() {
        RequestMetrics.observe("BREW", "/api/branches", 200, 1)
        RequestMetrics.observe("get", "/api/branches", 200, 1)
        val body = RequestMetrics.render(DatabaseConfig.PoolStats.empty())
        assertContains(body, "http_requests_total{method=\"UNKNOWN\",route=\"/api/branches\"} 1")
        assertContains(body, "http_requests_total{method=\"GET\",route=\"/api/branches\"} 1")
    }

    @Test
    fun `thousands of distinct unmatched alphabetic paths collapse to one bucket`() {
        val app =
            Javalin.create { cfg ->
                cfg.routes.before { RequestElapsedConverter.startRequest() }
                cfg.routes.after {
                    TraceIdFilter.echo(it)
                    RequestMetrics.observe(it)
                    RequestLog.complete(it)
                }
                cfg.routes.get("/api/widgets/{id}") { it.result("ok") }
            }
        app.start(0)
        try {
            val client = HttpClient.newHttpClient()
            repeat(UNMATCHED_PROBE_COUNT) { i ->
                assertEquals(HTTP_NOT_FOUND, client.get(app.port(), "/missing/${alphaSuffix(i)}").statusCode())
            }
            val counts = RequestMetrics.snapshot()
            assertEquals(1, counts.size, "distinct 404 paths must not grow series: $counts")
            assertEquals(UNMATCHED_PROBE_COUNT.toLong(), counts["GET" to RequestMetrics.UNMATCHED_ROUTE])
            val body = RequestMetrics.render(DatabaseConfig.PoolStats.empty())
            assertTrue(body.lines().count { it.startsWith("http_requests_total{") } == 1, "one counter series")
        } finally {
            app.stop()
        }
    }

    @Test
    fun `route stack keeps template labels for parameterized and fixed routes`() {
        val app =
            Javalin.create { cfg ->
                cfg.routes.before { RequestElapsedConverter.startRequest() }
                cfg.routes.before("/api/gated/*") { throw UnauthorizedResponse() }
                cfg.routes.after {
                    TraceIdFilter.echo(it)
                    RequestMetrics.observe(it)
                    RequestLog.complete(it)
                }
                cfg.routes.get("/api/widgets/search") { it.result("ok") }
                cfg.routes.get("/api/widgets/{id}") { it.result("ok") }
                cfg.routes.get("/api/malformed/{id}") { ctx ->
                    runCatching { UUID.fromString(ctx.pathParam("id")) }
                        .getOrElse { throw BadRequestResponse("Invalid id") }
                    ctx.result("ok")
                }
            }
        app.start(0)
        try {
            val client = HttpClient.newHttpClient()
            val uuid = "123e4567-e89b-12d3-a456-426614174000"
            assertEquals(HTTP_OK, client.get(app.port(), "/api/widgets/$uuid").statusCode())
            assertEquals(HTTP_OK, client.get(app.port(), "/api/widgets/42").statusCode())
            assertEquals(HTTP_OK, client.get(app.port(), "/api/widgets/search").statusCode())
            assertEquals(HTTP_BAD_REQUEST, client.get(app.port(), "/api/malformed/not-a-uuid").statusCode())
            assertEquals(HTTP_OK, client.get(app.port(), "/api/malformed/$uuid").statusCode())
            assertEquals(HTTP_UNAUTHORIZED, client.get(app.port(), "/api/gated/door").statusCode())
            val counts = RequestMetrics.snapshot()
            assertEquals(2, counts["GET" to "/api/widgets/{id}"])
            assertEquals(1, counts["GET" to "/api/widgets/search"])
            assertEquals(2, counts["GET" to "/api/malformed/{id}"])
            assertEquals(1, counts["GET" to RequestMetrics.UNMATCHED_ROUTE])
            assertEquals(4, counts.size, "templates plus one unmatched bucket: $counts")
        } finally {
            app.stop()
        }
    }

    @Test
    fun `route stack counts errors once in bounded buckets`() {
        val app =
            Javalin.create { cfg ->
                cfg.routes.before { RequestElapsedConverter.startRequest() }
                cfg.routes.after {
                    TraceIdFilter.echo(it)
                    RequestMetrics.observe(it)
                    RequestLog.complete(it)
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    TraceIdFilter.echo(ctx)
                    ctx.status(HTTP_BAD_REQUEST).json(mapOf("error" to (e.message ?: "Bad Request")))
                    RequestMetrics.observe(ctx)
                    RequestLog.complete(ctx)
                }
                cfg.routes.error(HttpStatus.INTERNAL_SERVER_ERROR) { ctx ->
                    TraceIdFilter.echo(ctx)
                    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(mapOf("error" to "Internal Server Error"))
                    RequestMetrics.observe(ctx)
                    RequestLog.complete(ctx)
                }
                cfg.routes.get("/api/boom-400") { throw ValidationException("bad") }
                cfg.routes.get("/api/boom-500") { error("boom") }
            }
        app.start(0)
        try {
            val client = HttpClient.newHttpClient()
            assertEquals(HTTP_BAD_REQUEST, client.get(app.port(), "/api/boom-400").statusCode())
            assertEquals(HTTP_INTERNAL_ERROR, client.get(app.port(), "/api/boom-500").statusCode())
            assertEquals(HTTP_NOT_FOUND, client.get(app.port(), "/missing/alpha").statusCode())
            assertEquals(HTTP_NOT_FOUND, client.get(app.port(), "/missing/beta").statusCode())
            val counts = RequestMetrics.snapshot()
            assertEquals(1, counts["GET" to "/api/boom-400"])
            assertEquals(1, counts["GET" to "/api/boom-500"])
            assertEquals(2, counts["GET" to RequestMetrics.UNMATCHED_ROUTE])
            assertEquals(3, counts.size, "templates plus one unmatched bucket: $counts")
            val body = RequestMetrics.render(DatabaseConfig.PoolStats.empty())
            assertContains(body, "http_request_errors_total{method=\"GET\",route=\"/api/boom-500\"} 1")
        } finally {
            app.stop()
        }
    }

    @Test
    fun `recording overhead stays negligible on hot paths`() {
        val (_, duration) =
            measureTimedValue {
                repeat(RECORDING_ITERATIONS) { RequestMetrics.observe("GET", "/api/branches", 200, 1) }
            }
        assertTrue(duration.inWholeMilliseconds < RECORDING_BUDGET_MS, "5k records took $duration")
    }

    private fun alphaSuffix(index: Int): String {
        var rest = index
        val out = StringBuilder()
        do {
            out.append('a' + rest % ALPHA_BASE)
            rest /= ALPHA_BASE
        } while (rest > 0)
        return out.toString()
    }

    private fun HttpClient.get(
        port: Int,
        path: String,
    ): HttpResponse<String> =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun durationSum(
        body: String,
        route: String,
    ): Double {
        val match =
            Regex("http_request_duration_seconds_sum\\{method=\"GET\",route=\"$route\"\\} ([0-9.Ee+-]+)")
                .find(body) ?: return -1.0
        return match.groupValues[1].toDouble()
    }

    private companion object {
        const val FAST_MS = 10L
        const val SLOW_MS = 80L
        const val ERROR_SLEEP_MS = 50L
        const val RECORDING_ITERATIONS = 5000
        const val RECORDING_BUDGET_MS = 2000L
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_INTERNAL_ERROR = 500
        const val UNMATCHED_PROBE_COUNT = 2000
        const val ALPHA_BASE = 26
    }
}
