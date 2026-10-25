package com.companyb.companyapp.observability

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.middleware.TraceIdFilter
import com.companyb.companyapp.http.KotlinxSerializationMapper
import com.companyb.companyapp.registerServerErrorHandler
import com.companyb.companyapp.test.JavalinTestServerRule
import io.javalin.Javalin
import io.javalin.http.BadRequestResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.ClassRule
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private data class Sent(
    val url: String,
    val auth: String,
    val body: String,
)

class ErrorTrackerTest {
    private val sent = CopyOnWriteArrayList<Sent>()

    @BeforeTest
    fun fakeTracker() {
        ErrorTracker.configure(
            ErrorTrackerConfig.fromEnvironment(mapOf(ErrorTrackerConfig.DSN_ENV to "https://abc123@tracker.example/42")),
        ) { url, auth, body -> sent += Sent(url, auth, body) }
    }

    @AfterTest
    fun noTracker() = ErrorTracker.configure(null)

    private fun awaitSent(count: Int) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (sent.size < count && System.nanoTime() < end) Thread.sleep(10)
    }

    @Test
    fun `a forced 500 sends one event with the trace id and no message`() {
        val response = testServer.client.get("/boom")

        assertEquals(500, response.code)
        val traceId = response.headers().get(ApiRoutes.TRACE_ID_HEADER)?.firstOrNull()
        assertNotNull(traceId)
        awaitSent(1)
        assertEquals(1, sent.size)
        val one = sent.single()
        assertEquals("https://tracker.example/api/42/envelope/", one.url)
        assertTrue("sentry_key=abc123" in one.auth)
        val event = Json.parseToJsonElement(one.body.lines().last()).jsonObject
        assertEquals(traceId, event["tags"]!!.jsonObject["trace_id"]!!.jsonPrimitive.content)
        assertEquals("GET", event["tags"]!!.jsonObject["method"]!!.jsonPrimitive.content)
        assertEquals("/boom", event["tags"]!!.jsonObject["route"]!!.jsonPrimitive.content)
        val error = event["exception"]!!.jsonObject["values"]!!.jsonArray.single().jsonObject
        assertEquals("java.lang.IllegalStateException", error["type"]!!.jsonPrimitive.content)
        assertTrue(error["stacktrace"]!!.jsonObject["frames"]!!.jsonArray.isNotEmpty())
        assertFalse("kaboom" in one.body, "exception message (may quote client data) must not leave the server")
    }

    @Test
    fun `a refusal is not sent and keeps its status`() {
        val response = testServer.client.get("/refuse")

        assertEquals(400, response.code)
        Thread.sleep(200)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `with no tracker configured nothing is sent and the 500 is the same`() {
        ErrorTracker.configure(null)

        val response = testServer.client.get("/boom")

        assertEquals(500, response.code)
        assertTrue(response.body.string().contains("Internal Server Error"))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `dsn parsing accepts a project dsn and rejects the rest`() {
        fun parse(dsn: String?) = ErrorTrackerConfig.fromEnvironment(mapOf(ErrorTrackerConfig.DSN_ENV to dsn))
        assertEquals("https://o1.example:8443/api/7/envelope/", parse("https://k@o1.example:8443/7")?.envelopeUrl)
        assertEquals("production", parse("https://k@o1.example/7")?.environment)
        assertNull(parse(null))
        assertNull(parse(""))
        assertNull(parse("not a dsn"))
        assertNull(parse("https://o1.example/7"))
        assertNull(parse("ftp://k@o1.example/7"))
        assertNull(parse("https://k@o1.example/"))
    }

    companion object {
        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin =
            Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx -> TraceIdFilter.before(ctx) }
                cfg.routes.after { ctx -> TraceIdFilter.echo(ctx) }
                cfg.routes.get("/boom") { throw IllegalStateException("kaboom client Maria Santos") }
                cfg.routes.get("/refuse") { throw BadRequestResponse("nope") }
                registerServerErrorHandler(cfg)
            }
    }
}
