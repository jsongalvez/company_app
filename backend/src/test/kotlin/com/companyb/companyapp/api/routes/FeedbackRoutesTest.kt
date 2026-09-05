package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.middleware.TraceIdFilter
import com.companyb.companyapp.auth.RateLimiter
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.dto.FeedbackResponse
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.observability.IncidentRegistry
import com.companyb.companyapp.test.JavalinTestServerRule
import io.javalin.Javalin
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #475 — feedback endpoint over HTTP: full packet shape, validation 400s,
 * trace-id dedup collapse, id scrubbing, and the shared per-user/IP budget.
 */
class FeedbackRoutesTest {
    @BeforeTest
    fun reset() {
        IncidentRegistry.resetForTest()
        RateLimiter.resetForTest()
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    private fun reportBody(
        trace: String,
        endpoint: String = "GET /api/clients",
        appVersion: String = "9",
        elapsedMs: Long = 1200,
    ) = mapOf(
        "traceId" to trace,
        "endpoint" to endpoint,
        "appVersion" to appVersion,
        "elapsedMs" to elapsedMs,
    )

    @Test
    fun `report files a complete packet`() {
        val user = UUID.randomUUID()
        val trace = "trace-${UUID.randomUUID()}"
        val response = testServer.client.post("/api/feedback", reportBody(trace), asUser(user))

        assertEquals(200, response.code)
        val filed = json.decodeFromString<FeedbackResponse>(response.body.string())
        assertFalse(filed.duplicate)
        assertEquals(trace, filed.packet.traceId)
        assertEquals("GET", filed.packet.method)
        assertEquals("/api/clients", filed.packet.route)
        assertEquals("9", filed.packet.appVersion)
        assertEquals(1200, filed.packet.elapsedMs)
        assertTrue(filed.packet.timestamp.isNotBlank())
        assertFalse(filed.packet.reporter.contains(user.toString()))
    }

    @Test
    fun `blank trace is a 400`() {
        val response =
            testServer.client.post("/api/feedback", reportBody("  "), asUser(UUID.randomUUID()))

        assertEquals(400, response.code)
        val error = Json.parseToJsonElement(response.body.string()).jsonObject["error"]
        assertTrue(error.toString().contains("traceId"))
    }

    @Test
    fun `repeat trace collapses to one packet`() {
        val user = UUID.randomUUID()
        val trace = "trace-${UUID.randomUUID()}"
        val first = testServer.client.post("/api/feedback", reportBody(trace), asUser(user))
        val second = testServer.client.post("/api/feedback", reportBody(trace), asUser(user))

        assertEquals(200, first.code)
        assertEquals(200, second.code)
        val firstFiled = json.decodeFromString<FeedbackResponse>(first.body.string())
        val secondFiled = json.decodeFromString<FeedbackResponse>(second.body.string())
        assertFalse(firstFiled.duplicate)
        assertTrue(secondFiled.duplicate)
        assertEquals(firstFiled.packet, secondFiled.packet)
    }

    @Test
    fun `raw ids never reach the packet`() {
        val user = UUID.randomUUID()
        val rawId = UUID.randomUUID()
        val response =
            testServer.client.post(
                "/api/feedback",
                reportBody("trace-${UUID.randomUUID()}", "GET /api/sessions/$rawId?date=2026-01-01"),
                asUser(user),
            )

        assertEquals(200, response.code)
        val body = response.body.string()
        assertFalse(body.contains(rawId.toString()))
        assertTrue(body.contains("/api/sessions/{id}"))
    }

    @Test
    fun `hammer trips the shared budget`() {
        val user = UUID.randomUUID()
        val codes =
            (1..11).map { index ->
                testServer.client
                    .post(
                        "/api/feedback",
                        reportBody("hammer-$index-${UUID.randomUUID()}"),
                        asUser(user),
                    ).code
            }

        assertEquals(List(10) { 200 }, codes.take(10))
        assertEquals(429, codes.last())
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        private val defaultUser = UUID.randomUUID()

        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin =
            Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    TraceIdFilter.before(ctx)
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: defaultUser.toString())
                }
                cfg.routes.after { ctx ->
                    TraceIdFilter.echo(ctx)
                }
                cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                    ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
                }
                FeedbackRoutes.register(cfg)
            }
    }
}
