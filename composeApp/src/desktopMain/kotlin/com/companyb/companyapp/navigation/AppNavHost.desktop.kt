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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.companyb.companyapp.state.hasCapabilityAnyContext
import com.companyb.companyapp.state.hasDayGrant
import com.companyb.companyapp.ui.drawer.DrawerContent
import com.companyb.companyapp.ui.screen.AcceptInviteScreen
import com.companyb.companyapp.ui.screen.AuditLogHistoryScreen
import com.companyb.companyapp.ui.screen.AuditLogScreen
import com.companyb.companyapp.ui.screen.BranchSelectScreen
import com.companyb.companyapp.ui.screen.ClientDetailScreen
import com.companyb.companyapp.ui.screen.ClientsScreen
import com.companyb.companyapp.ui.screen.DashboardSelection
import com.companyb.companyapp.ui.screen.FinanceReportsScreen
import com.companyb.companyapp.ui.screen.ForgotPasswordScreen
import com.companyb.companyapp.ui.screen.LoginNavActions
import com.companyb.companyapp.ui.screen.LoginScreen
import com.companyb.companyapp.ui.screen.NotificationsScreen
import com.companyb.companyapp.ui.screen.ProfileScreen
import com.companyb.companyapp.ui.screen.ReliefDayScreen
import com.companyb.companyapp.ui.screen.RemittanceDetailScreen
import com.companyb.companyapp.ui.screen.RemittanceListScreen
import com.companyb.companyapp.ui.screen.RouteGateCard
import com.companyb.companyapp.ui.screen.SessionCreateScreen
import com.companyb.companyapp.ui.screen.SessionDashboardScreen
import com.companyb.companyapp.ui.screen.SessionDetailPane
import com.companyb.companyapp.ui.screen.SessionDetailScreen
import com.companyb.companyapp.ui.screen.UserManagementScreen
import com.companyb.companyapp.viewmodel.AuditLogViewModel
import com.companyb.companyapp.viewmodel.AuthViewModel
import com.companyb.companyapp.viewmodel.BranchSelectViewModel
import com.companyb.companyapp.viewmodel.ClientViewModel
import com.companyb.companyapp.viewmodel.FinanceReportsViewModel
import com.companyb.companyapp.viewmodel.NotificationViewModel
import com.companyb.companyapp.viewmodel.ProfileViewModel
import com.companyb.companyapp.viewmodel.ReliefAccessViewModel
import com.companyb.companyapp.viewmodel.ReliefDayViewModel
import com.companyb.companyapp.viewmodel.ReliefInviteViewModel
import com.companyb.companyapp.viewmodel.RemittanceViewModel
import com.companyb.companyapp.viewmodel.SessionBootstrapViewModel
import com.companyb.companyapp.viewmodel.SessionCreateViewModel
import com.companyb.companyapp.viewmodel.SessionDashboardViewModel
import com.companyb.companyapp.viewmodel.SessionDetailViewModel
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
                        apiClient = apiClient,
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
                        authViewModel = viewModel { AuthViewModel(apiClient) },
                        bootstrapViewModel = viewModel { SessionBootstrapViewModel(apiClient) },
                        tokenStore = tokenStore,
                        actions =
                            LoginNavActions(
                                onLoginSuccess = {
                                    // Per #91 — popUpTo(Login) inclusive on clock-in
                                    // (foundation best-guess; #94-grad refines)
                                    navController.navigate(Route.BranchSelect) {
                                        popUpTo(Route.Login) { inclusive = true }
                                    }
                                },
                                // #350 — invite redemption entry point.
                                onAcceptInviteClick = { navController.navigate(Route.AcceptInvite) },
                                // #353 — forgot-password entry point.
                                onForgotPasswordClick = { navController.navigate(Route.ForgotPassword) },
                            ),
                    )
                }
                composable<Route.AcceptInvite> {
                    // Entry-scoped VM (the #112 shape): a resolved or abandoned invite
                    // self-cleans — back to Login starts a fresh one.
                    val acceptInviteViewModel: AuthViewModel = viewModel { AuthViewModel(apiClient) }
                    AcceptInviteScreen(
                        authViewModel = acceptInviteViewModel,
                        tokenStore = tokenStore,
                        onDone = { navController.popBackStack() },
                    )
                }
                composable<Route.ForgotPassword> {
                    // Entry-scoped VM (#112 shape, same as AcceptInvite).
                    val forgotPasswordViewModel: AuthViewModel = viewModel { AuthViewModel(apiClient) }
                    ForgotPasswordScreen(
                        authViewModel = forgotPasswordViewModel,
                        tokenStore = tokenStore,
                        onDone = { navController.popBackStack() },
                    )
                }
                composable<Route.BranchSelect> {
                    val branchSelectViewModel: BranchSelectViewModel =
                        viewModel { BranchSelectViewModel(apiClient) }
                    val reliefInviteViewModel: ReliefInviteViewModel =
                        viewModel { ReliefInviteViewModel(apiClient) }
                    // #357 — the pre-clock-in relief-request panel (entry-scoped VM).
                    val reliefAccessViewModel: ReliefAccessViewModel =
                        viewModel { ReliefAccessViewModel(apiClient) }
                    BranchSelectScreen(
                        viewModel = branchSelectViewModel,
                        reliefInviteViewModel = reliefInviteViewModel,
                        reliefAccessViewModel = reliefAccessViewModel,
                        onClockInComplete = {
                            // Per #91 — popUpTo(Login) inclusive on clock-in; #94 Phase 3:
                            // navigate Dashboard only after the capability refresh succeeded.
                            // popUpTo(0): on the launch-validation path BranchSelect IS the
                            // start destination (empty back stack below) — popUpTo(Login)
                            // would no-op there and leave a dead-end BranchSelect reachable
                            // via back (clocked in, no clock-out — #97-grad fog).
                            navController.navigate(Route.Dashboard()) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }
                // Per ADR-0020 + #91: desktop Dashboard composes a master-detail Row with the
                // SessionDetail pane inline. #152 (the #151 Q6 scoped revision): desktop gained
                // a pushed `Route.SessionDetail` for the NOTIFICATION entry point only — the
                // dashboard pane below stays untouched.
                composable<Route.Dashboard> { entry ->
                    // #358 — relief deep link: (branchId, date) renders the branch-day panel
                    // in place of the master-detail live dashboard.
                    val deepLink = entry.toRoute<Route.Dashboard>()
                    if (deepLink.branchId != null && deepLink.date != null) {
                        val reliefDayViewModel: ReliefDayViewModel =
                            viewModel { ReliefDayViewModel(apiClient, deepLink.branchId, deepLink.date) }
                        ReliefDayScreen(
                            viewModel = reliefDayViewModel,
                            date = deepLink.date,
                        )
                        return@composable
                    }
                    val dashboardViewModel: SessionDashboardViewModel =
                        viewModel { SessionDashboardViewModel(apiClient) }
                    val selectedBranchName by SessionState.selectedBranchName.collectAsState()
                    val lastData by dashboardViewModel.lastData.collectAsState()
                    var selectedSessionId by remember { mutableStateOf<String?>(null) }
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(DESKTOP_MASTER_WEIGHT).fillMaxSize()) {
                            SessionDashboardScreen(
                                viewModel = dashboardViewModel,
                                selection =
                                    DashboardSelection(
                                        branchName = selectedBranchName,
                                        sessionId = selectedSessionId,
                                    ),
                                onSessionClick = { session -> selectedSessionId = session.id },
                                // #348 — the dashboard's entry into the start-a-session flow.
                                onSessionCreateClick = { navController.navigate(Route.SessionCreate) },
                            )
                        }
                        Box(modifier = Modifier.weight(DESKTOP_DETAIL_WEIGHT).fillMaxSize()) {
                            // #382 — the editable pane (mutations + authoritative reload via the
                            // dashboard poll refresh).
                            SessionDetailPane(
                                session = lastData?.sessions?.firstOrNull { it.id == selectedSessionId },
                                apiClient = apiClient,
                                refreshSession = { dashboardViewModel.refreshAfterMutation() },
                            )
                        }
                    }
                }
                composable<Route.Clients> {
                    // #113 D7 — code-only route gate, now the #156 any-context check
                    // (#92 Q3 "some branch"; backend GLOBAL gate + 403 paths stay authoritative).
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
                // #113 — ClientDetail is a pushed route on desktop TOO (the #91 inline-pane
                // lock is dashboard-specific; #99 outline: push on both platforms, no desktop
                // pane). SessionDetail gained its own pushed desktop route in #152 (notification
                // entry point only — #151 Q6).
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
                // #105 D1 — the merged Finance & Reports screen, gate = widest read capability
                // (VIEW_BRANCH_DATA any-context, #92 Q3; backend gates authoritative).
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
                    NotificationsDestination(apiClient, navController)
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
                // #135 — D5: code-only MANAGE_USERS route gate, now the #156 any-context
                // check (backend GLOBAL gate + 403 paths stay authoritative).
                composable<Route.UserManagement> {
                    val capabilities by SessionState.capabilities.collectAsState()
                    val currentUser by SessionState.currentUser.collectAsState()
                    if (capabilities.hasCapabilityAnyContext(CapabilityCodes.MANAGE_USERS)) {
                        val userViewModel: UserViewModel = viewModel { UserViewModel(apiClient) }
                        UserManagementScreen(
                            viewModel = userViewModel,
                            currentUserId = currentUser?.id,
                        )
                    } else {
                        RouteGateCard(label = "User Management")
                    }
                }
                // #381 — own profile: no route gate (every authenticated user), pushed
                // route, entry-scoped VM (#112).
                composable<Route.Profile> {
                    val profileViewModel: ProfileViewModel =
                        viewModel { ProfileViewModel(apiClient) }
                    ProfileScreen(
                        viewModel = profileViewModel,
                        onBack = { navController.popBackStack() },
                    )
                }
                // #152 — scoped #91-lock revision (#151 Q6): desktop SessionDetail exists as a
                // pushed route for the NOTIFICATION entry point only (desktop is the main
                // platform); the dashboard's inline pane is untouched. Entry-scoped VM (#112):
                // a fresh VM per push — the one-shot fetch state self-cleans on pop.
                composable<Route.SessionCreate> {
                    // #348 — code-only route gate (the Clients #156 shape); the backend's
                    // branch-day create gate stays authoritative. No clocked-in branch →
                    // gate card: sessions belong to a branch day.
                    val capabilities by SessionState.capabilities.collectAsState()
                    val selectedBranchId by SessionState.selectedBranchId.collectAsState()
                    val selectedBranchName by SessionState.selectedBranchName.collectAsState()
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
                            onBack = { navController.popBackStack() },
                            onSessionCreated = { id ->
                                navController.navigate(Route.SessionDetail(id)) {
                                    popUpTo(Route.Dashboard())
                                }
                            },
                        )
                    } else {
                        RouteGateCard(label = "New session")
                    }
                }
                composable<Route.SessionDetail> { entry ->
                    val route = entry.toRoute<Route.SessionDetail>()
                    val sessionDetailViewModel: SessionDetailViewModel =
                        viewModel { SessionDetailViewModel(apiClient, route.sessionId, route.row) }
                    SessionDetailScreen(
                        sessionId = route.sessionId,
                        viewModel = sessionDetailViewModel,
                        apiClient = apiClient,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

private const val DESKTOP_MASTER_WEIGHT = 0.6f
private const val DESKTOP_DETAIL_WEIGHT = 0.4f

@Composable
private fun NotificationsDestination(
    apiClient: ApiClient,
    navController: NavHostController,
) {
    val notificationsViewModel: NotificationViewModel = viewModel { NotificationViewModel(apiClient) }
    val reliefInviteViewModel: ReliefInviteViewModel =
        viewModel { ReliefInviteViewModel(apiClient) }
    NotificationsScreen(
        viewModel = notificationsViewModel,
        reliefInviteViewModel = reliefInviteViewModel,
        onNotificationClick = { notification ->
            // #152 — scoped #91-lock revision (#151 Q6): desktop notification taps
            // now mark-read + push the SessionDetail route (desktop is the main
            // platform). The pushed route exists ONLY for this entry point — the
            // dashboard keeps its inline master-detail pane. The markRead PATCH is
            // fire-and-forget; the detail GET's bearer check accepts read or unread
            // rows, so there is no markRead/GET race (#151 Q7). #358: relief rows
            // deep-link to the branch+date panel.
            notificationsViewModel.markRead(notification.id)
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

@Composable
private fun PlaceholderRoute(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$label — pending build ticket", style = MaterialTheme.typography.bodyLarge)
    }
}
