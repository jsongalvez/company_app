package com.companyb.companyapp.api.middleware

import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.exception.ValidationException
import io.javalin.Javalin
import io.javalin.http.UnauthorizedResponse
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TraceIdFilterTest {
    private fun testApp(): Javalin =
        Javalin.create { cfg ->
            cfg.routes.before(TraceIdFilter::before)
            cfg.routes.after(TraceIdFilter::echo)
            cfg.routes.before("/api/*") { ctx ->
                if (ctx.path() == "/api/gated") throw UnauthorizedResponse()
            }
            cfg.routes.get("/ok") { it.result("ok") }
            cfg.routes.get("/boom") { throw ValidationException("bad") }
            cfg.routes.exception(ValidationException::class.java) { e, ctx ->
                TraceIdFilter.echo(ctx)
                ctx.status(400).json(mapOf("error" to (e.message ?: "Bad Request")))
            }
        }

    private fun get(
        client: HttpClient,
        port: Int,
        path: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun withApp(block: (HttpClient, Int) -> Unit) {
        val app = testApp().start(0)
        try {
            block(HttpClient.newHttpClient(), app.port())
        } finally {
            app.stop()
        }
    }

    @Test
    fun `success response carries opaque trace header`() {
        withApp { client, port ->
            val response = get(client, port, "/ok")
            assertEquals(200, response.statusCode())
            val traceId = response.headers().firstValue(ApiRoutes.TRACE_ID_HEADER).orElse(null)
            assertNotNull(traceId)
            assertTrue(traceId.matches(Regex("[A-Za-z0-9]+")), "trace id must be opaque, was: $traceId")
        }
    }

    @Test
    fun `each request mints a fresh trace id`() {
        withApp { client, port ->
            val first = get(client, port, "/ok").headers().firstValue(ApiRoutes.TRACE_ID_HEADER).orElse(null)
            val second = get(client, port, "/ok").headers().firstValue(ApiRoutes.TRACE_ID_HEADER).orElse(null)
            assertNotNull(first)
            assertNotNull(second)
            assertNotEquals(first, second)
        }
    }

    @Test
    fun `error response still carries the trace header`() {
        withApp { client, port ->
            val response = get(client, port, "/boom")
            assertEquals(400, response.statusCode())
            assertTrue(response.headers().firstValue(ApiRoutes.TRACE_ID_HEADER).isPresent)
        }
    }

    @Test
    fun `auth-filter rejection still carries the trace header`() {
        withApp { client, port ->
            val response = get(client, port, "/api/gated")
            assertEquals(401, response.statusCode())
            assertTrue(response.headers().firstValue(ApiRoutes.TRACE_ID_HEADER).isPresent)
        }
    }
}
