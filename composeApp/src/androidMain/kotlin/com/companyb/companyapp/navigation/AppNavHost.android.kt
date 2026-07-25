package com.companyb.companyapp.navigation

import androidx.compose.foundation.layout.Box
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

@Composable
private fun PlaceholderRoute(label: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$label — pending build ticket", style = MaterialTheme.typography.bodyLarge)
    }
}
