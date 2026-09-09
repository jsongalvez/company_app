package com.companyb.companyapp.session.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// #672 — mobile keeps its text-first empty state (list pull-to-refresh + auto-poll
// cover); the workspace toolbar owns the persistent Refresh.
@Composable
internal actual fun DashboardEmptyState(
    args: DashboardEmptyArgs,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    EmptyStateContent(args, modifier)
}
