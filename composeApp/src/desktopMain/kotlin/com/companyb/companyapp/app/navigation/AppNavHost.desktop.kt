package com.companyb.companyapp.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.drawer.DrawerContent
import com.companyb.companyapp.client.ClientState
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.session.dashboard.DashboardLayoutPolicy
import com.companyb.companyapp.session.dashboard.DashboardSelection
import com.companyb.companyapp.session.dashboard.SessionDashboardScreen
import com.companyb.companyapp.session.dashboard.SessionDashboardViewModel
import com.companyb.companyapp.session.detail.SessionDetailPane
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.workforce.attendance.AttendanceRosterCard
import com.companyb.companyapp.workforce.attendance.AttendanceRosterViewModel
import com.companyb.companyapp.workforce.relief.ReliefAccessViewModel
import com.companyb.companyapp.workforce.relief.ReliefInviteViewModel
import com.companyb.companyapp.workforce.relief.ReliefPlanningTabContent
import com.companyb.companyapp.workforce.relief.incomingPending
import kotlinx.coroutines.launch

@Composable
actual fun AppNavHost(
    apiClient: ApiClient,
    tokenStore: TokenStore,
    navController: NavHostController,
    modifier: Modifier,
) {
    // #96 Q5 — shell wraps the post-clock-in sub-graph of one AppNavHost. Pre-shell routes
    // (Login, BranchSelect, AcceptInvite, ForgotPassword — #487) render full-screen:
    // the empty `drawerContent` branch plus no topBar leaves NavHost the full content
    // area. Both width branches share the caller-owned NavController, so the back-stack
    // (and entry-scoped VMs) survive a resize across the breakpoint.
    val currentRoute = navController.currentRoute()
    val postClockIn = currentRoute.isPostClockIn()
    val sessionCreateNavigationLocked = rememberSessionCreateNavigationLock()
    // #726 — dirty-intake exit intent (shared with mobile).
    val sessionCreateExitGuard = rememberSessionCreateExitGuard()
    val clientMutationInFlight by ClientState.clientMutationInFlight.collectAsState()
    NotificationBadgeHost(apiClient, postClockIn)

    CompositionLocalProvider(LocalNavHostController provides navController) {
        // #671 — width-driven chrome (supersedes ADR-0020's platform-target split for the
        // shell): >=1200dp pins a 224dp sidebar; below it a labeled menu trigger opens a
        // modal drawer, so a 1024-wide window keeps its working space. Feature-level
        // list/detail splits stay with their owners (#672 dashboard, #677 remittance).
        val navigationEnabled =
            shellNavigationEnabled(
                sessionCreateNavigationLocked.value,
                clientMutationInFlight,
            )
        // One nav-content lambda serves both widths; only the surrounding chrome
        // differs (sidebar vs modal + top bar).
        val navContent: @Composable (Modifier) -> Unit = { contentModifier ->
            DesktopShellNavHost(
                apiClient = apiClient,
                tokenStore = tokenStore,
                navController = navController,
                sessionCreateNavigationLocked = sessionCreateNavigationLocked,
                sessionCreateExitGuard = sessionCreateExitGuard,
                contentModifier = contentModifier,
            )
        }
        BoxWithConstraints(modifier = modifier) {
            if (ShellLayoutPolicy.useSidebar(maxWidth)) {
                WideDesktopShell(
                    apiClient = apiClient,
                    postClockIn = postClockIn,
                    navigationEnabled = navigationEnabled,
                    navContent = navContent,
                    exitGuard = sessionCreateExitGuard,
                )
            } else {
                NarrowDesktopShell(
                    apiClient = apiClient,
                    postClockIn = postClockIn,
                    navigationEnabled = navigationEnabled,
                    navContent = navContent,
                    exitGuard = sessionCreateExitGuard,
                )
            }
        }
    }
}

@Composable
private fun WideDesktopShell(
    apiClient: ApiClient,
    postClockIn: Boolean,
    navigationEnabled: Boolean,
    navContent: @Composable (Modifier) -> Unit,
    exitGuard: SessionCreateExitGuard? = null,
) {
    PermanentNavigationDrawer(
        drawerContent = {
            if (postClockIn) {
                DrawerContent(
                    apiClient = apiClient,
                    navigationEnabled = navigationEnabled,
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .width(ShellLayoutPolicy.sidebarWidth)
                            .background(MaterialTheme.colorScheme.surface),
                    exitGuard = exitGuard,
                )
            }
        },
    ) {
        navContent(Modifier)
    }
}

@Composable
private fun NarrowDesktopShell(
    apiClient: ApiClient,
    postClockIn: Boolean,
    navigationEnabled: Boolean,
    navContent: @Composable (Modifier) -> Unit,
    exitGuard: SessionCreateExitGuard? = null,
) {
    val narrowDrawerState = rememberDrawerState(DrawerValue.Closed)
    val narrowScope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerContent = {
            if (postClockIn) {
                ModalDrawerSheet(drawerState = narrowDrawerState) {
                    DrawerContent(
                        apiClient = apiClient,
                        navigationEnabled = navigationEnabled,
                        onItemNavigated = { narrowScope.launch { narrowDrawerState.close() } },
                        exitGuard = exitGuard,
                    )
                }
            }
        },
        drawerState = narrowDrawerState,
        gesturesEnabled = false,
    ) {
        Scaffold(
            topBar = {
                if (postClockIn) {
                    ShellTopBar(onMenuClick = { narrowScope.launch { narrowDrawerState.open() } })
                }
            },
        ) { padding ->
            navContent(Modifier.padding(padding))
        }
    }
}

@Composable
private fun DesktopShellNavHost(
    apiClient: ApiClient,
    tokenStore: TokenStore,
    navController: NavHostController,
    sessionCreateNavigationLocked: MutableState<Boolean>,
    sessionCreateExitGuard: SessionCreateExitGuard,
    contentModifier: Modifier,
) {
    NavHost(navController = navController, startDestination = startDestination(), modifier = contentModifier) {
        // #456 — one shared registration; this shell keeps only chrome, the
        // master-detail dashboard below, and the remittance desk queue.
        appRouteGraph(
            apiClient = apiClient,
            tokenStore = tokenStore,
            navController = navController,
            onSubmissionLockChanged = { locked ->
                sessionCreateNavigationLocked.value = locked
            },
            hooks =
                PlatformRouteHooks(
                    dashboardLive = {
                        DesktopDashboardLive(
                            apiClient = apiClient,
                            navController = navController,
                            resetSessionCreateNavigationLock = {
                                sessionCreateNavigationLocked.value = false
                            },
                        )
                    },
                    onClientProfileClick = { clientId ->
                        navController.navigate(Route.ClientDetail(clientId))
                    },
                    onDeskQueueNavigate = { currentId, id ->
                        navigateDeskQueue(navController, currentId, id)
                    },
                ),
            exitGuard = sessionCreateExitGuard,
            onExitToRoute = { route ->
                navController.navigate(route) {
                    popUpTo(Route.Dashboard()) { inclusive = route is Route.Dashboard }
                }
            },
        )
    }
}

@Composable
private fun DashboardReliefContent(
    context: DashboardMasterContext,
    inviteViewModel: ReliefInviteViewModel,
    viewModel: ReliefAccessViewModel,
) {
    // #680 — the Relief tab: today's requests plus invitation planning for the viewed
    // branch/date (shared owner with BranchSelect and mobile). The context bundle is the
    // file's DashboardMasterContext (the #535 real-ownership shape), not a new DTO.
    val id = context.branchId
    if (id == null) {
        // Transient pre-clock snapshot: the tab never renders blank without guidance.
        Text(
            text = "Clock in to a branch to plan relief.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            modifier = Modifier.padding(Spacing.md),
        )
        return
    }
    ReliefPlanningTabContent(
        branchId = id,
        branchName = context.branchName,
        viewedDate = context.operationalDate,
        branchDayId = context.branchDayId,
        inviteViewModel = inviteViewModel,
        accessViewModel = viewModel,
        currentUserId = context.userId,
        isReliefUser = context.isReliefUser,
    )
}

@Composable
private fun DesktopAttendanceContent(
    isReliefUser: Boolean,
    branchId: String,
    branchName: String?,
    currentUserId: String?,
    viewModel: AttendanceRosterViewModel,
) {
    if (!isReliefUser) {
        AttendanceRosterCard(
            viewModel = viewModel,
            branchId = branchId,
            branchName = branchName,
            currentUserId = currentUserId,
            isReliefUser = isReliefUser,
        )
    }
}

@Composable
private fun DesktopDetailPane(
    session: DashboardSessionResponse?,
    apiClient: ApiClient,
    onRefresh: () -> Unit,
) {
    // #382 — the editable pane (mutations + authoritative reload via the
    // dashboard poll refresh). #406 — desktop carries the void/unvoid affordance.
    SessionDetailPane(
        session = session,
        apiClient = apiClient,
        refreshSession = onRefresh,
        allowVoid = true,
    )
}

// #677 — desk queue selection replaces the active detail selection instead of growing
// a history of sibling remittances: pop back to the list, then push the one detail.
private fun navigateDeskQueue(
    navController: NavHostController,
    currentId: String,
    id: String,
) {
    if (id != currentId) {
        navController.navigate(Route.RemittanceDetail(id)) {
            popUpTo(Route.RemittanceList) { inclusive = false }
            launchSingleTop = true
        }
    }
}

/**
 * #407 — the desktop live dashboard (the #394 mechanical shape): the #91 master-detail
 * Row with the inline SessionDetail pane (#382) and the entry-scoped relief-access
 * card (#400, the #112 self-cleaning VM). The #358 relief deep-link renders above
 * this in the shared graph — this covers the live dashboard only.
 */
@Composable
private fun DesktopDashboardLive(
    apiClient: ApiClient,
    navController: NavHostController,
    resetSessionCreateNavigationLock: () -> Unit,
) {
    val dashboardViewModel: SessionDashboardViewModel =
        viewModel { SessionDashboardViewModel(apiClient) }
    // #671 — section working context: the selected session survives section switches
    // (retained per user+branch; a branch switch rekeys and starts unselected, so
    // old-branch rows never present as new-branch data).
    val masterContext = rememberDashboardMasterContext()
    var selectedSessionId by remember(masterContext.userId, masterContext.branchId) {
        mutableStateOf(masterContext.selectedSessionId)
    }
    // #672 — narrow windows open the detail full-width over the mounted master (Back
    // restores the selected row/scroll structurally); wide layouts keep side detail.
    var narrowDetailOpen by remember(masterContext.userId, masterContext.branchId) {
        mutableStateOf(false)
    }
    val paneWiring =
        DashboardPaneWiring(
            onSessionClick = { sessionId ->
                selectedSessionId = sessionId
                NavigationContextStore.retain(
                    masterContext.userId,
                    masterContext.branchId,
                    Route.Dashboard(),
                    selectedId = sessionId,
                )
            },
            onSessionCreateClick = {
                resetSessionCreateNavigationLock()
                navController.navigate(Route.SessionCreate)
            },
            detailOpen = narrowDetailOpen,
            onDetailOpenChange = { narrowDetailOpen = it },
        )
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Captured before the Row/Box scopes shadow the constraints receiver.
        val contentWidth = maxWidth
        if (DashboardLayoutPolicy.showSideDetail(contentWidth)) {
            DesktopWideDashboard(
                apiClient = apiClient,
                dashboardViewModel = dashboardViewModel,
                context = masterContext.copy(selectedSessionId = selectedSessionId),
                detailWidth = DashboardLayoutPolicy.detailWidthFor(contentWidth),
                wiring = paneWiring,
            )
        } else {
            DesktopNarrowDashboard(
                apiClient = apiClient,
                dashboardViewModel = dashboardViewModel,
                context = masterContext.copy(selectedSessionId = selectedSessionId),
                wiring = paneWiring,
            )
        }
    }
}

/**
 * #672 — the dashboard pane wiring as one object (the #477 carrier precedent): the
 * row callbacks plus the narrow-detail open flag travel together to both width
 * branches; wide layouts ignore the detail legs.
 */
private data class DashboardPaneWiring(
    val onSessionClick: (String) -> Unit,
    val onSessionCreateClick: () -> Unit,
    val detailOpen: Boolean,
    val onDetailOpenChange: (Boolean) -> Unit,
)

@Composable
private fun DesktopWideDashboard(
    apiClient: ApiClient,
    dashboardViewModel: SessionDashboardViewModel,
    context: DashboardMasterContext,
    detailWidth: Dp,
    wiring: DashboardPaneWiring,
) {
    val lastData by dashboardViewModel.lastData.collectAsState()
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            DesktopMasterPane(
                apiClient = apiClient,
                dashboardViewModel = dashboardViewModel,
                context = context,
                onSessionClick = wiring.onSessionClick,
                onSessionCreateClick = wiring.onSessionCreateClick,
            )
        }
        Box(
            modifier =
                Modifier
                    .width(detailWidth)
                    .fillMaxSize(),
        ) {
            DesktopDetailPane(
                session = lastData?.sessions?.firstOrNull { it.id == context.selectedSessionId },
                apiClient = apiClient,
                onRefresh = { dashboardViewModel.refreshAfterMutation() },
            )
        }
    }
}

@Composable
private fun DesktopNarrowDashboard(
    apiClient: ApiClient,
    dashboardViewModel: SessionDashboardViewModel,
    context: DashboardMasterContext,
    wiring: DashboardPaneWiring,
) {
    val lastData by dashboardViewModel.lastData.collectAsState()
    Box(modifier = Modifier.fillMaxSize()) {
        DesktopMasterPane(
            apiClient = apiClient,
            dashboardViewModel = dashboardViewModel,
            context = context,
            onSessionClick = { sessionId ->
                wiring.onSessionClick(sessionId)
                wiring.onDetailOpenChange(true)
            },
            onSessionCreateClick = wiring.onSessionCreateClick,
        )
        if (wiring.detailOpen) {
            val narrowSession = lastData?.sessions?.firstOrNull { it.id == context.selectedSessionId }
            LaunchedEffect(narrowSession) {
                if (narrowSession == null) wiring.onDetailOpenChange(false)
            }
            if (narrowSession != null) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TertiaryActionButton(
                            label = "Back",
                            onClick = { wiring.onDetailOpenChange(false) },
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        )
                        DesktopDetailPane(
                            session = narrowSession,
                            apiClient = apiClient,
                            onRefresh = { dashboardViewModel.refreshAfterMutation() },
                        )
                    }
                }
            }
        }
    }
}

/**
 * #671 — the dashboard's snapshot-derived view context, read once per composition from
 * the single session snapshot.
 * #672 — the viewed operational date names the day in the workspace header.
 */
@Composable
private fun rememberDashboardMasterContext(): DashboardMasterContext {
    val snapshot by AppSessionState.snapshot.collectAsState()
    return DashboardMasterContext(
        branchName = snapshot.clock?.branchName,
        branchId = snapshot.clock?.branchId,
        branchDayId = snapshot.clock?.branchDayId,
        operationalDate = snapshot.clock?.operationalDate,
        userId = snapshot.user?.id,
        isReliefUser = snapshot.clock?.isRelief == true,
        selectedSessionId =
            NavigationContextStore
                .retained(
                    snapshot.user?.id,
                    snapshot.clock?.branchId,
                    Route.Dashboard(),
                )?.selectedId,
    )
}

/**
 * #671 — the dashboard's snapshot-derived view context: every ambient input the
 * master pane renders but never mutates, read once per composition from the single
 * session snapshot. One bundle because the six legs are always derived together;
 * the selected session itself stays in nav state (retained per user+branch).
 * #672 — the viewed operational date names the day in the workspace header.
 */
private data class DashboardMasterContext(
    val branchName: String?,
    val branchId: String?,
    val branchDayId: String?,
    val operationalDate: String?,
    val userId: String?,
    val isReliefUser: Boolean,
    val selectedSessionId: String?,
)

@Composable
private fun DesktopMasterPane(
    apiClient: ApiClient,
    dashboardViewModel: SessionDashboardViewModel,
    context: DashboardMasterContext,
    onSessionClick: (String) -> Unit,
    onSessionCreateClick: () -> Unit,
) {
    // #400/#404 — entry-scoped relief + attendance VMs (the #112 self-cleaning shape):
    // same NavBackStackEntry owner as before the #671 split, so lifecycle is unchanged.
    val reliefAccessViewModel: ReliefAccessViewModel =
        viewModel { ReliefAccessViewModel(apiClient) }
    // #680 — entry-scoped relief-invite VM for the Team Relief tab (shared owner with
    // BranchSelect and mobile).
    val reliefInviteViewModel: ReliefInviteViewModel =
        viewModel { ReliefInviteViewModel(apiClient) }
    val attendanceViewModel: AttendanceRosterViewModel =
        viewModel { AttendanceRosterViewModel(apiClient) }
    // #672 — the relief rows load here (not inside the Team sheet slot) so the
    // toolbar's actionable count is live before the sheet opens.
    val reliefRows by reliefAccessViewModel.freshestRequests.collectAsState()
    val dayId = context.branchDayId
    if (dayId != null) {
        LaunchedEffect(dayId) {
            reliefAccessViewModel.resetActionStates()
            reliefAccessViewModel.loadRequests(dayId)
        }
    }
    SessionDashboardScreen(
        viewModel = dashboardViewModel,
        selection =
            DashboardSelection(
                branchName = context.branchName,
                sessionId = context.selectedSessionId,
                operationalDate = context.operationalDate,
            ),
        onSessionClick = { session -> onSessionClick(session.id) },
        // #348 — the dashboard's entry into the start-a-session flow.
        onSessionCreateClick = onSessionCreateClick,
        teamRequestCount = reliefRows?.incomingPending(context.userId)?.size,
        reliefAccessContent = {
            DashboardReliefContent(
                context = context,
                inviteViewModel = reliefInviteViewModel,
                viewModel = reliefAccessViewModel,
            )
        },
        attendanceContent = {
            // #404 — member-marked attendance; the card self-hides for relief users
            // (the server's membership gate 403s the read and the section renders nil).
            context.branchId?.let { clockedBranchId ->
                DesktopAttendanceContent(
                    isReliefUser = context.isReliefUser,
                    branchId = clockedBranchId,
                    branchName = context.branchName,
                    currentUserId = context.userId,
                    viewModel = attendanceViewModel,
                )
            }
        },
    )
}
