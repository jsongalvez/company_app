package com.companyb.companyapp.session.dashboard

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * #672 — the Sessions workspace layout contract: content-width-driven list/detail
 * geometry (the shell-chrome #671 precedent applied at feature level — ADR-0020's
 * platform-only feature splits stay retired for this surface).
 *
 * - At content width >= [detailBreakpoint] the list keeps >= [listMinWidth] and the
 *   selected-session detail takes [detailMinWidth]–[detailMaxWidth]; below it the
 *   list opens a full-width detail and Back restores the selected row/scroll.
 * - Below [compactRowsBreakpoint] rows stack identity-first (client, then
 *   status/type + time, price end-aligned); wider rows use matching columns with
 *   fixed action cells (the desktop table).
 *
 * Pure + composition-free (the #670 contract shape): owners read
 * `BoxWithConstraints.maxWidth` and branch on these helpers, so the decisions are
 * unit-testable without a Compose runtime.
 */
object DashboardLayoutPolicy {
    val detailBreakpoint: Dp = 1000.dp
    val compactRowsBreakpoint: Dp = 600.dp
    val listMinWidth: Dp = 600.dp
    val detailMinWidth: Dp = 360.dp
    val detailMaxWidth: Dp = 440.dp

    /** Side-by-side list + detail when true; full-width detail with Back otherwise. */
    fun showSideDetail(contentWidth: Dp): Boolean = contentWidth >= detailBreakpoint

    /** Stacked identity rows when true; column rows with fixed action cells otherwise. */
    fun useCompactRows(contentWidth: Dp): Boolean = contentWidth < compactRowsBreakpoint

    /**
     * Detail width for the side-by-side layout: the remainder past the 600dp list
     * floor, clamped to 360–440dp.
     */
    fun detailWidthFor(contentWidth: Dp): Dp = (contentWidth - listMinWidth).coerceIn(detailMinWidth, detailMaxWidth)
}
