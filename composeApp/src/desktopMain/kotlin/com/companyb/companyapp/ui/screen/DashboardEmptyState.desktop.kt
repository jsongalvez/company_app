package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.ui.theme.Spacing

// #150 — the #147 accepted-SOFT gap: the desktop empty state had no manual refresh
// (auto-poll was the only recovery when a session appeared). The Refresh button sits in
// the same top-right position as the table's Refresh row (SessionList.desktop.kt
// TableHeaderRow) so the affordance position is stable between the empty and list states.
@Composable
internal actual fun DashboardEmptyState(
    selectedBranchName: String?,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xxs),
        ) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onRefresh) {
                Text("Refresh", style = MaterialTheme.typography.labelSmall)
            }
        }
        EmptyStateContent(selectedBranchName)
    }
}
