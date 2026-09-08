package com.companyb.companyapp.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.drawer.DrawerContent
import com.companyb.companyapp.client.ClientState
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.session.dashboard.DashboardSelection
import com.companyb.companyapp.session.dashboard.SessionDashboardScreen
import com.companyb.companyapp.session.dashboard.SessionDashboardViewModel
import com.companyb.companyapp.session.detail.SessionDetailPane
import com.companyb.companyapp.workforce.attendance.AttendanceRosterCard
import com.companyb.companyapp.workforce.attendance.AttendanceRosterViewModel
import com.companyb.companyapp.workforce.relief.ReliefAccessCard
import com.companyb.companyapp.workforce.relief.ReliefAccessViewModel
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
                )
            } else {
                NarrowDesktopShell(
                    apiClient = apiClient,
                    postClockIn = postClockIn,
                    navigationEnabled = navigationEnabled,
                    navContent = navContent,
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
        )
    }
}

private const val DESKTOP_MASTER_WEIGHT = 0.6f
private const val DESKTOP_DETAIL_WEIGHT = 0.4f

@Composable
private fun DashboardReliefContent(
    branchDayId: String?,
    viewModel: ReliefAccessViewModel,
    currentUserId: String?,
    isReliefUser: Boolean,
) {
    val dayId = branchDayId
    if (dayId != null) {
        LaunchedEffect(dayId) {
            viewModel.resetActionStates()
            viewModel.loadRequests(dayId)
        }
        ReliefAccessCard(
            viewModel = viewModel,
            branchDayId = dayId,
            currentUserId = currentUserId,
            isReliefUser = isReliefUser,
        )
    }
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

// #447 — desk queue selection: re-clicking the open draft is a no-op, anything else
// pushes the existing detail route (no re-routing; the branch keeps its own decision).
private fun navigateDeskQueue(
    navController: NavHostController,
    currentId: String,
    id: String,
) {
    if (id != currentId) {
        navController.navigate(Route.RemittanceDetail(id))
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
    val snapshot by AppSessionState.snapshot.collectAsState()
    val lastData by dashboardViewModel.lastData.collectAsState()
    // #671 — section working context: the selected session survives section switches
    // (retained per user+branch; a branch switch rekeys and starts unselected, so
    // old-branch rows never present as new-branch data).
    val masterContext =
        DashboardMasterContext(
            branchName = snapshot.clock?.branchName,
            branchId = snapshot.clock?.branchId,
            branchDayId = snapshot.clock?.branchDayId,
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
    var selectedSessionId by remember(masterContext.userId, masterContext.branchId) {
        mutableStateOf(masterContext.selectedSessionId)
    }
    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(DESKTOP_MASTER_WEIGHT).fillMaxSize()) {
            DesktopMasterPane(
                apiClient = apiClient,
                dashboardViewModel = dashboardViewModel,
                context = masterContext.copy(selectedSessionId = selectedSessionId),
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
            )
        }
        Box(modifier = Modifier.weight(DESKTOP_DETAIL_WEIGHT).fillMaxSize()) {
            DesktopDetailPane(
                session = lastData?.sessions?.firstOrNull { it.id == selectedSessionId },
                apiClient = apiClient,
                onRefresh = { dashboardViewModel.refreshAfterMutation() },
            )
        }
    }
}

/**
 * #671 — the dashboard's snapshot-derived view context: every ambient input the
 * master pane renders but never mutates, read once per composition from the single
 * session snapshot. One bundle because the six legs are always derived together;
 * the selected session itself stays in nav state (retained per user+branch).
 */
private data class DashboardMasterContext(
    val branchName: String?,
    val branchId: String?,
    val branchDayId: String?,
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
    val attendanceViewModel: AttendanceRosterViewModel =
        viewModel { AttendanceRosterViewModel(apiClient) }
    SessionDashboardScreen(
        viewModel = dashboardViewModel,
        selection =
            DashboardSelection(
                branchName = context.branchName,
                sessionId = context.selectedSessionId,
            ),
        onSessionClick = { session -> onSessionClick(session.id) },
        // #348 — the dashboard's entry into the start-a-session flow.
        onSessionCreateClick = onSessionCreateClick,
        reliefAccessContent = {
            DashboardReliefContent(
                branchDayId = context.branchDayId,
                viewModel = reliefAccessViewModel,
                currentUserId = context.userId,
                isReliefUser = context.isReliefUser,
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
