package com.companyb.companyapp.observability

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebErrorAlertsTest {
    private val alerts = CopyOnWriteArrayList<Pair<String?, String>>()
    private lateinit var server: HttpServer
    private val session = UUID.randomUUID()
    private val client = "3f2b8a10-6c1d-4e5f-9a7b-1c2d3e4f5a6b"

    @BeforeTest
    fun startFakeNtfy() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            alerts += ex.requestHeaders.getFirst("Title") to ex.requestBody.readBytes().decodeToString()
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }
        server.start()
        WebErrorAlerts.configure(NtfyConfig("http://127.0.0.1:${server.address.port}", "web-topic"))
    }

    @AfterTest
    fun stop() {
        WebErrorAlerts.configure(null)
        server.stop(0)
    }

    private fun event(
        kind: String,
        data: String,
        seq: Int = 0,
    ) = TelemetryEvent(seq, OffsetDateTime.now(), kind, data)

    private fun awaitAlerts(count: Int) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (alerts.size < count && System.nanoTime() < end) Thread.sleep(10)
    }

    @Test
    fun `an uncaught script error reaches ntfy with its class and screen, never its message`() {
        val message = "Uncaught TypeError: Cannot read properties of null (reading 'Maria Santos')"
        WebErrorAlerts.notice(session, listOf(event("error", """{"r":"#/clients/$client?q=Maria","msg":"$message"}""")))

        awaitAlerts(1)
        val (title, body) = alerts.single()
        assertEquals("CompanyApp web error", title)
        assertTrue(body.startsWith("TypeError on #/clients/{id}\n"), body)
        assertTrue("session $session" in body, body)
        assertFalse("Maria" in body, "message text and query must not leave the server")
        assertFalse(client in body, "ids are replaced")
    }

    @Test
    fun `a refused offline save reaches ntfy`() {
        WebErrorAlerts.notice(
            session,
            listOf(event("error", """{"r":"#/today","outbox":"POST /api/sessions","s":400}""")),
        )

        awaitAlerts(1)
        assertTrue(
            alerts.single().second.startsWith("Offline save refused: POST /api/sessions answered 400 on #/today"),
        )
    }

    @Test
    fun `refused calls alert, but not the expected statuses, server errors or successes`() {
        fun api(status: Int) =
            event("api", """{"r":"#/finance","m":"GET","p":"/api/branches/$client/expenses","s":$status}""")
        assertEquals("GET /api/branches/{id}/expenses answered 403 on #/finance", WebErrorAlerts.line(api(403)))
        for (quiet in listOf(200, 401, 409, 429, 500, 0)) assertNull(WebErrorAlerts.line(api(quiet)), "status $quiet")
        assertNull(WebErrorAlerts.line(event("tap", """{"r":"#/today"}""")))
    }

    @Test
    fun `a burst of errors sends one alert`() {
        WebErrorAlerts.notice(
            session,
            (0 until 5).map { event("error", """{"r":"#/today","msg":"TypeError: x"}""", it) },
        )

        awaitAlerts(1)
        Thread.sleep(300)
        assertEquals(1, alerts.size)
    }
}
