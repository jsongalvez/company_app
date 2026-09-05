package com.companyb.companyapp.observability

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.middleware.TraceIdFilter
import com.companyb.companyapp.database.DatabaseConfig
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.logging.RequestLog
import io.javalin.Javalin
import io.javalin.http.HttpStatus
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
                cfg.routes.get("/boom-4xx") { throw ValidationException("bad") }
                cfg.routes.get("/boom-5xx") { throw IllegalStateException("boom") }
                cfg.routes.get("/ok") { it.result("ok") }
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

    private fun HttpClient.get(
        port: Int,
        path: String,
    ): HttpResponse<String> =
        send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private companion object {
        const val FAST_MS = 10L
        const val SLOW_MS = 80L
        const val RECORDING_ITERATIONS = 5000
        const val RECORDING_BUDGET_MS = 2000L
        const val HTTP_OK = 200
        const val HTTP_BAD_REQUEST = 400
        const val HTTP_INTERNAL_ERROR = 500
    }
}
