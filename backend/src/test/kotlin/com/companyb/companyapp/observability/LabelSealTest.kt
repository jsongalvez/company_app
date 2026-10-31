package com.companyb.companyapp.observability

import com.companyb.companyapp.contracts.telemetry.TelemetryBatchRequest
import com.companyb.companyapp.contracts.telemetry.TelemetryEventInput
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Audit L5: a stored tap never carries the guessable label hash the page sent. */
class LabelSealTest {
    private val tap = """{"el":{"tag":"button","h":"1x2y3z","al":"9abc"},"lh":"q7","w":360,"h2":"keep"}"""

    private fun stored(data: String): String {
        val now = System.currentTimeMillis()
        val event = TelemetryEventInput(0, now, "tap", data)
        val batch = TelemetryBatchRequest(UUID.randomUUID().toString(), listOf(event))
        return TelemetryRoutes.parse(batch, now).single().data
    }

    @Test
    fun `label hashes are keyed by the deployment secret before they are stored`() {
        LabelSeal.configure("secret-one")
        val once = stored(tap)
        listOf("1x2y3z", "9abc", "\"q7\"").forEach { assertFalse(it in once, "raw label hash $it stored: $once") }
        assertTrue("\"w\":360" in once && "\"h2\":\"keep\"" in once, "other fields untouched: $once")
        assertEquals(once, stored(tap), "the same label seals the same way, so replay can match it")
        LabelSeal.configure("secret-two")
        assertNotEquals(once, stored(tap), "another secret gives other marks")
    }

    /** The vector tools/replay/replay.mjs computes in the page for the same secret and hash. */
    @Test
    fun `the seal matches the replay tool's`() {
        LabelSeal.configure("secret-one")
        assertEquals("""{"h":"de925b49b55f"}""", stored("""{"h":"abc"}"""))
    }

    @Test
    fun `a boot event's numeric height stays a number`() {
        LabelSeal.configure("secret-one")
        assertEquals("""{"w":360,"h":640}""", stored("""{"w":360,"h":640}"""))
    }
}
