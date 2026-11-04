package com.companyb.companyapp.observability

import com.companyb.companyapp.api.middleware.TraceIdFilter
import com.companyb.companyapp.logging.DeltaTimeConverter
import com.companyb.companyapp.logging.RequestElapsedConverter
import com.companyb.companyapp.logging.RequestLog
import com.companyb.companyapp.testsupport.database.BasePostgresTest
import io.javalin.Javalin
import io.javalin.testtools.JavalinTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TracingPostgresTest : BasePostgresTest() {
    override fun initTestData() = Unit

    @BeforeTest
    fun startTracing() = Tracing.start(PostgresSpanExporter())

    @AfterTest
    fun stopTracing() = Tracing.stop()

    private val user = "0b6e2a1c-5d4f-4e3a-9b8c-7d6e5f4a3b2c"

    private fun app(): Javalin =
        Javalin.create { cfg ->
            cfg.routes.before {
                RequestElapsedConverter.startRequest()
                DeltaTimeConverter.startRequest()
                TraceIdFilter.before(it)
                it.attribute("userId", user)
            }
            cfg.routes.after {
                TraceIdFilter.echo(it)
                RequestLog.complete(it)
            }
            cfg.routes.get("/api/branches/{branchId}/thing") { ctx ->
                ctx.result(readCount().toString())
            }
            cfg.routes.get("/boom") { error("planted") }
            cfg.routes.exception(Exception::class.java) { e, ctx ->
                ctx.attribute(RequestLog.SERVER_ERROR_ATTRIBUTE, e)
                ctx.status(500)
                RequestLog.complete(ctx)
            }
        }

    private fun readCount(): Long =
        traced("TracingPostgresTest.readCount") {
            transaction { TraceSpanTable.selectAll().count() }
        }

    private fun spans(traceId: String) =
        transaction {
            TraceSpanTable.selectAll().where { TraceSpanTable.traceId eq traceId }.map {
                Triple(it[TraceSpanTable.name], it[TraceSpanTable.parentId], it[TraceSpanTable.attrs]) to
                    Pair(it[TraceSpanTable.spanId], it[TraceSpanTable.error])
            }
        }

    @Test
    fun `a request continues the caller's trace and stores its wide event and child span`() {
        val trace = "4bf92f3577b34da6a3ce929d0e0e4736"
        JavalinTest.test(app()) { _, client ->
            val res =
                client.get("/api/branches/b-1/thing?name=Maria+Santos") {
                    it.header("traceparent", "00-$trace-00f067aa0ba902b7-01")
                }
            assertEquals(trace, res.headers().get("X-Trace-Id")?.firstOrNull())
        }
        Tracing.flush()
        val rows = spans(trace)
        val root = rows.single { it.first.first == "GET /api/branches/{branchId}/thing" }
        assertEquals("00f067aa0ba902b7", root.first.second, "root's parent is the caller's span")
        val attrs = Json.parseToJsonElement(root.first.third).jsonObject
        assertEquals("200", attrs.getValue("http.response.status_code").jsonPrimitive.content)
        assertEquals("ok", attrs.getValue("outcome").jsonPrimitive.content)
        assertEquals("b-1", attrs.getValue("branch").jsonPrimitive.content)
        assertEquals("0b6e2a1c-****-****-****-7d6e5f4a3b2c", attrs.getValue("user").jsonPrimitive.content)
        assertFalse(root.first.third.contains("Maria"), "no query string in the event")
        assertTrue("db.statements" in attrs && "db.ms" in attrs && "elapsed.ms" in attrs)
        val child = rows.single { it.first.first == "TracingPostgresTest.readCount" }
        assertEquals(root.second.first, child.first.second, "function span is the root's child")
    }

    @Test
    fun `a 500 stores its error class and our frames but not the message`() {
        lateinit var trace: String
        JavalinTest.test(app()) { _, client ->
            trace =
                client
                    .get("/boom")
                    .headers()
                    .get("X-Trace-Id")!!
                    .first()
        }
        Tracing.flush()
        val root = spans(trace).single()
        assertNull(root.first.second, "no caller trace: a new root")
        assertTrue(root.second.second, "marked as an error")
        val attrs = Json.parseToJsonElement(root.first.third).jsonObject
        assertEquals("server_error", attrs.getValue("outcome").jsonPrimitive.content)
        assertEquals("java.lang.IllegalStateException", attrs.getValue("error.type").jsonPrimitive.content)
        assertTrue(
            attrs
                .getValue("error.at")
                .jsonPrimitive.content
                .startsWith("TracingPostgresTest.kt:"),
        )
        assertFalse(root.first.third.contains("planted"), "no exception message")
    }
}
