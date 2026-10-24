package com.companyb.companyapp.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationalDayTest {
    @Test
    fun `manila zone id is pinned`() {
        assertEquals("Asia/Manila", OperationalDay.MANILA_ZONE_ID)
    }

    @Test
    fun `boundary hour is pinned at 4`() {
        assertEquals(4, OperationalDay.DAY_BOUNDARY_HOUR)
    }

    @Test
    fun `0359 belongs to the previous business day`() {
        // Jun-27 03:59 Manila is still business-day Jun-26.
        assertTrue(OperationalDay.isBeforeCutoff(3))
        assertEquals(JUN_26, OperationalDay.operationalEpochDay(3, JUN_27))
    }

    @Test
    fun `0400 rolls to the same calendar business day`() {
        assertFalse(OperationalDay.isBeforeCutoff(4))
        assertEquals(JUN_27, OperationalDay.operationalEpochDay(4, JUN_27))
    }

    @Test
    fun `midnight keeps the previous business day current`() {
        assertTrue(OperationalDay.isBeforeCutoff(0))
        assertEquals(JUN_26, OperationalDay.operationalEpochDay(0, JUN_27))
    }

    @Test
    fun `mid-afternoon stays the same calendar business day`() {
        assertFalse(OperationalDay.isBeforeCutoff(15))
        assertEquals(JUN_27, OperationalDay.operationalEpochDay(15, JUN_27))
    }

    private companion object {
        // Epoch days for 2026-06-26/27 (portable longs — no datetime type in commonMain).
        const val JUN_26 = 20630L
        const val JUN_27 = 20631L
    }
}
