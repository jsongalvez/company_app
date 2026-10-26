package com.companyb.companyapp.logging

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.companyb.companyapp.api.middleware.TraceIdFilter
import com.companyb.companyapp.database.JdbcTiming
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import com.companyb.companyapp.testsupport.database.TestDatabaseLifecycle
import io.javalin.Javalin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.content

private fun JsonObject.num(key: String) = this[key]?.jsonPrimitive?.long

class RequestTimingTest : BasePostgresTest() {
    override fun initTestData() = Unit

    private class Run(
        val responses: Map<String, HttpResponse<String>>,
        val events: List<JsonObject>,
    ) {
        fun completed(template: String) =
            events.filter {
                it.str("event") == "request_completed" &&
                    it.str("template") == template
            }

        fun slow() = events.filter { it.str("event") == "slow_statement" }
    }

    private fun timed(
        paths: List<String>,
        parallel: Boolean = false,
    ): Run {
        val responses = linkedMapOf<String, HttpResponse<String>>()
        val events =
            captureEvents {
                val source = JdbcTiming.wrap(TestDatabaseLifecycle.requireTestDataSource())
                val app = timedApp(source, Database.connect(source)).start(0)
                try {
                    responses += fetch(app.port(), paths, parallel)
                } finally {
                    app.stop()
                }
            }
        return Run(responses, events)
    }

    private fun captureEvents(block: () -> Unit): List<JsonObject> {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        root.addAppender(appender)
        try {
            block()
        } finally {
            root.detachAppender(appender)
        }
        return appender.list.mapNotNull { event ->
            runCatching { Json.parseToJsonElement(event.formattedMessage).jsonObject }
                .getOrNull()
                ?.takeIf { it.containsKey("event") }
        }
    }

    private fun fetch(
        port: Int,
        paths: List<String>,
        parallel: Boolean,
    ): Map<String, HttpResponse<String>> {
        val client = HttpClient.newHttpClient()
        val request = { path: String ->
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build()
        }
        if (!parallel) return paths.associateWith { client.send(request(it), HttpResponse.BodyHandlers.ofString()) }
        val futures = paths.associateWith { client.sendAsync(request(it), HttpResponse.BodyHandlers.ofString()) }
        CompletableFuture.allOf(*futures.values.toTypedArray()).join()
        return futures.mapValues { it.value.join() }
    }

    private fun timedApp(
        source: DataSource,
        db: Database,
    ): Javalin =
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
            cfg.routes.get("/api/sleepy/{id}") {
                transaction(db) { exec("SELECT pg_sleep(0.3)") }
                it.result("ok")
            }
            cfg.routes.get("/api/quiet/{id}") { it.result("ok") }
            cfg.routes.get("/api/bound") {
                source.connection.use { c ->
                    c.prepareStatement("SELECT pg_sleep(0.3) WHERE ? = ?").use { st ->
                        st.setString(1, "Maria Santos")
                        st.setString(2, "Maria Santos")
                        st.execute()
                    }
                }
                it.result("ok")
            }
            cfg.routes.get("/api/literal") {
                source.connection.use { c ->
                    c.createStatement().use { it.execute("SELECT pg_sleep(0.3) WHERE 'Maria Santos' = 'Maria Santos'") }
                }
                it.result("ok")
            }
            cfg.routes.get("/api/boom") {
                transaction(db) { exec("SELECT 1") }
                error("boom")
            }
            cfg.routes.exception(IllegalStateException::class.java) { _, ctx ->
                TraceIdFilter.echo(ctx)
                ctx.status(500).result("boom")
                RequestLog.complete(ctx)
            }
        }

    @Test
    fun `a request that sleeps in the database logs its database time and one query`() {
        val run = timed(listOf("/api/sleepy/1"))
        val line = run.completed("/api/sleepy/{id}").single()
        assertTrue(line.num("dbMs")!! >= 300, "dbMs ${line.num("dbMs")}")
        assertEquals(1, line.num("queries"))
        assertTrue(line.num("elapsedMs")!! >= line.num("dbMs")!!)
        val header =
            run.responses
                .getValue("/api/sleepy/1")
                .headers()
                .firstValue("Server-Timing")
                .orElse("")
        val db =
            Regex("""db;dur=(\d+)""")
                .find(header)
                ?.groupValues
                ?.get(1)
                ?.toLong()
        assertTrue(db != null && db >= 300, "Server-Timing: $header")
        assertTrue(Regex("""app;dur=\d+""").containsMatchIn(header), "Server-Timing: $header")
    }

    @Test
    fun `a route with no query logs zero database time and zero queries`() {
        val line = timed(listOf("/api/quiet/1")).completed("/api/quiet/{id}").single()
        assertEquals(0, line.num("dbMs"))
        assertEquals(0, line.num("queries"))
    }

    @Test
    fun `template is the route pattern, with no raw id`() {
        val run = timed(listOf("/api/quiet/7f9a9582", "/api/nowhere/7f9a9582"))
        assertEquals(
            "/api/quiet/7f9a9582",
            run.events
                .single {
                    it.str("route") == "/api/quiet/7f9a9582" &&
                        it.str("event") == "request_completed"
                }.str("route"),
        )
        assertEquals(
            "/api/quiet/{id}",
            run.events
                .single {
                    it.str("route") == "/api/quiet/7f9a9582" &&
                        it.str("event") == "request_completed"
                }.str("template"),
        )
        assertEquals(
            "UNMATCHED",
            run.events
                .single {
                    it.str("route") == "/api/nowhere/7f9a9582" &&
                        it.str("event") == "request_completed"
                }.str("template"),
        )
    }

    @Test
    fun `a statement over 200ms logs slow_statement with the request trace id`() {
        val run = timed(listOf("/api/sleepy/1"))
        val completed = run.completed("/api/sleepy/{id}").single()
        val slow = run.slow().single()
        assertEquals(completed.str("traceId"), slow.str("traceId"))
        assertTrue(slow.str("sql")!!.contains("pg_sleep"))
        assertTrue(slow.num("ms")!! >= 300)
    }

    @Test
    fun `slow_statement never holds a bound value or a quoted literal`() {
        val run = timed(listOf("/api/bound", "/api/literal"))
        assertEquals(2, run.slow().size)
        assertFalse(run.slow().any { it.toString().contains("Maria") }, run.slow().toString())
    }

    @Test
    fun `a failing request still logs its database time and queries once`() {
        val run = timed(listOf("/api/boom"))
        val lines = run.completed("/api/boom")
        assertEquals(1, lines.size)
        assertEquals(500, lines.single().num("status"))
        assertEquals(1, lines.single().num("queries"))
        assertTrue(
            run.responses
                .getValue("/api/boom")
                .headers()
                .firstValue("Server-Timing")
                .isPresent,
        )
    }

    @Test
    fun `parallel requests keep their own counters`() {
        val paths = (1..12).flatMap { listOf("/api/sleepy/$it", "/api/quiet/$it") }
        val run = timed(paths, parallel = true)
        val sleepy = run.completed("/api/sleepy/{id}")
        val quiet = run.completed("/api/quiet/{id}")
        assertEquals(12, sleepy.size)
        assertEquals(12, quiet.size)
        assertTrue(quiet.all { it.num("dbMs") == 0L && it.num("queries") == 0L }, quiet.toString())
        assertTrue(
            sleepy.all { it.num("queries") == 1L && it.num("dbMs")!! <= it.num("elapsedMs")!! },
            sleepy.toString(),
        )
    }
}
