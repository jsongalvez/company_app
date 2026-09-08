package com.companyb.companyapp.session.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// #672 — mobile keeps its text-first empty state (list pull-to-refresh + auto-poll
// cover); the workspace toolbar owns the persistent Refresh.
@Composable
@Suppress("UnusedParameter") // #672 mobile text-first state; refresh lives in the toolbar + pull-to-refresh
internal actual fun DashboardEmptyState(
    args: DashboardEmptyArgs,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    EmptyStateContent(args, modifier)
}
