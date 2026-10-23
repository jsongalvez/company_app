package com.companyb.companyapp.api.middleware

import io.javalin.Javalin
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals

/** Found by the fuzz tester (tools/fuzz): U+0000 in a string reached Postgres and came back as a 500. */
class NulCharFilterTest {
    private fun withApp(block: (HttpClient, Int) -> Unit) {
        val app =
            Javalin
                .create { cfg ->
                    cfg.routes.before(NulCharFilter::before)
                    cfg.routes.post("/echo") { it.result("ok") }
                    cfg.routes.get("/echo") { it.result("ok") }
                }.start(0)
        try {
            block(HttpClient.newHttpClient(), app.port())
        } finally {
            app.stop()
        }
    }

    private fun post(
        client: HttpClient,
        port: Int,
        body: String,
    ): Int =
        client
            .send(
                HttpRequest
                    .newBuilder(URI.create("http://localhost:$port/echo"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            ).statusCode()

    @Test
    fun `json body with an escaped NUL is rejected`() =
        withApp { client, port -> assertEquals(400, post(client, port, """{"reason":"a\u0000b"}""")) }

    @Test
    fun `json body with an escaped backslash followed by u0000 text is accepted`() =
        withApp { client, port -> assertEquals(200, post(client, port, """{"reason":"a\\u0000b"}""")) }

    @Test
    fun `ordinary json body passes`() =
        withApp { client, port -> assertEquals(200, post(client, port, """{"reason":"fine"}""")) }

    @Test
    fun `query string with a percent-encoded NUL is rejected`() =
        withApp { client, port ->
            val status =
                client
                    .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:$port/echo?q=a%00b")).GET().build(),
                        HttpResponse.BodyHandlers.ofString(),
                    ).statusCode()
            assertEquals(400, status)
        }
}
