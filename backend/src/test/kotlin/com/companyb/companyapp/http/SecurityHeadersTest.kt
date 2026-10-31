package com.companyb.companyapp.http

import com.companyb.companyapp.app.AppConfig
import com.companyb.companyapp.configureHttpSurface
import io.javalin.Javalin
import io.javalin.testtools.JavalinTest
import io.javalin.testtools.Response
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a browser gets before any route runs: headers that limit a script that slipped in, and no API map. */
class SecurityHeadersTest {
    private val themeScript = "\n  document.documentElement.dataset.theme = 'dark';\n"
    private val snapScript = "\n  (function () { document.write('<div id=\"snap\"></div>'); })();\n"

    private fun webDir(): String {
        val dir = Files.createTempDirectory("web")
        Files.createDirectories(dir.resolve("assets"))
        Files.writeString(
            dir.resolve("index.html"),
            "<!doctype html><html><head><script>$themeScript</script>" +
                "<script type=\"module\" crossorigin src=\"/assets/index-x.js\"></script>" +
                "<script data-snap>$snapScript</script></head><body><div id=\"app\"></div></body></html>",
        )
        return dir.toString()
    }

    private fun config(
        apiDocs: Boolean,
        dsn: String? = null,
    ) = AppConfig(
        appPort = 0,
        dbHost = "",
        dbPort = "",
        dbName = "",
        dbUser = "",
        dbPassword = "",
        jwtSecret = "x".repeat(64),
        jwtIssuer = "i",
        jwtAudience = "a",
        authDummyPassword = "d",
        errorTracker =
            dsn?.let {
                com.companyb.companyapp.observability.ErrorTrackerConfig
                    .fromEnvironment(mapOf("SENTRY_DSN" to it))
            },
        webDir = webDir(),
        apiDocs = apiDocs,
    )

    private fun app(config: AppConfig) = Javalin.create { configureHttpSurface(it, config) }

    private fun sha(script: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(script.toByteArray())
        return "'sha256-" + Base64.getEncoder().encodeToString(digest) + "'"
    }

    private fun Response.h(name: String): String = headers().get(name)?.firstOrNull().orEmpty()

    private fun Response.timing(): String? = headers().get("Server-Timing")?.firstOrNull()

    @Test
    fun `the page carries the browser hardening headers`() =
        JavalinTest.test(app(config(apiDocs = false))) { _, client ->
            val res = client.get("/")
            assertEquals(200, res.code)
            assertTrue(res.h("Strict-Transport-Security").startsWith("max-age="), "HSTS")
            assertEquals("nosniff", res.h("X-Content-Type-Options"))
            assertEquals("DENY", res.h("X-Frame-Options"))
            assertEquals("strict-origin-when-cross-origin", res.h("Referrer-Policy"))
            val csp = res.h("Content-Security-Policy")
            assertTrue("frame-ancestors 'none'" in csp, csp)
            assertTrue("object-src 'none'" in csp, csp)
        }

    @Test
    fun `the policy allows exactly the page's own inline scripts`() =
        JavalinTest.test(app(config(apiDocs = false))) { _, client ->
            val scriptSrc = directive(client.get("/").h("Content-Security-Policy"), "script-src")
            assertTrue(sha(themeScript) in scriptSrc, scriptSrc)
            assertTrue(sha(snapScript) in scriptSrc, scriptSrc)
            assertTrue("'unsafe-inline'" !in scriptSrc && "'unsafe-eval'" !in scriptSrc, scriptSrc)
        }

    @Test
    fun `error reports may reach the configured tracker and nowhere else`() =
        JavalinTest.test(app(config(apiDocs = false, dsn = "https://abc@o1.ingest.sentry.io/42"))) { _, client ->
            val connect = directive(client.get("/").h("Content-Security-Policy"), "connect-src")
            assertEquals("connect-src 'self' https://o1.ingest.sentry.io", connect)
        }

    @Test
    fun `api responses carry the headers too`() =
        JavalinTest.test(app(config(apiDocs = false))) { _, client ->
            val res = client.get("/api/nothing-here")
            assertEquals("nosniff", res.h("X-Content-Type-Options"))
            assertTrue("frame-ancestors 'none'" in res.h("Content-Security-Policy"))
        }

    @Test
    fun `the API map is hidden unless docs are switched on`() {
        JavalinTest.test(app(config(apiDocs = false))) { _, client ->
            assertEquals(404, client.get("/swagger").code, "/swagger in prod")
            assertEquals(404, client.get("/openapi").code, "/openapi in prod")
        }
        JavalinTest.test(app(config(apiDocs = true))) { _, client ->
            assertEquals(200, client.get("/openapi").code, "/openapi in dev")
        }
    }

    @Test
    fun `signed-out responses carry no server timing`() =
        JavalinTest.test(app(config(apiDocs = false))) { _, client ->
            assertNull(client.get("/").timing(), "page")
            assertNull(client.get("/api/nothing-here").timing(), "api without a token")
        }

    private fun directive(
        csp: String,
        name: String,
    ): String = csp.split(";").map { it.trim() }.first { it.startsWith("$name ") }
}
