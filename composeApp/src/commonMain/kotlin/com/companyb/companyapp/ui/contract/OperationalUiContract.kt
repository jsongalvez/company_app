package com.companyb.companyapp.ui.contract

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * #670 — the operational UI contract: one small shared system for action hierarchy,
 * pending geometry, focus, and feedback.
 *
 * Pure constants + pure decision helpers. Composable owners live in [OperationalActions],
 * [InlineStatus], and [OperationalDialog]; this file stays composition-free so its
 * decisions are unit-testable without a Compose runtime.
 *
 * Downstream feature tickets own feature-wide adoption; this contract only proves itself
 * on Login, Accept invite, Forgot password, launch/BranchSelect chrome, and Mission
 * delegates.
 */
object OperationalUiContract {
    val dialogMaxWidth: Dp = 560.dp
    val progressSlot: Dp = 18.dp
    val focusRingWidth: Dp = 2.dp
    val controlRadius: Dp = 6.dp
    val surfaceRadius: Dp = 8.dp
    val minTargetDesktop: Dp = 40.dp
    val minTargetTouch: Dp = 48.dp
    val minActionHeight: Dp = 48.dp
    val iconSizeMin: Dp = 18.dp
    val iconSizeMax: Dp = 20.dp
    val pageGutterDesktop: Dp = 24.dp
    val pageGutterCompact: Dp = 16.dp
    val compactViewportBreakpoint: Dp = 600.dp

    val pageHeading: TextUnit = 28.sp
    val pageHeadingLine: TextUnit = 34.sp
    val pageHeadingCompact: TextUnit = 24.sp
    val pageHeadingCompactLine: TextUnit = 30.sp
    val currentObject: TextUnit = 24.sp
    val sectionHeading: TextUnit = 18.sp
    val decisiveNumeral: TextUnit = 32.sp
    val desktopBody: TextUnit = 14.sp
    val touchBody: TextUnit = 16.sp
    val secondaryLabelMin: TextUnit = 12.sp

    /** Compact layouts change grouping/navigation; they never inherit shrunken desktop rails. */
    fun isCompactViewport(viewportWidth: Dp): Boolean = viewportWidth < compactViewportBreakpoint

    /**
     * Dialogs cap at [dialogMaxWidth]; compact viewports stay inset by the compact gutter
     * on each side instead of clipping.
     */
    fun dialogWidthFor(viewportWidth: Dp): Dp {
        val inset = viewportWidth - pageGutterCompact * GUTTER_BOTH_SIDES
        return minOf(dialogMaxWidth, inset.coerceAtLeast(MIN_DIALOG_WIDTH))
    }

    /** Page headings step down on compact layouts; object/section sizes stay fixed. */
    fun pageHeadingSize(isCompact: Boolean): TextUnit = if (isCompact) pageHeadingCompact else pageHeading

    fun pageHeadingLineHeight(isCompact: Boolean): TextUnit = if (isCompact) pageHeadingCompactLine else pageHeadingLine

    /**
     * Initial focus belongs to the first meaningful field, or to the safe action in a
     * destructive confirmation (never the destructive control itself).
     */
    fun initialFocusTarget(
        hasFields: Boolean,
        isDestructive: Boolean,
    ): DialogFocusTarget =
        if (isDestructive || !hasFields) {
            DialogFocusTarget.SAFE_ACTION
        } else {
            DialogFocusTarget.FIRST_FIELD
        }

    /** Escape/Back cancels only when allowed: busy dialogs stay pinned unless explicitly escapable. */
    fun canDismissWhileBusy(
        isBusy: Boolean,
        allowCancelWhenBusy: Boolean,
    ): Boolean = !isBusy || allowCancelWhenBusy

    /**
     * Duplicate submission is disabled structurally: every shared action ANDs the
     * caller gate with the pending leg, so busy always wins regardless of call site.
     */
    fun isActionEnabled(
        baseEnabled: Boolean,
        isBusy: Boolean,
    ): Boolean = baseEnabled && !isBusy

    private const val GUTTER_BOTH_SIDES = 2
    private val MIN_DIALOG_WIDTH: Dp = 280.dp
}

/** Where [OperationalUiContract.initialFocusTarget] points on dialog open. */
enum class DialogFocusTarget {
    FIRST_FIELD,
    SAFE_ACTION,
}
