package com.companyb.companyapp.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.drawer.DrawerContent
import com.companyb.companyapp.app.drawer.HamburgerWithBadge
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.state.ClientState
import com.companyb.companyapp.state.NotificationState
import com.companyb.companyapp.ui.screen.AttendanceRosterCard
import com.companyb.companyapp.ui.screen.DashboardSelection
import com.companyb.companyapp.ui.screen.ReliefAccessCard
import com.companyb.companyapp.ui.screen.SessionDashboardScreen
import com.companyb.companyapp.viewmodel.AttendanceRosterViewModel
import com.companyb.companyapp.viewmodel.ReliefAccessViewModel
import com.companyb.companyapp.viewmodel.SessionDashboardViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileShellTopBar(
    postClockIn: Boolean,
    onHamburgerClick: () -> Unit,
) {
    if (postClockIn) {
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
                    onClick = onHamburgerClick,
                    unreadCount = badgeCount,
                )
            },
        )
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
    SessionDashboardScreen(
        viewModel = dashboardViewModel,
        selection = DashboardSelection(branchName = selectedBranchName, sessionId = null),
        onSessionClick = { row ->
            // The dashboard path keeps passing the enriched row (zero extra
            // requests — the #152 session-detail GET exists now, but only the
            // notifications path fetches on null-row).
            navController.navigate(Route.SessionDetail(row.id, row))
        },
        // #348 — the dashboard's entry into the start-a-session flow.
        onSessionCreateClick = {
            resetSessionCreateNavigationLock()
            navController.navigate(Route.SessionCreate)
        },
        reliefAccessContent = {
            val dayId = branchDayId
            if (dayId != null) {
                LaunchedEffect(dayId) {
                    reliefAccessViewModel.resetActionStates()
                    reliefAccessViewModel.loadRequests(dayId)
                }
                ReliefAccessCard(
                    viewModel = reliefAccessViewModel,
                    branchDayId = dayId,
                    currentUserId = currentUserId?.id,
                    isReliefUser = isRelief,
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
