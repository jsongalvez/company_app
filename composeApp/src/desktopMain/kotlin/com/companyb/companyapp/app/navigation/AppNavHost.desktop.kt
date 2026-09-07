package com.companyb.companyapp.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

@Composable
actual fun AppNavHost(
    apiClient: ApiClient,
    tokenStore: TokenStore,
    navController: NavHostController,
    modifier: Modifier,
) {
    val start = startDestination()
    // #96 Q5 — shell wraps the post-clock-in sub-graph of one AppNavHost. Pre-shell routes
    // (Login, BranchSelect, AcceptInvite, ForgotPassword — #487) render full-screen:
    // empty `drawerContent` lambda produces a no-op
    // Row child, so Row collapses to just `Box { content() }` (verified at Material3 source
    // NavigationDrawer.kt:621-624). Post-shell routes get the permanent drawer on-screen by
    // structural always-on rendering (no parent-swap of NavHost — keeps NavController back-stack
    // stable across the boundary).
    val currentRoute = navController.currentRoute()
    val postClockIn = currentRoute.isPostClockIn()
    val sessionCreateNavigationLocked = rememberSessionCreateNavigationLock()
    val clientMutationInFlight by ClientState.clientMutationInFlight.collectAsState()
    NotificationBadgeHost(apiClient, postClockIn)

    CompositionLocalProvider(LocalNavHostController provides navController) {
        PermanentNavigationDrawer(
            drawerContent = {
                if (postClockIn) {
                    // #96 Q7 follow-up — PermanentNavigationDrawer's bare Row slot applies no
                    // padding/background/chrome; desktop needs explicit surface-1 background +
                    // width 360 (Material3 PermanentDrawerContainerWidth). Mobile ModalDrawerSheet
                    // provides these defaults.
                    DrawerContent(
                        apiClient = apiClient,
                        navigationEnabled =
                            shellNavigationEnabled(
                                sessionCreateNavigationLocked.value,
                                clientMutationInFlight,
                            ),
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
            NavHost(navController = navController, startDestination = start) {
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
    // #400 — parity with the mobile host: the dashboard carries the
    // entry-scoped relief-access card (the #112 self-cleaning VM shape).
    val reliefAccessViewModel: ReliefAccessViewModel =
        viewModel { ReliefAccessViewModel(apiClient) }
    // #404 — entry-scoped member-attendance roster (same self-cleaning shape).
    val attendanceViewModel: AttendanceRosterViewModel =
        viewModel { AttendanceRosterViewModel(apiClient) }
    val snapshot by AppSessionState.snapshot.collectAsState()
    val selectedBranchName = snapshot.clock?.branchName
    val selectedBranchId = snapshot.clock?.branchId
    val branchDayId = snapshot.clock?.branchDayId
    val currentUserId = snapshot.user
    val isRelief = snapshot.clock?.isRelief == true
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
                onSessionCreateClick = {
                    resetSessionCreateNavigationLock()
                    navController.navigate(Route.SessionCreate)
                },
                reliefAccessContent = {
                    DashboardReliefContent(
                        branchDayId = branchDayId,
                        viewModel = reliefAccessViewModel,
                        currentUserId = currentUserId?.id,
                        isReliefUser = isRelief,
                    )
                },
                attendanceContent = {
                    // #404 — member-marked attendance; the card self-hides for relief users
                    // (the server's membership gate 403s the read and the section renders nil).
                    selectedBranchId?.let { clockedBranchId ->
                        DesktopAttendanceContent(
                            isReliefUser = isRelief,
                            branchId = clockedBranchId,
                            branchName = selectedBranchName,
                            currentUserId = currentUserId?.id,
                            viewModel = attendanceViewModel,
                        )
                    }
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
