package com.companyb.companyapp.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.viewmodel.NotificationBadgeViewModel

/**
 * #96 Q6 — Shell-scoped NotificationBadgeVM bounded by isPostClockIn (DrawerShell's conditional
 * render per ADR-0020). On isPostClockIn → false (logout / clock-out / 401 session-termination),
 * the subtree leaves composition and DisposableEffect.onDispose fires → vm.dispose() cancels
 * viewModelScope, stopping the 60s poll loop cleanly (no GET /api/notifications 401-stream after
 * logout).
 */
@Composable
fun NotificationBadgeHost(
    apiClient: ApiClient,
    isPostClockIn: Boolean,
) {
    if (isPostClockIn) {
        val notificationBadgeViewModel = remember { NotificationBadgeViewModel(apiClient) }
        DisposableEffect(notificationBadgeViewModel) {
            onDispose { notificationBadgeViewModel.dispose() }
        }
    }
}
