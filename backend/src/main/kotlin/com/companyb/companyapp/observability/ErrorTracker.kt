package com.companyb.companyapp.observability

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** What the 500 handler knows about a failed request. The exception is kept for its class and frames only. */
internal data class ServerErrorReport(
    val traceId: String,
    val method: String,
    val route: String,
    val status: Int,
    val error: Throwable?,
)

/** One HTTP POST to the tracker; a test replaces it to see what would be sent. */
internal fun interface ErrorTrackerTransport {
    fun post(
        url: String,
        authHeader: String,
        body: String,
    )
}

/**
 * Sends unexpected server errors to Sentry (or GlitchTip) when [ErrorTrackerConfig] is set; does nothing
 * otherwise. An event carries the trace id (the same id as the request log and the `X-Trace-Id` header),
 * method, normalized route, status and the exception's class and stack frames. It never carries the
 * exception message, request body, query string, user or client data: messages from SQL and parsing errors
 * can quote values. Delivery is async and bounded; a failure is logged and never breaks the request.
 */
internal object ErrorTracker {
    private val logger = KotlinLogging.logger {}
    private const val QUEUE_SIZE = 50
    private const val MAX_FRAMES = 30
    private const val TIMEOUT_SECONDS = 3L
    private const val SENTRY_VERSION = 7
    private const val HTTP_REDIRECT = 300

    @Volatile private var config: ErrorTrackerConfig? = null

    @Volatile private var transport: ErrorTrackerTransport = httpTransport()

    private var executor: ThreadPoolExecutor? = null

    @Synchronized
    fun configure(
        newConfig: ErrorTrackerConfig?,
        newTransport: ErrorTrackerTransport? = null,
    ) {
        executor?.shutdownNow()
        config = newConfig
        transport = newTransport ?: httpTransport()
        executor = newConfig?.let { newExecutor() }
        if (newConfig ==
            null
        ) {
            logger.info { "[ERROR-TRACKER] Not configured (SENTRY_DSN unset); errors stay in the server log." }
        }
    }

    @Synchronized
    fun shutdown() {
        executor?.shutdown()
        executor?.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        executor = null
        config = null
    }

    fun captureServerError(report: ServerErrorReport) {
        val cfg = config ?: return
        val body = envelope(event(cfg, report))
        val auth = "Sentry sentry_version=$SENTRY_VERSION, sentry_client=company-app/1, sentry_key=${cfg.publicKey}"
        try {
            executor?.execute {
                runCatching { transport.post(cfg.envelopeUrl, auth, body) }
                    .onFailure { logger.warn(it) { "[ERROR-TRACKER] Delivery failed (trace ${report.traceId})" } }
            }
        } catch (rejected: RejectedExecutionException) {
            logger.warn(rejected) { "[ERROR-TRACKER] Queue full or stopped; event dropped (trace ${report.traceId})" }
        }
    }

    internal fun event(
        cfg: ErrorTrackerConfig,
        report: ServerErrorReport,
    ): JsonObject {
        val (traceId, method, route, status, error) = report
        return buildJsonObject {
            put("event_id", UUID.randomUUID().toString().replace("-", ""))
            put("timestamp", Instant.now().toString())
            put("platform", "java")
            put("level", "error")
            put("environment", cfg.environment)
            put("message", "$method $route -> $status")
            putJsonObject("tags") {
                put("trace_id", traceId)
                put("method", method)
                put("route", route)
                put("status", status.toString())
            }
            if (error != null) {
                putJsonObject("exception") {
                    putJsonArray("values") {
                        add(
                            buildJsonObject {
                                put("type", error::class.qualifiedName ?: "Throwable")
                                putJsonObject("stacktrace") {
                                    putJsonArray("frames") {
                                        // Sentry wants the oldest frame first.
                                        error.stackTrace.take(MAX_FRAMES).reversed().forEach { f ->
                                            add(
                                                buildJsonObject {
                                                    put("module", f.className)
                                                    put("function", f.methodName)
                                                    put("filename", f.fileName?.let { JsonPrimitive(it) } ?: JsonNull)
                                                    put(
                                                        "lineno",
                                                        f.lineNumber.takeIf { it > 0 }?.let { JsonPrimitive(it) }
                                                            ?: JsonNull,
                                                    )
                                                },
                                            )
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    private fun envelope(event: JsonObject): String {
        val header =
            JsonObject(
                mapOf(
                    "event_id" to event.getValue("event_id"),
                    "sent_at" to JsonPrimitive(Instant.now().toString()),
                ),
            )
        val item = JsonObject(mapOf("type" to JsonPrimitive("event")))
        return listOf(header, item, event).joinToString("\n")
    }

    private fun newExecutor(): ThreadPoolExecutor =
        ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUE_SIZE)) { r ->
            Thread(r, "error-tracker").apply { isDaemon = true }
        }

    private fun httpTransport(): ErrorTrackerTransport {
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS)).build()
        return ErrorTrackerTransport { url, auth, body ->
            val request =
                HttpRequest
                    .newBuilder(URI(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("X-Sentry-Auth", auth)
                    .header("Content-Type", "application/x-sentry-envelope")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.discarding())
            check(response.statusCode() < HTTP_REDIRECT) { "tracker answered ${response.statusCode()}" }
        }
    }
}
