package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.LoginResponse
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.AuthViewModel
import com.companyb.companyapp.viewmodel.SessionBootstrapViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * #94 Phase 2 — fresh login. One continuous loading from button-press to BranchSelect-render:
 * POST /auth/login → save token → validateSession (GET /api/me → capabilities, same shared
 * implementation as the launch splash). The screen holds on Login while EITHER the login or
 * the bootstrap is in flight (#94 Q2 — never navigate to a screen whose backing state isn't
 * ready).
 *
 * Error copy per the #94 Q3 table: credential/rate-limit/network failures on /auth/login are
 * inline and keep the form enabled (no token saved); a network failure after login success
 * (bootstrap) is inline too but the token IS saved — the login itself succeeded.
 */
@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    bootstrapViewModel: SessionBootstrapViewModel,
    tokenStore: TokenStore,
    onLoginSuccess: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showExpiredNotice by remember { mutableStateOf(false) }
    val loginState by authViewModel.loginState.collectAsState()
    val bootstrapState by bootstrapViewModel.validationState.collectAsState()
    val expiredNotice by SessionState.expiredNotice.collectAsState()

    LaunchedEffect(Unit) {
        logInfo("LoginScreen", "composable entered (first composition)")
    }

    // #94 Q3c(ii) — mid-session 401 → Login with "session expired" (launch 401s are silent;
    // App.kt only sets the notice for post-splash 401s). Consumed once, cleared on display.
    LaunchedEffect(expiredNotice) {
        if (expiredNotice) {
            showExpiredNotice = true
            SessionState.setExpiredNotice(false)
        }
    }

    LaunchedEffect(loginState) {
        when (val state = loginState) {
            is UiState.Success<LoginResponse> -> {
                logInfo("LoginScreen", "loginState=Success, saving token + bootstrapping session")
                tokenStore.saveToken(state.data.token)
                bootstrapViewModel.validateSession()
            }

            is UiState.Error -> {
                logWarn("LoginScreen", "loginState=Error: ${state.message}")
            }

            else -> {}
        }
    }

    LaunchedEffect(bootstrapState) {
        when (val state = bootstrapState) {
            is UiState.Success -> {
                // Token gate: in the (near-impossible) caps-leg-401 ordering — a token /api/me
                // just accepted but /api/me/capabilities rejects — the App-level 401 handler
                // clears the token and navigates to Login; navigating BranchSelect here would
                // race it. The token is the real discriminator for "session validated".
                if (tokenStore.getToken() != null) {
                    logInfo("LoginScreen", "bootstrapState=Success, calling onLoginSuccess")
                    onLoginSuccess()
                }
            }

            is UiState.Error -> {
                logWarn("LoginScreen", "bootstrapState=Error: ${state.message}")
            }

            else -> {}
        }
    }

    val isLoading = loginState is UiState.Loading || bootstrapState is UiState.Loading
    val inlineError =
        when {
            // Bootstrap copy only while the login itself isn't the failing leg: a stale
            // bootstrap Error must not mask a fresh credential error on the next attempt
            // (round-3 catch — wrong password after a failed bootstrap showed the network copy).
            bootstrapState is UiState.Error && loginState !is UiState.Error -> "Could not reach the server."

            else -> loginErrorText(loginState)
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "CompanyApp",
            style = MaterialTheme.typography.headlineLarge,
        )

        Spacer(modifier = Modifier.height(Spacing.xxl))

        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                showExpiredNotice = false
            },
            label = { Text("Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
            enabled = !isLoading,
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                showExpiredNotice = false
            },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation =
                if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
            keyboardActions =
                KeyboardActions(
                    onDone = {
                        if (username.isNotBlank() && password.isNotBlank()) {
                            showExpiredNotice = false
                            authViewModel.login(username, password)
                        }
                    },
                ),
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Button(
            onClick = {
                logInfo("LoginScreen", "login button onClick: username=$username")
                showExpiredNotice = false
                authViewModel.login(username, password)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled =
                username.isNotBlank() &&
                    password.isNotBlank() &&
                    !isLoading,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Log in")
            }
        }

        when {
            showExpiredNotice -> {
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = "Your session has expired. Please log in again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            inlineError != null -> {
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = inlineError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * #94 Q3 — inline error copy by failure class. ApiCallHandler's deterministic message format
 * ("$operation failed: ${status}") is the discriminator — matched on the full "failed: NNN"
 * suffix (not a bare "401" substring, which a network exception message could contain);
 * everything else (network exceptions, timeouts) is the connection copy. Form stays enabled;
 * credentials keep their values.
 */
internal fun loginErrorText(state: UiState<LoginResponse>): String? =
    when {
        state is UiState.Error && "failed: 401" in state.message -> "Invalid username or password."
        state is UiState.Error && "failed: 429" in state.message -> "Too many attempts. Try again later."
        state is UiState.Error -> "Could not reach the server. Check your connection and try again."
        else -> null
    }
