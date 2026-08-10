package com.companyb.companyapp.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.ui.drawer.DrawerContent
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
    // (Login, BranchSelect) render full-screen: empty `drawerContent` lambda produces a no-op
    // Row child, so Row collapses to just `Box { content() }` (verified at Material3 source
    // NavigationDrawer.kt:621-624). Post-shell routes get the permanent drawer on-screen by
    // structural always-on rendering (no parent-swap of NavHost — keeps NavController back-stack
    // stable across the boundary).
    val currentRoute = navController.currentRoute()
    val isPostClockIn =
        currentRoute != null && currentRoute !is Route.Login && currentRoute !is Route.BranchSelect
    NotificationBadgeHost(apiClient, isPostClockIn)

    CompositionLocalProvider(LocalNavHostController provides navController) {
        PermanentNavigationDrawer(
            drawerContent = {
                if (isPostClockIn) {
                    // #96 Q7 follow-up — PermanentNavigationDrawer's bare Row slot applies no
                    // padding/background/chrome; desktop needs explicit surface-1 background +
                    // width 360 (Material3 PermanentDrawerContainerWidth). Mobile ModalDrawerSheet
                    // provides these defaults.
                    DrawerContent(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .width(360.dp)
                                .background(MaterialTheme.colorScheme.surface),
                    )
                }
            },
            modifier = modifier,
        ) {
            NavHost(navController = navController, startDestination = startDestination) {
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
                            navController.navigate(Route.Dashboard) {
                                popUpTo(Route.Login) { inclusive = true }
                            }
                        },
                    )
                }
                // Per ADR-0020 + #91: desktop Dashboard composes a master-detail Row with SessionDetail pane inline
                // (no SessionDetail route navigation on desktop).
                composable<Route.Dashboard> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            PlaceholderRoute("Dashboard — master pane")
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                            PlaceholderRoute("Session detail pane (inline)")
                        }
                    }
                }
                composable<Route.Clients> {
                    // #113 D7 — code-only route gate (same shape as androidMain); backend GLOBAL
                    // gate + 403 paths stay authoritative (D8).
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
                // #113 — ClientDetail is a pushed route on desktop TOO (unlike SessionDetail —
                // the #91 inline-pane lock is dashboard-specific; #99 outline: push on both
                // platforms, no desktop pane).
                composable<Route.ClientDetail> { entry ->
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
                // #120 — D1: code-only route gate (the `Set<String>` capabilities, #99 D7 pattern);
                // backend 403 paths stay authoritative (D8).
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
                    val notificationsViewModel: NotificationViewModel = viewModel { NotificationViewModel(apiClient) }
                    NotificationsScreen(
                        viewModel = notificationsViewModel,
                        onNotificationClick = { notification ->
                            // D3 (desktop): mark-read only — no desktop SessionDetail route (#91 lock).
                            notificationsViewModel.markRead(notification.id)
                        },
                    )
                }
                // #123 — D9: no route gate (always-visible per #108; backend-authoritative read
                // scoping). hasAnyCapability = zero-grant "No branch access" empty state.
                composable<Route.AuditLog> {
                    val capabilities by SessionState.capabilities.collectAsState()
                    val currentUser by SessionState.currentUser.collectAsState()
                    val auditLogViewModel: AuditLogViewModel = viewModel { AuditLogViewModel(apiClient) }
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
                    val auditLogViewModel: AuditLogViewModel = viewModel { AuditLogViewModel(apiClient) }
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
                        val userViewModel: UserViewModel = viewModel { UserViewModel(apiClient) }
                        UserManagementScreen(
                            viewModel = userViewModel,
                            currentUserId = currentUser?.id,
                        )
                    } else {
                        RouteGateCard(label = "User Management")
                    }
                }
                // No composable<Route.SessionDetail> on desktop — locked by #91 (desktop inline-pane only).
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
