package com.companyb.companyapp.session.dashboard

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #672 — the workspace layout contract: side detail at >= 1000dp content width
 * (detail clamped 360–440dp past the 600dp list floor), full-width detail with Back
 * below it; stacked identity rows below 600dp.
 */
class DashboardLayoutPolicyTest {
    @Test
    fun side_detail_at_and_above_1000dp() {
        assertFalse(DashboardLayoutPolicy.showSideDetail(999.dp))
        assertTrue(DashboardLayoutPolicy.showSideDetail(1000.dp))
        assertTrue(DashboardLayoutPolicy.showSideDetail(1200.dp))
    }

    @Test
    fun detail_width_clamps_360_to_440() {
        assertEquals(360.dp, DashboardLayoutPolicy.detailWidthFor(900.dp))
        assertEquals(360.dp, DashboardLayoutPolicy.detailWidthFor(960.dp))
        assertEquals(400.dp, DashboardLayoutPolicy.detailWidthFor(1000.dp))
        assertEquals(440.dp, DashboardLayoutPolicy.detailWidthFor(1100.dp))
        assertEquals(440.dp, DashboardLayoutPolicy.detailWidthFor(1400.dp))
    }

    @Test
    fun compact_rows_below_600dp() {
        assertTrue(DashboardLayoutPolicy.useCompactRows(390.dp))
        assertTrue(DashboardLayoutPolicy.useCompactRows(599.dp))
        assertFalse(DashboardLayoutPolicy.useCompactRows(600.dp))
        assertFalse(DashboardLayoutPolicy.useCompactRows(1000.dp))
    }
}
