package com.companyb.companyapp

import androidx.compose.material3.MaterialTheme
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
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.viewmodel.AttendanceViewModel
import com.companyb.companyapp.viewmodel.AuthViewModel
import com.companyb.companyapp.viewmodel.BranchViewModel
import com.companyb.companyapp.viewmodel.UiState

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
        if (logoutState is UiState.Success) {
            logInfo("App", "logoutState=Success, clearing token, setting isLoggedIn=false")
            tokenStore.clearToken()
            isLoggedIn = false
        }
    }

    logInfo("App", "isLoggedIn=$isLoggedIn, isCheckingAuth=$isCheckingAuth")

    if (isCheckingAuth) return

    MaterialTheme {
        if (isLoggedIn) {
            HomeScreen(
                authViewModel = authViewModel,
                branchViewModel = branchViewModel,
                attendanceViewModel = attendanceViewModel,
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
