package com.companyb.companyapp.app

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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.SessionBootstrapViewModel
import com.companyb.companyapp.app.navigation.AppNavHost
import com.companyb.companyapp.app.navigation.Route
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.client.ClientState
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.network.createTokenStore
import com.companyb.companyapp.notification.NotificationState
import com.companyb.companyapp.ui.theme.LinearTheme
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import kotlinx.coroutines.flow.collectLatest

@Composable
fun App() {
    val tokenStore: TokenStore = remember { createTokenStore() }
    val apiClient = remember { ApiClient(tokenStore) }
    val navController = rememberNavController()
    // #94 — one bootstrap implementation shared by launch validation (here) and fresh login
    // (LoginScreen creates its own instance — same class, independent states).
    val bootstrapViewModel: SessionBootstrapViewModel = viewModel { SessionBootstrapViewModel(apiClient) }
    val validationState by bootstrapViewModel.validationState.collectAsState()

    // #94 Phase 1 — launch validation. A persisted token is cached evidence, not proof:
    // validate via GET /api/me → GET /api/me/capabilities before rendering the post-login
    // graph (silent-401 prevention, #94 Q1/Q4 pair).
    var hasToken by remember { mutableStateOf(tokenStore.getToken() != null) }
    var launchValidationActive by remember { mutableStateOf(tokenStore.getToken() != null) }

    val goToLogin: () -> Unit = {
        // #94 outline — "Go to Login (token preserved for next launch)": the network error
        // carries no auth information, so the token survives; next launch re-validates it.
        // The in-flight validation is cancelled so a late 200 can't repopulate the state
        // we're clearing (round-3 catch).
        bootstrapViewModel.cancelValidation()
        hasToken = false
        AppSessionState.clear()
        NotificationState.clear()
        ClientState.clear()
    }

    AppLaunchValidationEffects(
        tokenStore = tokenStore,
        bootstrapViewModel = bootstrapViewModel,
        validationState = validationState,
        onValidationActiveChange = { launchValidationActive = it },
    )

    LaunchedEffect(apiClient) {
        // #94 Q3 + #504 — path discriminates credential 401s (public auth → inline
        // form error, no global reaction) from session 401s (clear → Login). #504:
        // the event carries the credential that sent it; a delayed 401 from a
        // superseded session never clears the live one.
        apiClient.onUnauthorized.collectLatest { event ->
            if (!apiClient.isCurrentSessionEvent(event)) {
                return@collectLatest
            }
            // Read BEFORE clearing: the launch-401 is silent, the mid-session 401 is not.
            handleSessionUnauthorized(
                apiClient = apiClient,
                navController = navController,
                path = event.path,
                isLaunchValidationActive = { launchValidationActive },
                onSessionStateChange = { h, v ->
                    hasToken = h
                    launchValidationActive = v
                },
            )
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
                    tokenStore = apiClient.sessionTokens,
                    navController = navController,
                )
            }
        }
    }
}

@Composable
private fun AppLaunchValidationEffects(
    tokenStore: TokenStore,
    bootstrapViewModel: SessionBootstrapViewModel,
    validationState: UiState<Unit>,
    onValidationActiveChange: (Boolean) -> Unit,
) {
    LaunchedEffect(Unit) {
        val token = tokenStore.getToken()
        logInfo("App", "Initial auth check: hasToken=${token != null}")
        if (token != null) {
            bootstrapViewModel.validateSession()
        } else {
            AppSessionState.clear()
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
                onValidationActiveChange(true)
            }

            is UiState.Success, is UiState.Error -> {
                onValidationActiveChange(false)
            }

            else -> {}
        }
    }
}

@Suppress("TooGenericExceptionCaught") // #585 best-effort session clear must not throw (moved #555)
private suspend fun handleSessionUnauthorized(
    apiClient: ApiClient,
    navController: NavHostController,
    path: String,
    isLaunchValidationActive: () -> Boolean,
    onSessionStateChange: (hasToken: Boolean, validationActive: Boolean) -> Unit,
) {
    logInfo("App", "onUnauthorized on $path, clearing token + SessionState")
    try {
        apiClient.clearSession()
    } catch (e: Exception) {
        logError("App", "Failed to clear token on unauthorized", e)
    }
    // Read BEFORE clearing: the launch-401 is silent, the mid-session 401 is not.
    val wasLaunchValidation = isLaunchValidationActive()
    onSessionStateChange(false, false)
    AppSessionState.clear()
    NotificationState.clear()
    ClientState.clear()
    // The token is gone: launch validation is over either way. Leaving the flag true
    // would silently swallow every later mid-session 401 (no notice, no navigate —
    // the user stuck on a screen whose every call 403s).
    if (!wasLaunchValidation) {
        AppSessionState.setExpiredNotice(true)
        navController.navigate(Route.Login) {
            popUpTo(0) { inclusive = true }
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
