package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun DashboardEmptyState(
    selectedBranchName: String?,
    onRefresh: () -> Unit,
    modifier: Modifier,
) = MobileDashboardEmptyState(selectedBranchName, onRefresh, modifier)
