package com.companyb.companyapp.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.drawer.DrawerContent
import com.companyb.companyapp.client.ClientState
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.session.dashboard.DashboardSelection
import com.companyb.companyapp.session.dashboard.SessionDashboardScreen
import com.companyb.companyapp.session.dashboard.SessionDashboardViewModel
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
internal fun MobileAppNavHost(
    apiClient: ApiClient,
    tokenStore: TokenStore,
    navController: NavHostController,
    modifier: Modifier,
) {
    val start = startDestination()
    // #96 Q5 — shell wraps the post-clock-in sub-graph of one AppNavHost. Pre-shell routes
    // (Login, BranchSelect, AcceptInvite, ForgotPassword — #487) render full-screen: empty `drawerContent` lambda is invisible
    // inside ModalNavigationDrawer's Box (drawer closed + Sheet has no measurable children);
    // no Scaffold topBar pre-shell either, so NavHost gets full content area.
    val currentRoute = navController.currentRoute()
    val postClockIn = currentRoute.isPostClockIn()
    val sessionCreateNavigationLocked = rememberSessionCreateNavigationLock()
    val clientMutationInFlight by ClientState.clientMutationInFlight.collectAsState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    NotificationBadgeHost(apiClient, postClockIn)

    // #96 Q1 + Q5 — `gesturesEnabled = false` (below) is LOAD-BEARING. Prevents a three-way
    // edge-gesture collision (drawer swipe vs Android back-swipe vs pushed-route back-pop) at
    // the shell level across every post-clock-in route. Do NOT silently flip back to true.
    CompositionLocalProvider(LocalNavHostController provides navController) {
        ModalNavigationDrawer(
            drawerContent = {
                MobileDrawerSheet(
                    apiClient = apiClient,
                    postClockIn = postClockIn,
                    drawerState = drawerState,
                    navigationEnabled =
                        shellNavigationEnabled(
                            sessionCreateNavigationLocked.value,
                            clientMutationInFlight,
                        ),
                    onItemNavigated = { scope.launch { drawerState.close() } },
                )
            },
            drawerState = drawerState,
            gesturesEnabled = false,
            modifier = modifier,
        ) {
            Scaffold(
                topBar = {
                    MobileShellTopBar(
                        postClockIn = postClockIn,
                        onHamburgerClick = { scope.launch { drawerState.open() } },
                    )
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = start,
                    modifier = Modifier.padding(padding),
                ) {
                    mobileAppRoutes(
                        apiClient = apiClient,
                        tokenStore = tokenStore,
                        navController = navController,
                        navigationLocked = sessionCreateNavigationLocked,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileDrawerSheet(
    apiClient: ApiClient,
    postClockIn: Boolean,
    drawerState: DrawerState,
    navigationEnabled: Boolean,
    onItemNavigated: () -> Unit,
) {
    if (postClockIn) {
        // #96 Q5 close-out check (source-verified at Material3 NavigationDrawer.kt:1013):
        // DrawerPredictiveBackHandler is gated on `drawerState.isOpen`, NOT on
        // `gesturesEnabled=false`. Closed drawer + gesturesEnabled=false registers NO
        // back interceptor at the drawer, so pushed-route back-pop wins cleanly.
        ModalDrawerSheet(drawerState = drawerState) {
            // #389 — selection navigates underneath; close the sheet so the
            // chosen section is actually visible.
            DrawerContent(
                apiClient = apiClient,
                navigationEnabled = navigationEnabled,
                onItemNavigated = onItemNavigated,
            )
        }
    }
}

@Composable
private fun MobileShellTopBar(
    postClockIn: Boolean,
    onHamburgerClick: () -> Unit,
) {
    if (postClockIn) {
        // #671 — the one compact top bar: menu trigger + parent section title +
        // viewed branch/date (shared ShellTopBar; pushed detail routes keep their
        // single screen-owned back via nested-Scaffold per #96 Q5 — no second back).
        ShellTopBar(onMenuClick = onHamburgerClick)
    }
}

private fun NavGraphBuilder.mobileAppRoutes(
    apiClient: ApiClient,
    tokenStore: TokenStore,
    navController: NavHostController,
    navigationLocked: MutableState<Boolean>,
) {
    // #456 — one shared registration; this shell keeps only chrome +
    // the single-pane dashboard presentation below.
    appRouteGraph(
        apiClient = apiClient,
        tokenStore = tokenStore,
        navController = navController,
        onSubmissionLockChanged = { locked ->
            navigationLocked.value = locked
        },
        hooks =
            PlatformRouteHooks(
                dashboardLive = {
                    MobileDashboardLive(
                        apiClient = apiClient,
                        navController = navController,
                        resetSessionCreateNavigationLock = {
                            navigationLocked.value = false
                        },
                    )
                },
            ),
    )
}

@Composable
private fun MobileDashboardLive(
    apiClient: ApiClient,
    navController: NavHostController,
    resetSessionCreateNavigationLock: () -> Unit,
) {
    val dashboardViewModel: SessionDashboardViewModel =
        viewModel { SessionDashboardViewModel(apiClient) }
    // #351 — entry-scoped relief-access VM (the #112 self-cleaning shape).
    val reliefAccessViewModel: ReliefAccessViewModel =
        viewModel { ReliefAccessViewModel(apiClient) }
    // #680 — entry-scoped relief-invite VM: the Team Relief tab plans invitations from the
    // shift (same shared owner as the BranchSelect shortcut), so clocked-in members never
    // clock out merely to manage invitations.
    val reliefInviteViewModel: ReliefInviteViewModel =
        viewModel { ReliefInviteViewModel(apiClient) }
    // #404 — entry-scoped member-attendance roster (same self-cleaning shape).
    val attendanceViewModel: AttendanceRosterViewModel =
        viewModel { AttendanceRosterViewModel(apiClient) }
    val snapshot by AppSessionState.snapshot.collectAsState()
    val selectedBranchName = snapshot.clock?.branchName
    val selectedBranchId = snapshot.clock?.branchId
    val branchDayId = snapshot.clock?.branchDayId
    val currentUser = snapshot.user
    val currentUserId = currentUser
    val isRelief = snapshot.clock?.isRelief == true
    // #672 — the selected row survives the pushed-detail roundtrip per user+branch
    // (Back restores the highlighted row; the card-list anchor restores the scroll —
    // both ride the section context, so a branch switch rekeys clean).
    val userId = currentUser?.id
    var mobileSelectedId by remember(userId, selectedBranchId) {
        mutableStateOf(
            NavigationContextStore.retained(userId, selectedBranchId, Route.Dashboard())?.selectedId,
        )
    }
    // #672 — the relief rows load at the live-dashboard level (not inside the Team
    // sheet slot) so the toolbar's actionable count is live before the sheet opens.
    val reliefRows by reliefAccessViewModel.freshestRequests.collectAsState()
    val dayId = branchDayId
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
                branchName = selectedBranchName,
                sessionId = mobileSelectedId,
                operationalDate = snapshot.clock?.operationalDate,
            ),
        onSessionClick = { row ->
            // The dashboard path keeps passing the enriched row (zero extra
            // requests — the #152 session-detail GET exists now, but only the
            // notifications path fetches on null-row).
            mobileSelectedId = row.id
            NavigationContextStore.retain(userId, selectedBranchId, Route.Dashboard(), selectedId = row.id)
            navController.navigate(Route.SessionDetail(row.id, row))
        },
        // #348 — the dashboard's entry into the start-a-session flow.
        onSessionCreateClick = {
            resetSessionCreateNavigationLock()
            navController.navigate(Route.SessionCreate)
        },
        teamRequestCount = reliefRows?.incomingPending(currentUserId?.id)?.size,
        reliefAccessContent = {
            // #680 — the Relief tab: today's requests plus invitation planning for the
            // viewed branch/date (shared owner with BranchSelect).
            val clockedBranchId = selectedBranchId
            if (clockedBranchId != null) {
                ReliefPlanningTabContent(
                    branchId = clockedBranchId,
                    branchName = selectedBranchName,
                    viewedDate = snapshot.clock?.operationalDate,
                    branchDayId = dayId,
                    inviteViewModel = reliefInviteViewModel,
                    accessViewModel = reliefAccessViewModel,
                    currentUserId = currentUserId?.id,
                    isReliefUser = isRelief,
                )
            } else {
                // Transient pre-clock snapshot: the tab never renders blank without guidance.
                Text(
                    text = "Clock in to a branch to plan relief.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        },
        attendanceContent = {
            // #404 — member-marked attendance; the card self-hides for relief users
            // (the server's membership gate 403s the read and the section renders nil).
            val clockedBranchId = selectedBranchId
            if (!isRelief && clockedBranchId != null) {
                AttendanceRosterCard(
                    viewModel = attendanceViewModel,
                    branchId = clockedBranchId,
                    branchName = selectedBranchName,
                    currentUserId = currentUserId?.id,
                    isReliefUser = isRelief,
                )
            }
        },
    )
}
