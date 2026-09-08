package com.companyb.companyapp.remittance

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * #677 — the remittance desk layout contract: content-width-driven queue/workspace
 * geometry (the shell-chrome #671 and Sessions-workspace #672 precedents applied at
 * feature level).
 *
 * - At content width >= [queueBreakpoint] a [queueWidth] remittance queue sits beside
 *   one workspace; below it the queue (list route) opens a full-width detail and Back
 *   restores the tab/scroll structurally via the back stack.
 * - No third summary rail: the evolving draft and its review live in the one workspace.
 *
 * Pure + composition-free (the #670 contract shape): owners read
 * `BoxWithConstraints.maxWidth` and branch on these helpers, so the decisions are
 * unit-testable without a Compose runtime.
 */
object RemittanceLayoutPolicy {
    val queueBreakpoint: Dp = 1000.dp
    val queueWidth: Dp = 240.dp

    /** Side-by-side queue + workspace when true; full-width detail with Back otherwise. */
    fun showQueue(contentWidth: Dp): Boolean = contentWidth >= queueBreakpoint
}
