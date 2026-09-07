package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.incident.FeedbackRequest
import com.companyb.companyapp.contracts.incident.IncidentPacket
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #526 — incident delivery failure recovery and truthful deduplication: every
 * packet reaches the durable relay before risky dispatch, pre-send failures
 * evict so a retry recovers, ambiguous send failures stay deduped, and
 * concurrent duplicates still send once. No real external issues are created.
 */
class IncidentDeliveryRecoveryTest {
    private val relayed = mutableListOf<IncidentPacket>()

    @BeforeTest
    fun reset() {
        IncidentRegistry.resetForTest()
        IncidentDelivery.resetForTest()
        relayed.clear()
        IncidentDelivery.relayProbe = { relayed.add(it) }
    }

    @AfterTest
    fun cleanUp() {
        IncidentDelivery.resetForTest()
        IncidentRegistry.resetForTest()
    }

    @Test
    fun `rejected queue preserves the packet and lets a retry recover`() {
        val sent = mutableListOf<IncidentPacket>()
        val rejecting = daemonExecutor().also { it.shutdown() }
        IncidentDelivery.installForTest(IncidentSender { sent.add(it) }, rejecting)

        val trace = "trace-${UUID.randomUUID()}"
        val first =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest(trace, "GET /api/slow", "9", 1200),
            )

        assertFalse(first.duplicate)
        assertTrue(sent.isEmpty())
        assertEquals(1, relayed.size)
        assertEquals(trace, relayed.single().traceId)
        assertEquals("GET", relayed.single().method)

        val retry =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest(trace, "GET /api/slow", "9", 1200),
                IncidentSender { sent.add(it) },
            )

        assertFalse(retry.duplicate)
        assertEquals(trace, retry.packet.traceId)
        assertEquals(1, sent.size)
    }

    @Test
    fun `send failure keeps the packet in the relay and suppresses a blind duplicate`() {
        val attempts = AtomicInteger(0)
        val failing =
            IncidentSender {
                attempts.incrementAndGet()
                throw IOException("github down")
            }

        val trace = "trace-${UUID.randomUUID()}"
        val first =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest(trace, "POST /api/sessions", "9", 2500),
                failing,
            )

        assertFalse(first.duplicate)
        assertEquals(1, attempts.get())
        assertEquals(1, relayed.size)
        val preserved = relayed.single()
        assertEquals(trace, preserved.traceId)
        assertEquals("POST", preserved.method)
        assertEquals("/api/sessions", preserved.route)
        assertTrue(preserved.timestamp.isNotBlank())

        val retry =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest(trace, "POST /api/sessions", "9", 2500),
                failing,
            )

        assertTrue(retry.duplicate)
        assertEquals(first.packet, retry.packet)
        assertEquals(1, attempts.get())
    }

    @Test
    fun `concurrent duplicates still send once`() {
        val sent = AtomicInteger(0)
        val sender = IncidentSender { _ -> sent.incrementAndGet() }
        val duplicates = AtomicInteger(0)
        val trace = "trace-${UUID.randomUUID()}"
        val start = CountDownLatch(1)
        val done = CountDownLatch(THREADS)
        repeat(THREADS) {
            Thread {
                start.await()
                val filed =
                    IncidentService.fileUserReport(
                        UUID.randomUUID(),
                        FeedbackRequest(trace, "GET /api/slow", "9", 10),
                        sender,
                    )
                if (filed.duplicate) duplicates.incrementAndGet()
                done.countDown()
            }.start()
        }

        start.countDown()
        assertTrue(done.await(AWAIT_SECONDS, TimeUnit.SECONDS))

        assertEquals(1, sent.get())
        assertEquals(THREADS - 1, duplicates.get())
    }

    @Test
    fun `queued packet is already durable before shutdown`() {
        val sent = mutableListOf<IncidentPacket>()
        IncidentDelivery.installForTest(IncidentSender { sent.add(it) }, daemonExecutor())

        val trace = "trace-${UUID.randomUUID()}"
        val filed =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest(trace, "GET /api/slow", "9", 10),
            )

        assertFalse(filed.duplicate)
        assertEquals(trace, relayed.single().traceId)

        IncidentDelivery.shutdown()

        assertEquals(1, relayed.size)
        assertTrue(sent.size <= 1)
    }

    @Test
    fun `no sender relays the complete packet to the log`() {
        IncidentDelivery.installForTest(null, null)

        val trace = "trace-${UUID.randomUUID()}"
        val filed =
            IncidentService.fileUserReport(
                UUID.randomUUID(),
                FeedbackRequest(trace, "GET /api/clients", "9", 1200),
            )

        assertFalse(filed.duplicate)
        val preserved = relayed.single()
        assertEquals(trace, preserved.traceId)
        assertEquals("GET", preserved.method)
        assertEquals("/api/clients", preserved.route)
        assertEquals("9", preserved.appVersion)
        assertTrue(preserved.timestamp.isNotBlank())
        assertTrue(preserved.reporter.isNotBlank())
    }

    private fun daemonExecutor(): ThreadPoolExecutor =
        ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(QUEUE_CAPACITY),
            { runnable -> Thread(runnable, "incident-test").apply { isDaemon = true } },
        )

    private companion object {
        const val THREADS = 8
        const val AWAIT_SECONDS = 10L
        const val QUEUE_CAPACITY = 10
    }
}
