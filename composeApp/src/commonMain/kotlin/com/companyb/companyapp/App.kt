package com.companyb.companyapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.navigation.AppNavHost
import com.companyb.companyapp.navigation.Route
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.network.createTokenStore
import com.companyb.companyapp.state.ClientState
import com.companyb.companyapp.state.NotificationState
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.ui.theme.LinearTheme
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.SessionBootstrapViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlinx.coroutines.flow.collectLatest

@Composable
fun App() {
    val tokenStore: TokenStore = remember { createTokenStore() }
    val apiClient = remember { ApiClient(tokenStore) }
    val navController = rememberNavController()
    // #94 — one bootstrap implementation shared by launch validation (here) and fresh login
    // (LoginScreen creates its own instance — same class, independent states).
    val bootstrapViewModel = remember { SessionBootstrapViewModel(apiClient) }
    val validationState by bootstrapViewModel.validationState.collectAsState()

    // #94 Phase 1 — launch validation. A persisted token is cached evidence, not proof:
    // validate via GET /api/me → GET /api/me/capabilities before rendering the post-login
    // graph (silent-401 prevention, #94 Q1/Q4 pair).
    var hasToken by remember { mutableStateOf(tokenStore.getToken() != null) }
    var launchValidationActive by remember { mutableStateOf(tokenStore.getToken() != null) }

    val goToLogin: () -> Unit = {
        try {
            tokenStore.clearToken()
        } catch (e: Exception) {
            logError("App", "Failed to clear token on go-to-login", e)
        }
        hasToken = false
        SessionState.clear()
        NotificationState.clear()
        ClientState.clear()
    }

    LaunchedEffect(Unit) {
        val token = tokenStore.getToken()
        logInfo("App", "Initial auth check: hasToken=${token != null}")
        if (token != null) {
            bootstrapViewModel.validateSession()
        } else {
            SessionState.clear()
            NotificationState.clear()
            ClientState.clear()
        }
    }

    LaunchedEffect(validationState) {
        // #94 Q3c — launch-validation 401s are silent (the splash derives from hasToken); a
        // mid-session 401 after the flag flips is an expired-session 401 (message + navigate).
        // Re-entry to Loading (splash retry) re-arms the silent window.
        when (validationState) {
            UiState.Loading -> {
                launchValidationActive = true
            }

            is UiState.Success, is UiState.Error -> {
                launchValidationActive = false
            }

            else -> {}
        }
    }

    LaunchedEffect(apiClient) {
        // #94 Q3 — the 401'd path discriminates credential 401s (login/register → inline
        // form error, no global reaction) from session 401s (clear token → Login). Mid-session
        // 401s carry the "session expired" notice (Q3c(ii)); launch-validation 401s stay
        // silent (Q3c(i)) — the splash derives from hasToken, so clearing is enough. The
        // navigate is skipped while launch validation is active: no NavHost graph is composed
        // yet (pre-NavHost navigate would throw).
        apiClient.onUnauthorized.collectLatest { path ->
            if (path.endsWith(ApiRoutes.AUTH_LOGIN) || path.endsWith(ApiRoutes.AUTH_REGISTER)) {
                return@collectLatest
            }
            logInfo("App", "onUnauthorized on $path, clearing token + SessionState")
            try {
                tokenStore.clearToken()
            } catch (e: Exception) {
                logError("App", "Failed to clear token on unauthorized", e)
            }
            // Read BEFORE clearing: the launch-401 is silent, the mid-session 401 is not.
            val wasLaunchValidation = launchValidationActive
            hasToken = false
            SessionState.clear()
            NotificationState.clear()
            ClientState.clear()
            // The token is gone: launch validation is over either way. Leaving the flag true
            // would silently swallow every later mid-session 401 (no notice, no navigate —
            // the user stuck on a screen whose every call 403s).
            launchValidationActive = false
            if (!wasLaunchValidation) {
                SessionState.setExpiredNotice(true)
                navController.navigate(Route.Login) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    LinearTheme {
        when {
            // Phase-1 splash: token present but validation unresolved (Idle = first frame
            // before the LaunchedEffect fires, Loading = in flight, Error = retry state).
            // Neither Login (would flash then disappear) nor BranchSelect (needs currentUser)
            // is a valid destination yet — #94 Q2's one-loading-state-per-phase principle.
            hasToken && validationState !is UiState.Success -> {
                LaunchValidationSplash(
                    errorMessage = (validationState as? UiState.Error)?.message,
                    onRetry = { bootstrapViewModel.validateSession() },
                    onGoToLogin = goToLogin,
                )
            }

            else -> {
                AppNavHost(
                    apiClient = apiClient,
                    tokenStore = tokenStore,
                    navController = navController,
                )
            }
        }
    }
}

@Composable
private fun LaunchValidationSplash(
    errorMessage: String?,
    onRetry: () -> Unit,
    onGoToLogin: () -> Unit,
) {
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            logWarn("App", "launch validation error: $errorMessage")
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (errorMessage == null) {
            CircularProgressIndicator()
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(modifier = Modifier.height(Spacing.md))
                OutlinedButton(onClick = onRetry) {
                    Text("Retry")
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                TextButton(onClick = onGoToLogin) {
                    Text("Go to Login")
                }
            }
        }
    }
}
