package com.companyb.companyapp.session.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// #150 — the desktop empty state keeps the manual Refresh in the same top-right
// position as the table's Refresh row (affordance-position stability between the
// empty and list states). #672 — the workspace toolbar now owns a persistent Refresh
// too; this row stays so the empty surface keeps its own recovery without scrolling.
@Composable
internal actual fun DashboardEmptyState(
    args: DashboardEmptyArgs,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        DashboardRefreshRow(onRefresh)
        EmptyStateContent(args)
    }
}
