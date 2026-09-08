package com.companyb.companyapp.finance

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * #678 — the Finance & Reports layout contract: content-width-driven report-list /
 * selected-day geometry (the shell-chrome #671, Sessions-workspace #672 and
 * remittance-desk #677 precedents applied at feature level).
 *
 * - At content width >= [reportBreakpoint] the report list keeps >= [reportListMinWidth]
 *   and the selected-day region takes >= [selectedDayMinWidth]; below it selecting a
 *   day opens a full-width detail and Back restores the period list and scroll.
 * - Rows stay the default overview; Cards renders behind the View menu, never as a
 *   competing main task.
 *
 * Pure + composition-free (the #670 contract shape): owners read
 * `BoxWithConstraints.maxWidth` and branch on these helpers, so the decisions are
 * unit-testable without a Compose runtime.
 */
object FinanceLayoutPolicy {
    val reportBreakpoint: Dp = 1000.dp
    val reportListMinWidth: Dp = 480.dp
    val selectedDayMinWidth: Dp = 480.dp

    /** Side-by-side report list + selected-day region when true; full-width detail with Back otherwise. */
    fun showSideDetail(contentWidth: Dp): Boolean = contentWidth >= reportBreakpoint
}
