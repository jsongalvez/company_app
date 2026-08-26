package com.companyb.companyapp.ui.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #423 — the dashboard status dropdown mirrors the backend walk-in prohibition:
 * NO_SHOW/CANCELLED are offered for booked sessions only.
 */
class StatusOptionsForTest {
    @Test
    fun `walk-in rows never offer NO_SHOW or CANCELLED`() {
        val options = statusOptionsFor(isWalkIn = true)

        assertTrue("NO_SHOW" !in options)
        assertTrue("CANCELLED" !in options)
        assertTrue("PENDING" in options && "COMPLETED" in options)
    }

    @Test
    fun `booked rows offer every status`() {
        val options = statusOptionsFor(isWalkIn = false)

        assertEquals(setOf("PENDING", "COMPLETED", "NO_SHOW", "CANCELLED"), options.toSet())
    }
}
