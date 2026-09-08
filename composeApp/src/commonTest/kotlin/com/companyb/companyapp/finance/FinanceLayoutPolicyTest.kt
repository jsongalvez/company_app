package com.companyb.companyapp.finance

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #678 — the Finance & Reports layout contract: report list + selected-day region
 * side by side at >= 1000dp content width (480dp floors), full-width detail with
 * Back below it.
 */
class FinanceLayoutPolicyTest {
    @Test
    fun side_detail_at_and_above_1000dp() {
        assertFalse(FinanceLayoutPolicy.showSideDetail(999.dp))
        assertTrue(FinanceLayoutPolicy.showSideDetail(1000.dp))
        assertTrue(FinanceLayoutPolicy.showSideDetail(1366.dp))
    }

    @Test
    fun panes_keep_their_480dp_floors() {
        assertEquals(480.dp, FinanceLayoutPolicy.reportListMinWidth)
        assertEquals(480.dp, FinanceLayoutPolicy.selectedDayMinWidth)
    }
}
