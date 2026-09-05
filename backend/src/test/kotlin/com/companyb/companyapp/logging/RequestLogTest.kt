package com.companyb.companyapp.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.companyb.companyapp.api.middleware.TraceIdFilter
import com.companyb.companyapp.exception.ValidationException
import io.javalin.Javalin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RequestLogTest {
    private fun requestEvents(): List<ILoggingEvent> {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        root.addAppender(appender)
        try {
            val app =
                Javalin.create { cfg ->
                    cfg.routes.before {
                        RequestElapsedConverter.startRequest()
                        DeltaTimeConverter.startRequest()
                        TraceIdFilter.before(it)
                        RequestLog.start(it)
                    }
                    cfg.routes.after {
                        TraceIdFilter.echo(it)
                        RequestLog.complete(it)
                    }
                    cfg.routes.get("/api/clients/search") { it.result("ok") }
                    cfg.routes.get("/slow") {
                        Thread.sleep(SLOW_ENDPOINT_MS)
                        it.result("ok")
                    }
                    cfg.routes.get("/boom") { throw ValidationException("bad") }
                    cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                        TraceIdFilter.echo(ctx)
                        ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
                        RequestLog.complete(ctx)
                    }
                }
            app.start(0)
            try {
                val client = HttpClient.newHttpClient()
                client.get(app.port(), "/api/clients/search?query=jo")
                client.get(app.port(), "/slow")
                client.get(app.port(), "/boom")
            } finally {
                app.stop()
            }
        } finally {
            root.detachAppender(appender)
        }
        return appender.list.filter { event ->
            runCatching {
                Json
                    .parseToJsonElement(
                        event.formattedMessage,
                    ).jsonObject
                    .containsKey("event")
            }.getOrDefault(false)
        }
    }

    private fun HttpClient.get(
        port: Int,
        path: String,
    ): HttpResponse<String> =
        send(
            java.net.http.HttpRequest
                .newBuilder(URI.create("http://localhost:$port$path"))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    @Test
    fun `completed line carries machine-readable trace route status elapsed`() {
        val completed =
            requestEvents()
                .map { Json.parseToJsonElement(it.formattedMessage).jsonObject }
                .filter { it["event"]!!.jsonPrimitive.content == "request_completed" }
        assertEquals(3, completed.size)
        val search = completed.single { it["route"]!!.jsonPrimitive.content == "/api/clients/search" }
        assertEquals("GET", search["method"]!!.jsonPrimitive.content)
        assertEquals(200, search["status"]!!.jsonPrimitive.int)
        assertTrue(search["traceId"]!!.jsonPrimitive.content.isNotBlank())
        assertTrue(search["elapsedMs"]!!.jsonPrimitive.long >= 0)
    }

    @Test
    fun `slow request is findable by route plus minimum elapsed without regex`() {
        val completed =
            requestEvents()
                .map { Json.parseToJsonElement(it.formattedMessage).jsonObject }
                .filter { it["event"]!!.jsonPrimitive.content == "request_completed" }
        val slow =
            completed.filter {
                it["route"]!!.jsonPrimitive.content == "/slow" &&
                    it["elapsedMs"]!!.jsonPrimitive.long >= SLOW_ENDPOINT_MS
            }
        assertEquals(1, slow.size)
    }

    @Test
    fun `error path still logs exactly one completed line with the error status`() {
        val completed =
            requestEvents()
                .map { Json.parseToJsonElement(it.formattedMessage).jsonObject }
                .filter { it["event"]!!.jsonPrimitive.content == "request_completed" }
        val booms = completed.filter { it["route"]!!.jsonPrimitive.content == "/boom" }
        assertEquals(1, booms.size)
        assertEquals(400, booms.single()["status"]!!.jsonPrimitive.int)
    }

    @Test
    fun `format helpers emit no query string`() {
        val started = Json.parseToJsonElement(RequestLog.formatStart("t", "GET", "/api/clients/search")).jsonObject
        assertEquals("/api/clients/search", started["route"]!!.jsonPrimitive.content)
        assertTrue(started.keys.none { it.contains("query", ignoreCase = true) })
    }

    private companion object {
        const val SLOW_ENDPOINT_MS = 60L
    }
}
