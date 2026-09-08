package com.companyb.companyapp.ui.contract

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #670 — focused behavior tests for the operational UI contract's pure decisions:
 * pending-geometry invariants and dialog focus/dismiss rules. Composition itself stays
 * in targeted desktop validation; these pin the decisions every owner reuses.
 */
class OperationalUiContractTest {
    @Test
    fun `progress slot reserves stable pending geometry`() {
        assertEquals(18.dp, OperationalUiContract.progressSlot)
        // The reserved slot fits inside every shared action's minimum bounds, so idle and
        // busy compose identical outer geometry with the label always present.
        assertTrue(OperationalUiContract.minActionHeight > OperationalUiContract.progressSlot)
    }

    @Test
    fun `minimum action height covers both pointer and touch targets`() {
        assertTrue(OperationalUiContract.minActionHeight >= OperationalUiContract.minTargetDesktop)
        assertTrue(OperationalUiContract.minActionHeight >= OperationalUiContract.minTargetTouch)
    }

    @Test
    fun `dialog caps at 560dp and stays inset on compact viewports`() {
        assertEquals(560.dp, OperationalUiContract.dialogMaxWidth)
        assertEquals(560.dp, OperationalUiContract.dialogWidthFor(1200.dp))
        assertEquals(560.dp, OperationalUiContract.dialogWidthFor(800.dp))
        // 390px compact phone: viewport minus both compact gutters.
        assertEquals(390.dp - 16.dp * 2, OperationalUiContract.dialogWidthFor(390.dp))
    }

    @Test
    fun `compact breakpoint separates phone from desktop chrome`() {
        assertTrue(OperationalUiContract.isCompactViewport(390.dp))
        assertFalse(OperationalUiContract.isCompactViewport(1366.dp))
    }

    @Test
    fun `page heading steps down on compact layouts`() {
        assertEquals(28.sp, OperationalUiContract.pageHeadingSize(false))
        assertEquals(24.sp, OperationalUiContract.pageHeadingSize(true))
        assertEquals(34.sp, OperationalUiContract.pageHeadingLineHeight(false))
        assertEquals(30.sp, OperationalUiContract.pageHeadingLineHeight(true))
    }

    @Test
    fun `destructive confirmations focus the safe action`() {
        assertEquals(
            DialogFocusTarget.SAFE_ACTION,
            OperationalUiContract.initialFocusTarget(hasFields = true, isDestructive = true),
        )
        assertEquals(
            DialogFocusTarget.SAFE_ACTION,
            OperationalUiContract.initialFocusTarget(hasFields = false, isDestructive = false),
        )
        assertEquals(
            DialogFocusTarget.FIRST_FIELD,
            OperationalUiContract.initialFocusTarget(hasFields = true, isDestructive = false),
        )
    }

    @Test
    fun `busy dialogs stay pinned unless explicitly escapable`() {
        assertFalse(OperationalUiContract.canDismissWhileBusy(isBusy = true, allowCancelWhenBusy = false))
        assertTrue(OperationalUiContract.canDismissWhileBusy(isBusy = true, allowCancelWhenBusy = true))
        assertTrue(OperationalUiContract.canDismissWhileBusy(isBusy = false, allowCancelWhenBusy = false))
    }

    @Test
    fun `duplicate submission is disabled structurally while busy`() {
        assertTrue(OperationalUiContract.isActionEnabled(baseEnabled = true, isBusy = false))
        assertFalse(OperationalUiContract.isActionEnabled(baseEnabled = true, isBusy = true))
        assertFalse(OperationalUiContract.isActionEnabled(baseEnabled = false, isBusy = false))
    }

    @Test
    fun `compact breakpoint is exact at 600dp`() {
        assertFalse(OperationalUiContract.isCompactViewport(600.dp))
        assertTrue(OperationalUiContract.isCompactViewport(599.dp))
    }

    @Test
    fun `secondary labels never drop below 12sp`() {
        assertTrue(OperationalUiContract.secondaryLabelMin >= 12.sp)
        assertTrue(OperationalUiContract.desktopBody >= 12.sp)
        assertTrue(OperationalUiContract.touchBody >= 12.sp)
    }
}
