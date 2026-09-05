package com.companyb.companyapp.api.routes

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.api.middleware.TraceIdFilter
import com.companyb.companyapp.config.KotlinxSerializationMapper
import com.companyb.companyapp.domain.IncidentSource
import com.companyb.companyapp.logging.RequestElapsedConverter
import com.companyb.companyapp.observability.Auto5xxReport
import com.companyb.companyapp.observability.IncidentRegistry
import com.companyb.companyapp.observability.IncidentService
import com.companyb.companyapp.test.JavalinTestServerRule
import io.javalin.Javalin
import io.javalin.http.HttpStatus
import io.javalin.testtools.Request
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.ClassRule
import java.util.UUID
import java.util.function.Consumer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * #475 — 5xx auto-file over HTTP. The error-status handler mirrors
 * `Main.registerServerErrorHandler`: an unhandled throw still echoes the
 * trace id, answers generic 500 JSON (no leak), and files the same packet
 * shape as a user report without user action.
 */
class IncidentAutoFileTest {
    @BeforeTest
    fun reset() {
        IncidentRegistry.resetForTest()
    }

    private fun asUser(user: UUID): Consumer<Request.Builder> = Consumer { it.header("X-Test-User", user.toString()) }

    @Test
    fun `unhandled throw files an auto packet with the echoed trace`() {
        val response = testServer.client.get("/boom", asUser(UUID.randomUUID()))

        assertEquals(500, response.code)
        val traceId = response.headers().get(ApiRoutes.TRACE_ID_HEADER)?.firstOrNull()
        assertNotNull(traceId)
        val body = Json.parseToJsonElement(response.body.string()).jsonObject
        assertEquals("Internal Server Error", body["error"]!!.jsonPrimitive.content)
        assertFalse(body.toString().contains("kaboom"))

        val packet = IncidentRegistry.find(traceId)
        assertNotNull(packet)
        assertEquals(IncidentSource.AUTO_5XX, packet.source)
        assertEquals("GET", packet.method)
        assertEquals("/boom", packet.route)
        assertEquals(500, packet.status)
        assertTrue(packet.timestamp.isNotBlank())
    }

    @Test
    fun `auto packet masks the caller and normalizes ids`() {
        val user = UUID.randomUUID()
        val rawId = UUID.randomUUID()
        val response = testServer.client.get("/api/things/$rawId/boom", asUser(user))

        assertEquals(500, response.code)
        val traceId = response.headers().get(ApiRoutes.TRACE_ID_HEADER)?.firstOrNull()
        assertNotNull(traceId)
        val packet = IncidentRegistry.find(traceId)
        assertNotNull(packet)
        assertEquals("/api/things/{id}/boom", packet.route)
        assertFalse(packet.reporter.contains(user.toString()))
        assertTrue(packet.reporter.contains("****"))
    }

    companion object {
        private const val HTTP_INTERNAL_ERROR = 500
        private val defaultUser = UUID.randomUUID()

        @JvmField
        @ClassRule
        val testServer = JavalinTestServerRule(::createApp)

        private fun createApp(): Javalin =
            Javalin.create { cfg ->
                cfg.jsonMapper(KotlinxSerializationMapper())
                cfg.routes.before { ctx ->
                    RequestElapsedConverter.startRequest()
                    TraceIdFilter.before(ctx)
                    ctx.attribute("userId", ctx.header("X-Test-User") ?: defaultUser.toString())
                }
                cfg.routes.after { ctx ->
                    TraceIdFilter.echo(ctx)
                }
                cfg.routes.get("/boom") { throw IllegalStateException("kaboom") }
                cfg.routes.get("/api/things/{id}/boom") { throw IllegalStateException("kaboom") }
                cfg.routes.error(HttpStatus.INTERNAL_SERVER_ERROR) { ctx ->
                    TraceIdFilter.echo(ctx)
                    IncidentService.fileAuto5xx(
                        Auto5xxReport(
                            traceId = ctx.attribute<String>(TraceIdFilter.ATTRIBUTE) ?: "unknown",
                            method = ctx.method().name,
                            route = ctx.path(),
                            status = runCatching { ctx.statusCode() }.getOrDefault(HTTP_INTERNAL_ERROR),
                            elapsedMs = RequestElapsedConverter.currentElapsedMs(),
                            reporterRaw = ctx.attribute<String>("userId"),
                        ),
                    )
                    ctx.status(HttpStatus.INTERNAL_SERVER_ERROR).json(mapOf("error" to "Internal Server Error"))
                }
            }
    }
}
