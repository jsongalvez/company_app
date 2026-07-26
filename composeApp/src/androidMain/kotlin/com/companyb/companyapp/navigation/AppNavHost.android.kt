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
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.state.NotificationState
import com.companyb.companyapp.ui.drawer.DrawerContent
import com.companyb.companyapp.ui.drawer.HamburgerWithBadge
import com.companyb.companyapp.ui.screen.LoginScreen
import com.companyb.companyapp.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun AppNavHost(
    apiClient: ApiClient,
    tokenStore: TokenStore,
    navController: NavHostController,
    modifier: Modifier,
) {
    val startDestination: Route = if (tokenStore.getToken() != null) Route.Dashboard else Route.Login
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
                    composable<Route.BranchSelect> { PlaceholderRoute("BranchSelect") }
                    composable<Route.Dashboard> { PlaceholderRoute("Dashboard") }
                    composable<Route.Clients> { PlaceholderRoute("Clients") }
                    composable<Route.Inventory> { PlaceholderRoute("Inventory") }
                    composable<Route.Finance> { PlaceholderRoute("Finance") }
                    composable<Route.RemittanceList> { PlaceholderRoute("Remittance List") }
                    composable<Route.RemittanceDetail> { PlaceholderRoute("Remittance Detail") }
                    composable<Route.Notifications> { PlaceholderRoute("Notifications") }
                    composable<Route.AuditLog> { PlaceholderRoute("Audit Log") }
                    composable<Route.Reports> { PlaceholderRoute("Reports") }
                    composable<Route.UserManagement> { PlaceholderRoute("User Management") }
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
