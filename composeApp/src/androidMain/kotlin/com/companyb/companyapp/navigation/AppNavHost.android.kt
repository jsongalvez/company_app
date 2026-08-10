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
import com.companyb.companyapp.ui.drawer.DrawerContent
import com.companyb.companyapp.ui.drawer.HamburgerWithBadge
import com.companyb.companyapp.ui.screen.AuditLogHistoryScreen
import com.companyb.companyapp.ui.screen.AuditLogScreen
import com.companyb.companyapp.ui.screen.BranchSelectScreen
import com.companyb.companyapp.ui.screen.ClientDetailScreen
import com.companyb.companyapp.ui.screen.ClientsScreen
import com.companyb.companyapp.ui.screen.LoginScreen
import com.companyb.companyapp.ui.screen.NotificationsScreen
import com.companyb.companyapp.ui.screen.RemittanceDetailScreen
import com.companyb.companyapp.ui.screen.RemittanceListScreen
import com.companyb.companyapp.ui.screen.RouteGateCard
import com.companyb.companyapp.ui.screen.UserManagementScreen
import com.companyb.companyapp.viewmodel.AuditLogViewModel
import com.companyb.companyapp.viewmodel.AuthViewModel
import com.companyb.companyapp.viewmodel.BranchSelectViewModel
import com.companyb.companyapp.viewmodel.ClientViewModel
import com.companyb.companyapp.viewmodel.NotificationViewModel
import com.companyb.companyapp.viewmodel.RemittanceViewModel
import com.companyb.companyapp.viewmodel.SessionBootstrapViewModel
import com.companyb.companyapp.viewmodel.UserViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AppNavHost(
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
                        DrawerContent()
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
                        // (Q3a alert-not-status gating).
                        val unreadCount: Int? by NotificationState.unreadCount.collectAsState()
                        TopAppBar(
                            title = {},
                            navigationIcon = {
                                HamburgerWithBadge(
                                    onClick = { scope.launch { drawerState.open() } },
                                    unreadCount = unreadCount,
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
                            bootstrapViewModel = remember { SessionBootstrapViewModel(apiClient) },
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
                            remember { BranchSelectViewModel(apiClient) }
                        BranchSelectScreen(
                            viewModel = branchSelectViewModel,
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
                    composable<Route.Dashboard> { PlaceholderRoute("Dashboard") }
                    composable<Route.Clients> {
                        // #113 D7 — code-only route gate matching the implemented `Set<String>`
                        // capabilities; backend GLOBAL gate + 403 paths stay authoritative (D8).
                        val capabilities by SessionState.capabilities.collectAsState()
                        if (CapabilityCodes.EDIT_BRANCH_DATA in capabilities) {
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
                    composable<Route.Finance> { PlaceholderRoute("Finance") }
                    // #120 — D1: code-only route gate matching the implemented `Set<String>`
                    // capabilities; backend 403 paths stay authoritative (D8, #99 D7 pattern).
                    composable<Route.RemittanceList> {
                        val capabilities by SessionState.capabilities.collectAsState()
                        val selectedBranchId by SessionState.selectedBranchId.collectAsState()
                        if (CapabilityCodes.SUBMIT_REMITTANCE in capabilities) {
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
                        // fresh VM → Read self-cleans (D1).
                        val notificationsViewModel: NotificationViewModel =
                            viewModel { NotificationViewModel(apiClient) }
                        NotificationsScreen(
                            viewModel = notificationsViewModel,
                            onNotificationClick = { notification ->
                                // D3 (mobile): mark-read + navigate to the session detail.
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
                    composable<Route.Reports> { PlaceholderRoute("Reports") }
                    // #135 — D5: code-only MANAGE_USERS route gate (the #99 D7 pattern; backend
                    // GLOBAL gate + 403 paths stay authoritative). The drawer item stays hidden
                    // until the #94-grad capability wiring populates SessionState.capabilities —
                    // documented state, not hacked around (ticket note); pre-wiring the route
                    // shows the gate card.
                    composable<Route.UserManagement> {
                        val capabilities by SessionState.capabilities.collectAsState()
                        val currentUser by SessionState.currentUser.collectAsState()
                        if (CapabilityCodes.MANAGE_USERS in capabilities) {
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
                    composable<Route.SessionDetail> { PlaceholderRoute("Session Detail") }
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
