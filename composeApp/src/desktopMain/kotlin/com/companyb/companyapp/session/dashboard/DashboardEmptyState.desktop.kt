package com.companyb.companyapp.session.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// #150 — the #147 accepted-SOFT gap: the desktop empty state had no manual refresh
// (auto-poll was the only recovery when a session appeared). The Refresh button sits in
// the same top-right position as the table's Refresh row (the shared DashboardRefreshRow)
// so the affordance position is stable between the empty and list states.
@Composable
internal actual fun DashboardEmptyState(
    selectedBranchName: String?,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        DashboardRefreshRow(onRefresh)
        EmptyStateContent(selectedBranchName)
    }
}
