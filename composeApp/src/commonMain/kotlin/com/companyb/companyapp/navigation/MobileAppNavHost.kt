package com.companyb.companyapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.state.NotificationState
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.hasCapabilityAnyContext
import com.companyb.companyapp.state.hasDayGrant
import com.companyb.companyapp.ui.drawer.DrawerContent
import com.companyb.companyapp.ui.drawer.HamburgerWithBadge
import com.companyb.companyapp.ui.screen.AuditLogHistoryScreen
import com.companyb.companyapp.ui.screen.AuditLogScreen
import com.companyb.companyapp.ui.screen.BranchSelectScreen
import com.companyb.companyapp.ui.screen.ClientDetailScreen
import com.companyb.companyapp.ui.screen.ClientsScreen
import com.companyb.companyapp.ui.screen.FinanceReportsScreen
import com.companyb.companyapp.ui.screen.LoginScreen
import com.companyb.companyapp.ui.screen.NotificationsScreen
import com.companyb.companyapp.ui.screen.RemittanceDetailScreen
import com.companyb.companyapp.ui.screen.RemittanceListScreen
import com.companyb.companyapp.ui.screen.RouteGateCard
import com.companyb.companyapp.ui.screen.SessionDashboardScreen
import com.companyb.companyapp.ui.screen.SessionDetailScreen
import com.companyb.companyapp.ui.screen.UserManagementScreen
import com.companyb.companyapp.viewmodel.AuditLogViewModel
import com.companyb.companyapp.viewmodel.AuthViewModel
import com.companyb.companyapp.viewmodel.BranchSelectViewModel
import com.companyb.companyapp.viewmodel.ClientViewModel
import com.companyb.companyapp.viewmodel.FinanceReportsViewModel
import com.companyb.companyapp.viewmodel.NotificationViewModel
import com.companyb.companyapp.viewmodel.ReliefInviteViewModel
import com.companyb.companyapp.viewmodel.RemittanceViewModel
import com.companyb.companyapp.viewmodel.SessionBootstrapViewModel
import com.companyb.companyapp.viewmodel.SessionDashboardViewModel
import com.companyb.companyapp.viewmodel.SessionDetailViewModel
import com.companyb.companyapp.viewmodel.UserViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileAppNavHost(
    apiClient: ApiClient,
    tokenStore: TokenStore,
    navController: NavHostController,
    modifier: Modifier,
) {
    // #94-grad — start destination derives from the VALIDATED session, not raw token
    // presence: App() only composes AppNavHost once launch validation resolved, so a
    // non-null currentUser here means the token passed GET /api/me (silent-401 prevention).
    val startDestination: Route =
        if (SessionState.currentUser.value != null) {
            Route.BranchSelect
        } else {
            Route.Login
        }
    // #96 Q5 — shell wraps the post-clock-in sub-graph of one AppNavHost. Pre-shell routes
    // (Login, BranchSelect) render full-screen: empty `drawerContent` lambda is invisible
    // inside ModalNavigationDrawer's Box (drawer closed + Sheet has no measurable children);
    // no Scaffold topBar pre-shell either, so NavHost gets full content area.
    val currentRoute = navController.currentRoute()
    val isPostClockIn =
        currentRoute != null && currentRoute !is Route.Login && currentRoute !is Route.BranchSelect
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    NotificationBadgeHost(apiClient, isPostClockIn)

    // #96 Q1 + Q5 — `gesturesEnabled = false` (below) is LOAD-BEARING. Prevents a three-way
    // edge-gesture collision (drawer swipe vs Android back-swipe vs pushed-route back-pop) at
    // the shell level across every post-clock-in route. Do NOT silently flip back to true.
    CompositionLocalProvider(LocalNavHostController provides navController) {
        ModalNavigationDrawer(
            drawerContent = {
                if (isPostClockIn) {
                    // #96 Q5 close-out check (source-verified at Material3 NavigationDrawer.kt:1013):
                    // DrawerPredictiveBackHandler is gated on `drawerState.isOpen`, NOT on
                    // `gesturesEnabled=false`. Closed drawer + gesturesEnabled=false registers NO
                    // back interceptor at the drawer, so pushed-route back-pop wins cleanly.
                    ModalDrawerSheet(drawerState = drawerState) {
                        DrawerContent(apiClient = apiClient)
                    }
                }
            },
            drawerState = drawerState,
            gesturesEnabled = false,
            modifier = modifier,
        ) {
            Scaffold(
                topBar = {
                    if (isPostClockIn) {
                        // shell chrome only — pushed detail routes layer their own TopAppBar with
                        // back-chevron via nested-Scaffold per #96 Q5; shell's hamburger temporarily
                        // yields to that detail heading.
                        // HamburgerWithBadge.unreadCount live via NotificationState; null/0 ⟹ no badge
                        // (Q3a alert-not-status gating). #160 — the badge sums pending invites +
                        // unread reminders (#159 Q5): both slots feed the total.
                        val unreadCount: Int? by NotificationState.unreadCount.collectAsState()
                        val inviteCount: Int? by NotificationState.inviteCount.collectAsState()
                        val badgeCount = NotificationState.badgeSum(unreadCount, inviteCount)
                        TopAppBar(
                            title = {},
                            navigationIcon = {
                                HamburgerWithBadge(
                                    onClick = { scope.launch { drawerState.open() } },
                                    unreadCount = badgeCount,
                                )
                            },
                        )
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.padding(padding),
                ) {
                    composable<Route.Login> {
                        LoginScreen(
                            authViewModel = remember { AuthViewModel(apiClient) },
                            bootstrapViewModel = viewModel { SessionBootstrapViewModel(apiClient) },
                            tokenStore = tokenStore,
                            onLoginSuccess = {
                                // Per #91 — popUpTo(Login) inclusive on clock-in (foundation best-guess; #94-grad refines)
                                navController.navigate(Route.BranchSelect) {
                                    popUpTo(Route.Login) { inclusive = true }
                                }
                            },
                            onRegisterClick = { /* register route — out of scope, pending #94-grad */ },
                        )
                    }
                    composable<Route.BranchSelect> {
                        val branchSelectViewModel: BranchSelectViewModel =
                            viewModel { BranchSelectViewModel(apiClient) }
                        val reliefInviteViewModel: ReliefInviteViewModel =
                            viewModel { ReliefInviteViewModel(apiClient) }
                        BranchSelectScreen(
                            viewModel = branchSelectViewModel,
                            reliefInviteViewModel = reliefInviteViewModel,
                            onClockInComplete = {
                                // Per #91 — popUpTo(Login) inclusive on clock-in; #94 Phase 3:
                                // navigate Dashboard only after the capability refresh succeeded.
                                // popUpTo(0): on the launch-validation path BranchSelect IS the
                                // start destination (empty back stack below) — popUpTo(Login)
                                // would no-op there and leave a dead-end BranchSelect reachable
                                // via back (clocked in, no clock-out — #97-grad fog).
                                navController.navigate(Route.Dashboard) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable<Route.Dashboard> {
                        val dashboardViewModel: SessionDashboardViewModel =
                            viewModel { SessionDashboardViewModel(apiClient) }
                        val selectedBranchName by SessionState.selectedBranchName.collectAsState()
                        SessionDashboardScreen(
                            viewModel = dashboardViewModel,
                            selectedBranchName = selectedBranchName,
                            selectedSessionId = null,
                            onSessionClick = { row ->
                                // The dashboard path keeps passing the enriched row (zero extra
                                // requests — the #152 session-detail GET exists now, but only the
                                // notifications path fetches on null-row).
                                navController.navigate(Route.SessionDetail(row.id, row))
                            },
                        )
                    }
                    composable<Route.Clients> {
                        // #113 D7 — code-only route gate, now the #156 any-context check
                        // (#92 Q3 "some branch"; backend GLOBAL gate + 403 paths stay
                        // authoritative — D8).
                        val capabilities by SessionState.capabilities.collectAsState()
                        if (capabilities.hasCapabilityAnyContext(CapabilityCodes.EDIT_BRANCH_DATA)) {
                            val clientsViewModel: ClientViewModel = viewModel { ClientViewModel(apiClient) }
                            ClientsScreen(
                                viewModel = clientsViewModel,
                                onClientClick = { client ->
                                    navController.navigate(Route.ClientDetail(client.id))
                                },
                            )
                        } else {
                            RouteGateCard(label = "Clients")
                        }
                    }
                    composable<Route.ClientDetail> { entry ->
                        // Entry-scoped per #112: fresh VM per detail entry; the search entry's VM
                        // stays alive under the push so D9's stale-list + D1's notice work.
                        val clientDetailViewModel: ClientViewModel = viewModel { ClientViewModel(apiClient) }
                        ClientDetailScreen(
                            clientId = entry.toRoute<Route.ClientDetail>().clientId,
                            viewModel = clientDetailViewModel,
                            onBack = { navController.popBackStack() },
                            onAnonymized = { navController.popBackStack() },
                        )
                    }
                    composable<Route.Inventory> { PlaceholderRoute("Inventory") }
                    // #105 D1 — the merged Finance & Reports screen, gate = widest read
                    // capability (VIEW_BRANCH_DATA any-context, #92 Q3; backend gates authoritative).
                    composable<Route.Finance> {
                        val capabilities by SessionState.capabilities.collectAsState()
                        // #105 D1 — the merged Finance & Reports screen, gate = widest read
                        // capability (VIEW_BRANCH_DATA any-context, #92 Q3; backend gates
                        // authoritative). #158 — a BRANCH_DAY grant holder (relief delegate)
                        // reaches the day-scoped read entry without any VIEW grant.
                        if (capabilities.hasCapabilityAnyContext(CapabilityCodes.VIEW_BRANCH_DATA) ||
                            capabilities.hasDayGrant(CapabilityCodes.EDIT_BRANCH_DATA)
                        ) {
                            val financeReportsViewModel: FinanceReportsViewModel =
                                viewModel { FinanceReportsViewModel(apiClient) }
                            FinanceReportsScreen(viewModel = financeReportsViewModel)
                        } else {
                            RouteGateCard(label = "Finance & Reports")
                        }
                    }
                    // #120 — D1: code-only route gate, now the #156 any-context check
                    // (#92 Q3; backend 403 paths stay authoritative — D8).
                    composable<Route.RemittanceList> {
                        val capabilities by SessionState.capabilities.collectAsState()
                        val selectedBranchId by SessionState.selectedBranchId.collectAsState()
                        if (capabilities.hasCapabilityAnyContext(CapabilityCodes.SUBMIT_REMITTANCE)) {
                            val remittanceViewModel: RemittanceViewModel =
                                viewModel { RemittanceViewModel(apiClient) }
                            RemittanceListScreen(
                                viewModel = remittanceViewModel,
                                branchId = selectedBranchId,
                                onRemittanceClick = { remittance ->
                                    navController.navigate(Route.RemittanceDetail(remittance.id))
                                },
                            )
                        } else {
                            RouteGateCard(label = "Remittance")
                        }
                    }
                    composable<Route.RemittanceDetail> { entry ->
                        val selectedBranchId by SessionState.selectedBranchId.collectAsState()
                        val remittanceViewModel: RemittanceViewModel =
                            viewModel { RemittanceViewModel(apiClient) }
                        RemittanceDetailScreen(
                            remittanceId = entry.toRoute<Route.RemittanceDetail>().id,
                            branchId = selectedBranchId,
                            viewModel = remittanceViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable<Route.Notifications> {
                        // Entry-scoped viewModel(): the Notifications back-stack entry survives the
                        // SessionDetail push, so readThisSession persists across push/pop — D3
                        // "appears in Read (dimmed) on return". A fresh entry (new visit) creates a
                        // fresh VM → Read self-cleans (D1). The invite VM is entry-scoped too — a
                        // resolved invite stays gone on return (fresh load), and the badge singleton
                        // is poll-corrected within 60s (the accepted down-then-up bounce).
                        val notificationsViewModel: NotificationViewModel =
                            viewModel { NotificationViewModel(apiClient) }
                        val reliefInviteViewModel: ReliefInviteViewModel =
                            viewModel { ReliefInviteViewModel(apiClient) }
                        NotificationsScreen(
                            viewModel = notificationsViewModel,
                            reliefInviteViewModel = reliefInviteViewModel,
                            onNotificationClick = { notification ->
                                // D3 (mobile): mark-read + navigate to the session detail. The
                                // route carries only the sessionId — row = null → the detail
                                // screen fetches once via GET /api/sessions/{sessionId} (#152).
                                notificationsViewModel.markRead(notification.id)
                                navController.navigate(Route.SessionDetail(notification.sessionId))
                            },
                        )
                    }
                    // #123 — D9: no route gate (always-visible per #108; backend-authoritative
                    // read scoping). hasAnyCapability = zero-grant "No branch access" state.
                    composable<Route.AuditLog> {
                        val capabilities by SessionState.capabilities.collectAsState()
                        val currentUser by SessionState.currentUser.collectAsState()
                        val auditLogViewModel: AuditLogViewModel =
                            viewModel { AuditLogViewModel(apiClient) }
                        AuditLogScreen(
                            viewModel = auditLogViewModel,
                            currentUserId = currentUser?.id,
                            hasAnyCapability = capabilities.isNotEmpty(),
                            onFullHistory = { entry ->
                                navController.navigate(Route.AuditLogHistory(entry.tableName, entry.recordId))
                            },
                        )
                    }
                    // #123 — D8 per-record history: pushed route, entry-scoped VM (fresh entry
                    // self-cleans, #112 pattern).
                    composable<Route.AuditLogHistory> { entry ->
                        val route = entry.toRoute<Route.AuditLogHistory>()
                        val currentUser by SessionState.currentUser.collectAsState()
                        val auditLogViewModel: AuditLogViewModel =
                            viewModel { AuditLogViewModel(apiClient) }
                        AuditLogHistoryScreen(
                            viewModel = auditLogViewModel,
                            tableName = route.tableName,
                            recordId = route.recordId,
                            currentUserId = currentUser?.id,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    // #135 — D5: code-only MANAGE_USERS route gate, now the #156 any-context
                    // check (backend GLOBAL gate + 403 paths stay authoritative).
                    composable<Route.UserManagement> {
                        val capabilities by SessionState.capabilities.collectAsState()
                        val currentUser by SessionState.currentUser.collectAsState()
                        if (capabilities.hasCapabilityAnyContext(CapabilityCodes.MANAGE_USERS)) {
                            val userViewModel: UserViewModel =
                                viewModel { UserViewModel(apiClient) }
                            UserManagementScreen(
                                viewModel = userViewModel,
                                currentUserId = currentUser?.id,
                            )
                        } else {
                            RouteGateCard(label = "User Management")
                        }
                    }
                    composable<Route.SessionDetail> { entry ->
                        val route = entry.toRoute<Route.SessionDetail>()
                        // Entry-scoped (#112): fresh VM per detail entry — the one-shot fetch
                        // state self-cleans on pop. The dashboard path seeds Success with the
                        // nav-arg row (zero requests); the notifications path (row = null)
                        // fetches once via the session-detail GET (#152).
                        val sessionDetailViewModel: SessionDetailViewModel =
                            viewModel { SessionDetailViewModel(apiClient, route.sessionId, route.row) }
                        SessionDetailScreen(
                            sessionId = route.sessionId,
                            viewModel = sessionDetailViewModel,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderRoute(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$label — pending build ticket", style = MaterialTheme.typography.bodyLarge)
    }
}
