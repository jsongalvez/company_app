package com.companyb.companyapp.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.ui.screen.LoginScreen
import com.companyb.companyapp.viewmodel.AuthViewModel

@Composable
actual fun AppNavHost(
    apiClient: ApiClient,
    tokenStore: TokenStore,
    navController: NavHostController,
    modifier: Modifier,
) {
    val startDestination: Route = if (tokenStore.getToken() != null) Route.Dashboard else Route.Login

    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        composable<Route.Login> {
            LoginScreen(
                authViewModel = remember { AuthViewModel(apiClient) },
                tokenStore = tokenStore,
                onLoginSuccess = {
                    navController.navigate(Route.BranchSelect) {
                        popUpTo(Route.Login) { inclusive = true }
                    }
                },
                onRegisterClick = { /* register route — out of scope, pending #94-grad */ },
            )
        }
        composable<Route.BranchSelect> { PlaceholderRoute("BranchSelect") }
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
        composable<Route.Clients> { PlaceholderRoute("Clients") }
        composable<Route.Inventory> { PlaceholderRoute("Inventory") }
        composable<Route.Finance> { PlaceholderRoute("Finance") }
        composable<Route.RemittanceList> { PlaceholderRoute("Remittance List") }
        composable<Route.RemittanceDetail> { PlaceholderRoute("Remittance Detail") }
        composable<Route.Notifications> { PlaceholderRoute("Notifications") }
        composable<Route.AuditLog> { PlaceholderRoute("Audit Log") }
        composable<Route.Reports> { PlaceholderRoute("Reports") }
        composable<Route.UserManagement> { PlaceholderRoute("User Management") }
        // No composable<Route.SessionDetail> on desktop — locked by #91 (desktop inline-pane only).
    }
}

@Composable
private fun PlaceholderRoute(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$label — pending build ticket", style = MaterialTheme.typography.bodyLarge)
    }
}
