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
import com.companyb.companyapp.viewmodel.AuthViewModel
import com.companyb.companyapp.viewmodel.UiState

@Composable
fun App() {
    val tokenStore: TokenStore = remember { createTokenStore() }
    val apiClient = remember { ApiClient(tokenStore) }
    val authViewModel = remember { AuthViewModel(apiClient) }

    var isLoggedIn by remember { mutableStateOf(false) }
    var isCheckingAuth by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoggedIn = tokenStore.getToken() != null
        isCheckingAuth = false
    }

    val logoutState by authViewModel.logoutState.collectAsState()
    LaunchedEffect(logoutState) {
        if (logoutState is UiState.Success) {
            tokenStore.clearToken()
            isLoggedIn = false
        }
    }

    if (isCheckingAuth) return

    MaterialTheme {
        if (isLoggedIn) {
            HomeScreen(
                authViewModel = authViewModel,
                onLogout = {
                    tokenStore.clearToken()
                    isLoggedIn = false
                },
            )
        } else {
            LoginScreen(
                authViewModel = authViewModel,
                tokenStore = tokenStore,
                onLoginSuccess = { isLoggedIn = true },
                onRegisterClick = { /* TODO: navigate to register screen */ },
            )
        }
    }
}
