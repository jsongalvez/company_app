package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// #150 — mobile keeps its shipped #147 empty state (text only): the list's pull-to-refresh
// + the 30s auto-poll cover the refresh story, and the #147 accepted SOFT was scoped to
// desktop. The onRefresh parameter is contract-required by the expect signature (Kotlin
// mandates identical actual parameter lists) and intentionally unused on Android.
@Composable
internal actual fun DashboardEmptyState(
    selectedBranchName: String?,
    onRefresh: () -> Unit,
    modifier: Modifier,
) {
    EmptyStateContent(selectedBranchName, modifier)
}
