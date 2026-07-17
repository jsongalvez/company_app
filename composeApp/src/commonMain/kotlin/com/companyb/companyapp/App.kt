package com.companyb.companyapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.network.createTokenStore
import com.companyb.companyapp.ui.screen.HomeScreen
import com.companyb.companyapp.ui.screen.LoginScreen
import com.companyb.companyapp.ui.theme.LinearTheme
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.AttendanceViewModel
import com.companyb.companyapp.viewmodel.AuthViewModel
import com.companyb.companyapp.viewmodel.BranchViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlinx.coroutines.flow.collectLatest

@Composable
fun App() {
    val tokenStore: TokenStore = remember { createTokenStore() }
    val apiClient = remember { ApiClient(tokenStore) }
    val authViewModel = remember { AuthViewModel(apiClient) }
    val branchViewModel = remember { BranchViewModel(apiClient) }
    val attendanceViewModel = remember { AttendanceViewModel(apiClient) }

    var isLoggedIn by remember { mutableStateOf(false) }
    var isCheckingAuth by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val hasToken = tokenStore.getToken() != null
        logInfo("App", "Initial auth check: hasToken=$hasToken")
        isLoggedIn = hasToken
        isCheckingAuth = false
    }

    val logoutState by authViewModel.logoutState.collectAsState()
    LaunchedEffect(logoutState) {
        when (logoutState) {
            is UiState.Success -> {
                logInfo("App", "logoutState=Success, clearing token, setting isLoggedIn=false")
                tokenStore.clearToken()
                isLoggedIn = false
            }

            is UiState.Error -> {
                logWarn("App", "logoutState=Error, clearing token anyway, setting isLoggedIn=false")
                tokenStore.clearToken()
                isLoggedIn = false
            }

            else -> {}
        }
    }

    LaunchedEffect(Unit) {
        apiClient.onUnauthorized.collectLatest {
            logInfo("App", "onUnauthorized received, clearing token, navigating to login")
            try {
                tokenStore.clearToken()
            } catch (e: Exception) {
                logError("App", "Failed to clear token on unauthorized", e)
            }
            isLoggedIn = false
        }
    }

    logInfo("App", "isLoggedIn=$isLoggedIn, isCheckingAuth=$isCheckingAuth")

    if (isCheckingAuth) return

    LinearTheme {
        if (isLoggedIn) {
            HomeScreen(
                authViewModel = authViewModel,
                branchViewModel = branchViewModel,
                attendanceViewModel = attendanceViewModel,
                apiClient = apiClient,
            )
        } else {
            LoginScreen(
                authViewModel = authViewModel,
                tokenStore = tokenStore,
                onLoginSuccess = {
                    logInfo("App", "onLoginSuccess called, setting isLoggedIn=true")
                    isLoggedIn = true
                },
                onRegisterClick = { /* TODO: navigate to register screen */ },
            )
        }
    }
}
