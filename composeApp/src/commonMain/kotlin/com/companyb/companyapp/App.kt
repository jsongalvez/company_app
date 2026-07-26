package com.companyb.companyapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.companyb.companyapp.navigation.AppNavHost
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.network.createTokenStore
import com.companyb.companyapp.state.NotificationState
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.ui.theme.LinearTheme
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import kotlinx.coroutines.flow.collectLatest

@Composable
fun App() {
    val tokenStore: TokenStore = remember { createTokenStore() }
    val apiClient = remember { ApiClient(tokenStore) }
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        val hasToken = tokenStore.getToken() != null
        logInfo("App", "Initial auth check: hasToken=$hasToken")
        // Full POST-login GET /api/me → SessionState.setUser + setCapabilities wiring is deferred to #94-grad
        // build ticket. Foundation only clears SessionState if token absent — initial token-presence drives
        // AppNavHost's startDestination (Login or Dashboard).
        if (!hasToken) {
            SessionState.clear()
            NotificationState.clear()
        }
    }

    LaunchedEffect(apiClient) {
        apiClient.onUnauthorized.collectLatest {
            logInfo("App", "onUnauthorized received, clearing token + SessionState")
            try {
                tokenStore.clearToken()
            } catch (e: Exception) {
                logError("App", "Failed to clear token on unauthorized", e)
            }
            SessionState.clear()
            NotificationState.clear()
            navController.navigate(com.companyb.companyapp.navigation.Route.Login) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    LinearTheme {
        AppNavHost(
            apiClient = apiClient,
            tokenStore = tokenStore,
            navController = navController,
        )
    }
}
