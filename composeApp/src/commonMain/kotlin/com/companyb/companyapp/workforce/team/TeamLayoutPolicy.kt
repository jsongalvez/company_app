package com.companyb.companyapp.workforce.team

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * #681 — the Team & branches layout contract: content-width-driven list/detail
 * geometry (the shell-chrome #671, Sessions-workspace #672 and remittance-desk
 * #677 precedents applied at feature level).
 *
 * - At content width >= [detailBreakpoint] the people list sits beside the
 *   selected-person detail; below it the list opens a full-width detail and
 *   Back restores the selected row/scroll structurally via composition state.
 *
 * Pure + composition-free (the #670 contract shape): owners read
 * `BoxWithConstraints.maxWidth` and branch on these helpers, so the decisions are
 * unit-testable without a Compose runtime.
 */
object TeamLayoutPolicy {
    val detailBreakpoint: Dp = 1000.dp

    /** Side-by-side list + detail when true; full-width detail with Back otherwise. */
    fun showSideDetail(contentWidth: Dp): Boolean = contentWidth >= detailBreakpoint
}
