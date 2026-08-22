package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.validation.PasswordPolicy
import com.companyb.companyapp.viewmodel.AuthViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * #353 — public forgot-password flow, both legs on one screen: request a reset code by
 * username or email, then redeem it with a new password. The 204 after a request is uniform
 * by design — the confirmation copy must not imply an account exists. Redemption lands back
 * at Login (no auto-session), like invite acceptance (#350).
 *
 * Reached from LoginScreen's "Forgot password?" link. Deep links are out of scope (#350).
 */
@Composable
fun ForgotPasswordScreen(
    authViewModel: AuthViewModel,
    tokenStore: TokenStore,
    onDone: () -> Unit,
) {
    var identifier by remember { mutableStateOf("") }
    var resetCode by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var policyError by remember { mutableStateOf(false) }
    val requestState by authViewModel.requestResetState.collectAsState()
    val resetState by authViewModel.resetPasswordState.collectAsState()

    LaunchedEffect(Unit) {
        logInfo("ForgotPasswordScreen", "composable entered (first composition)")
    }

    // A stale token in storage must not survive into a fresh session started from here.
    val hasToken = tokenStore.getToken() != null

    LaunchedEffect(resetState) {
        when (val state = resetState) {
            is UiState.Success -> {
                logInfo("ForgotPasswordScreen", "password reset; returning to Login")
                if (hasToken) tokenStore.clearToken()
                onDone()
            }

            is UiState.Error -> {
                logWarn("ForgotPasswordScreen", "resetState=Error: ${state.message}")
            }

            else -> {}
        }
    }

    val isRequesting = requestState is UiState.Loading
    val isResetting = resetState is UiState.Loading

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Reset your password",
            style = MaterialTheme.typography.headlineLarge,
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        Text(
            text = "Request a reset code with your username or email.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.xxl))

        OutlinedTextField(
            value = identifier,
            onValueChange = { identifier = it },
            label = { Text("Username or email") },
            singleLine = true,
            enabled = !isRequesting,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Button(
            onClick = { authViewModel.requestPasswordReset(identifier.trim()) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = identifier.isNotBlank() && !isRequesting,
        ) {
            if (isRequesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Request reset code")
            }
        }

        when (val state = requestState) {
            is UiState.Success -> {
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text =
                        "Request received. If a reset code was created for you, " +
                            "get it from the operator and enter it below.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is UiState.Error -> {
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> {}
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(Spacing.xl))

        OutlinedTextField(
            value = resetCode,
            onValueChange = { resetCode = it },
            label = { Text("Reset code") },
            singleLine = true,
            enabled = !isResetting,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        OutlinedTextField(
            value = newPassword,
            onValueChange = {
                newPassword = it
                policyError = false
            },
            label = { Text("New password") },
            supportingText = { Text("At least ${PasswordPolicy.MIN_LENGTH} characters.") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = policyError,
            enabled = !isResetting,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.lg))

        Button(
            onClick = {
                if (!PasswordPolicy.isValid(newPassword)) {
                    policyError = true
                    return@Button
                }
                authViewModel.resetPassword(resetCode.trim(), newPassword)
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = resetCode.isNotBlank() && newPassword.isNotBlank() && !isResetting,
        ) {
            if (isResetting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Set new password")
            }
        }

        when (val state = resetState) {
            is UiState.Error -> {
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> {}
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        TextButton(onClick = onDone, enabled = !isRequesting && !isResetting) {
            Text("Back to login")
        }
    }
}
