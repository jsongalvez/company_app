package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.incident.FeedbackRequest
import com.companyb.companyapp.contracts.incident.IncidentPacket
import com.companyb.companyapp.contracts.incident.IncidentSource
import com.companyb.companyapp.exception.ValidationException
import com.companyb.companyapp.observability.Auto5xxReport
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IncidentServiceTest {
    private val sent = mutableListOf<IncidentPacket>()
    private val fakeSender = IncidentSender { sent.add(it) }

    @BeforeTest
    fun reset() {
        IncidentRegistry.resetForTest()
        sent.clear()
    }

    @Test
    fun `user report builds a complete packet`() {
        val caller = UUID.randomUUID()
        val filed =
            IncidentService.fileUserReport(
                caller,
                FeedbackRequest("trace-${UUID.randomUUID()}", "GET /api/clients", "1.2.3", 1500),
                fakeSender,
            )

        assertFalse(filed.duplicate)
        val packet = filed.packet
        assertTrue(packet.traceId.startsWith("trace-"))
        assertEquals("GET", packet.method)
        assertEquals("/api/clients", packet.route)
        assertNull(packet.status)
        assertEquals(1500, packet.elapsedMs)
        assertEquals("1.2.3", packet.appVersion)
        Instant.parse(packet.timestamp)
        assertEquals(IncidentSource.USER_REPORT, packet.source)
        assertTrue(packet.reporter.contains("****"))
        assertFalse(packet.reporter.contains(caller.toString()))
        assertEquals(1, sent.size)
    }

    @Test
    fun `route is normalized and scrubbed of ids and query`() {
        val rawId = UUID.randomUUID()
        val filed =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest("trace-${UUID.randomUUID()}", "POST /api/sessions/$rawId?date=2026-01-01", "9", null),
                fakeSender,
            )

        assertEquals("POST", filed.packet.method)
        assertEquals("/api/sessions/{id}", filed.packet.route)
        val packetJson = Json.encodeToString(filed.packet)
        assertFalse(packetJson.contains(rawId.toString()))
    }

    @Test
    fun `unknown method degrades without rejecting`() {
        val filed =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest("trace-${UUID.randomUUID()}", "BREW /api/x", "9", null),
                fakeSender,
            )

        assertEquals("unknown", filed.packet.method)
        assertEquals("/api/x", filed.packet.route)
    }

    @Test
    fun `negative elapsed becomes null`() {
        val filed =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest("trace-${UUID.randomUUID()}", "GET /api/x", "9", -5),
                fakeSender,
            )

        assertNull(filed.packet.elapsedMs)
    }

    @Test
    fun `blank or oversize fields are 400s`() {
        assertFailsWith<ValidationException> {
            IncidentService.fileUserReport(UUID.randomUUID(), FeedbackRequest("", "GET /x", "9"), fakeSender)
        }
        assertFailsWith<ValidationException> {
            IncidentService.fileUserReport(UUID.randomUUID(), FeedbackRequest("t", "  ", "9"), fakeSender)
        }
        assertFailsWith<ValidationException> {
            IncidentService.fileUserReport(UUID.randomUUID(), FeedbackRequest("t", "GET /x", ""), fakeSender)
        }
        assertFailsWith<ValidationException> {
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest("t", "GET /x", "v".repeat(65)),
                fakeSender,
            )
        }
    }

    @Test
    fun `duplicate trace collapses to one issue`() {
        val trace = "trace-${UUID.randomUUID()}"
        val first =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest(trace, "GET /api/slow", "9", 2000),
                fakeSender,
            )
        val second =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest(trace, "GET /api/slow", "9", 2000),
                fakeSender,
            )

        assertFalse(first.duplicate)
        assertTrue(second.duplicate)
        assertEquals(first.packet, second.packet)
        assertEquals(1, sent.size)
    }

    @Test
    fun `auto file carries the server-measured shape`() {
        val rawId = UUID.randomUUID()
        val filed =
            IncidentService.fileAuto5xx(
                Auto5xxReport(
                    "trace-${UUID.randomUUID()}",
                    "GET",
                    "/api/branches/$rawId",
                    500,
                    42,
                    rawId.toString(),
                ),
                fakeSender,
            )

        assertFalse(filed.duplicate)
        val packet = filed.packet
        assertEquals(500, packet.status)
        assertEquals("unknown", packet.appVersion)
        assertEquals(IncidentSource.AUTO_5XX, packet.source)
        assertEquals("/api/branches/{id}", packet.route)
        assertTrue(packet.reporter.contains("****"))
        assertFalse(Json.encodeToString(packet).contains(rawId.toString()))
        assertEquals(1, sent.size)
    }

    @Test
    fun `auto file without caller attributes to server`() {
        val filed =
            IncidentService.fileAuto5xx(
                Auto5xxReport("trace-${UUID.randomUUID()}", "GET", "/x", 500, 1, null),
                fakeSender,
            )

        assertEquals("server", filed.packet.reporter)
    }

    @Test
    fun `packet json carries every triage field`() {
        val filed =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest("trace-${UUID.randomUUID()}", "GET /api/x", "9", 7),
                fakeSender,
            )

        val keys = Json.parseToJsonElement(Json.encodeToString(filed.packet)).jsonObject.keys
        assertTrue(
            keys.containsAll(
                setOf(
                    "traceId",
                    "method",
                    "route",
                    "status",
                    "elapsedMs",
                    "appVersion",
                    "timestamp",
                    "pool",
                    "reporter",
                    "source",
                ),
            ),
            "missing packet fields: $keys",
        )
        val poolKeys =
            Json
                .parseToJsonElement(Json.encodeToString(filed.packet))
                .jsonObject["pool"]!!
                .jsonObject.keys
        assertTrue(poolKeys.containsAll(setOf("active", "idle", "awaiting", "total")))
    }
}
