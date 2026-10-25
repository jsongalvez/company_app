package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.incident.IncidentSource
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
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

private data class Alert(
    val path: String,
    val title: String?,
    val body: String,
)

/** A fake ntfy server on a local port: records every POST it gets. */
class NtfyAlertTest {
    private val alerts = CopyOnWriteArrayList<Alert>()
    private lateinit var server: HttpServer
    private lateinit var config: NtfyConfig

    @BeforeTest
    fun startFakeNtfy() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { ex ->
            alerts +=
                Alert(
                    ex.requestURI.path,
                    ex.requestHeaders.getFirst("Title"),
                    ex.requestBody.readBytes().decodeToString(),
                )
            ex.sendResponseHeaders(200, -1)
            ex.close()
        }
        server.start()
        config = NtfyConfig("http://127.0.0.1:${server.address.port}", "alerts-topic")
        IncidentRegistry.resetForTest()
        IncidentDelivery.resetForTest()
    }

    @AfterTest
    fun stop() {
        IncidentDelivery.resetForTest()
        server.stop(0)
    }

    private fun awaitAlerts(count: Int) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (alerts.size < count && System.nanoTime() < end) Thread.sleep(10)
    }

    private fun report(
        trace: String,
        user: UUID = UUID.randomUUID(),
    ) = Auto5xxReport(trace, "POST", "/api/clients/$user/sessions", 500, 12, user.toString())

    @Test
    fun `without a GitHub token a 5xx incident reaches ntfy with no personal data`() {
        IncidentDelivery.configure(null, config)
        val user = UUID.randomUUID()

        IncidentService.fileAuto5xx(report("trace-one", user))

        awaitAlerts(1)
        val alert = alerts.single()
        assertEquals("/alerts-topic", alert.path)
        assertEquals("CompanyApp server error", alert.title)
        assertTrue("POST /api/clients/{id}/sessions answered 500" in alert.body, alert.body)
        assertTrue("trace-one" in alert.body)
        assertFalse(user.toString() in alert.body, "raw user or client id must not be sent")
        assertFalse("****" in alert.body, "reporter is not part of the alert")
    }

    @Test
    fun `a burst sends one alert and a repeat of the same trace sends none`() {
        IncidentDelivery.configure(null, config)

        IncidentService.fileAuto5xx(report("burst-1"))
        IncidentService.fileAuto5xx(report("burst-1"))
        IncidentService.fileAuto5xx(report("burst-2"))
        IncidentService.fileAuto5xx(report("burst-3"))

        awaitAlerts(1)
        Thread.sleep(300)
        assertEquals(1, alerts.size)
    }

    @Test
    fun `after the window the next alert says how many were held back`() {
        var now = 1_000L
        val sender = NtfyIncidentSender(config, windowMs = 600_000, nowMs = { now })
        val packet = { trace: String -> filed(trace) }

        sender.send(packet("a"))
        sender.send(packet("b"))
        sender.send(packet("c"))
        now += 600_001
        sender.send(packet("d"))

        assertEquals(2, alerts.size)
        assertFalse("more errors" in alerts[0].body)
        assertTrue("+2 more errors since the last alert" in alerts[1].body, alerts[1].body)
    }

    @Test
    fun `a user report is not pushed`() {
        val sender = NtfyIncidentSender(config)

        sender.send(filed("u1").copy(source = IncidentSource.USER_REPORT))

        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `config needs both values and a plain topic`() {
        fun parse(
            url: String?,
            topic: String?,
        ) = NtfyConfig.fromEnvironment(mapOf(NtfyConfig.URL_ENV to url, NtfyConfig.TOPIC_ENV to topic))
        assertEquals(NtfyConfig("https://ntfy.sh", "abc_DEF-1"), parse("https://ntfy.sh/", "abc_DEF-1"))
        assertNull(parse("https://ntfy.sh", null))
        assertNull(parse(null, "abc"))
        assertNull(parse("https://ntfy.sh", "has space"))
        assertNull(parse("https://ntfy.sh", "a/b"))
        assertNull(parse("ftp://ntfy.sh", "abc"))
        assertNull(parse("nothost", "abc"))
    }

    private fun filed(trace: String) = IncidentService.fileAuto5xx(report(trace), senderOverride = null).packet
}
