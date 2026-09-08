package com.companyb.companyapp.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.hasCapabilityAnyContext
import com.companyb.companyapp.client.ClientDetailScreen
import com.companyb.companyapp.client.ClientState
import com.companyb.companyapp.client.ClientViewModel
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.notification.NotificationViewModel
import com.companyb.companyapp.notification.NotificationsScreen
import com.companyb.companyapp.session.create.SessionCreateScreen
import com.companyb.companyapp.session.create.SessionCreateViewModel
import com.companyb.companyapp.ui.RouteGateCard
import com.companyb.companyapp.workforce.relief.ReliefInviteViewModel

// #460 — gate + entry helpers extracted from SharedRouteGraph (TooManyFunctions budget
// is 11 per file; the graph keeps registrations, this file keeps the three gate
// composables they render). Same package, internal visibility, no contract change.

// #348 — code-only route gate (the Clients #156 shape); the backend's branch-day create
// gate stays authoritative. No clocked-in branch → gate card: sessions belong to a branch day.
@Composable
internal fun SessionCreateContent(
    apiClient: ApiClient,
    navController: NavHostController,
    onSubmissionLockChanged: (Boolean) -> Unit,
    onClientProfileClick: ((String) -> Unit)?,
) {
    val snapshot by AppSessionState.snapshot.collectAsState()
    val capabilities = snapshot.capabilities
    val selectedBranchId = snapshot.clock?.branchId
    val selectedBranchName = snapshot.clock?.branchName
    val clockedBranchId = selectedBranchId
    if (capabilities.hasCapabilityAnyContext(CapabilityCodes.EDIT_BRANCH_DATA) &&
        clockedBranchId != null
    ) {
        val sessionCreateViewModel: SessionCreateViewModel =
            viewModel { SessionCreateViewModel(apiClient, clockedBranchId) }
        val clientViewModel: ClientViewModel = viewModel { ClientViewModel(apiClient) }
        SessionCreateScreen(
            viewModel = sessionCreateViewModel,
            clientViewModel = clientViewModel,
            branchName = selectedBranchName,
            onBack = {
                onSubmissionLockChanged(false)
                navController.popBackStack()
            },
            // Desktop navigates to the client profile; mobile keeps the default (no-op).
            onClientProfileClick = { clientId -> onClientProfileClick?.invoke(clientId) },
            // #386 — created sessions carry the creator no notification row, so a
            // bearer-only SessionDetail push dead-ends in 404; land on a fresh Dashboard
            // instead (inclusive popUpTo rebuilds the entry-scoped VM so the new session
            // is in the day's list immediately).
            onSessionCreated = { _ ->
                onSubmissionLockChanged(false)
                navController.navigate(Route.Dashboard()) {
                    popUpTo(Route.Dashboard()) { inclusive = true }
                }
            },
            onSubmissionLockChanged = onSubmissionLockChanged,
        )
    } else {
        RouteGateCard(label = "New session")
    }
}

@Composable
internal fun ClientDetailContent(
    apiClient: ApiClient,
    navController: NavHostController,
    clientId: String,
) {
    // Entry-scoped per #112: fresh VM per detail entry; the search entry's VM stays alive under
    // the push so D9's stale-list + D1's notice work.
    val clientDetailViewModel: ClientViewModel = viewModel { ClientViewModel(apiClient) }
    val noticeReturnsToClients = navController.previousRoute() is Route.Clients
    ClientDetailScreen(
        clientId = clientId,
        viewModel = clientDetailViewModel,
        onBack = { navController.popBackStack() },
        onAnonymized = {
            if (!noticeReturnsToClients) ClientState.consumeAnonymizeNotice()
            navController.popBackStack()
        },
    )
}

@Composable
internal fun NotificationsContent(
    apiClient: ApiClient,
    navController: NavHostController,
) {
    // Entry-scoped viewModel(): the Notifications back-stack entry survives the
    // SessionDetail push, so readThisSession persists across push/pop — D3
    // "appears in Read (dimmed) on return". A fresh entry (new visit) creates a
    // fresh VM → Read self-cleans (D1). The invite VM is entry-scoped too — a
    // resolved invite stays gone on return (fresh load), and the badge singleton
    // is poll-corrected within 60s (the accepted down-then-up bounce).
    // The markRead PATCH is fire-and-forget; the detail GET's bearer check accepts
    // read or unread rows, so there is no markRead/GET race (#151 Q7).
    val notificationsViewModel: NotificationViewModel =
        viewModel { NotificationViewModel(apiClient) }
    val reliefInviteViewModel: ReliefInviteViewModel =
        viewModel { ReliefInviteViewModel(apiClient) }
    // #679 — read/navigation separation: read rows (server-read or marked this visit) never
    // re-send mark-read; unread rows fire it separately without gating navigation, so a
    // failed acknowledgment leaves navigation usable and the row honestly unread.
    val readThisSession by notificationsViewModel.readThisSession.collectAsState()
    NotificationsScreen(
        viewModel = notificationsViewModel,
        reliefInviteViewModel = reliefInviteViewModel,
        onNotificationClick = { notification ->
            // D3: mark-read + navigate to the session detail. The route carries only
            // the sessionId — row = null → the detail screen fetches once via
            // GET /api/sessions/{sessionId} (#152). #358: relief rows deep-link to
            // the dashboard scoped to their branch+date (the ReliefDayScreen panel).
            if (!notification.isRead && readThisSession.none { it.id == notification.id }) {
                notificationsViewModel.markRead(notification.id)
            }
            val sessionId = notification.sessionId
            when {
                sessionId != null -> {
                    navController.navigate(Route.SessionDetail(sessionId))
                }

                notification.targetDate != null -> {
                    navController.navigate(
                        Route.Dashboard(
                            branchId = notification.branchId,
                            date = notification.targetDate,
                        ),
                    )
                }
            }
        },
    )
}
