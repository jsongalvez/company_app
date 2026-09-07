package com.companyb.companyapp.app.auth

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
import com.companyb.companyapp.app.AuthViewModel
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.validation.PasswordPolicy

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
    val form = remember { ForgotPasswordForm() }
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

        RequestLeg(authViewModel, form)

        Spacer(modifier = Modifier.height(Spacing.xl))

        HorizontalDivider()

        Spacer(modifier = Modifier.height(Spacing.xl))

        ResetLeg(authViewModel, form)

        Spacer(modifier = Modifier.height(Spacing.lg))

        TextButton(onClick = onDone, enabled = !isRequesting && !isResetting) {
            Text("Back to login")
        }
    }
}

/** The screen's three fields plus the policy-error flag; one holder per leg param. */
private class ForgotPasswordForm {
    var identifier by mutableStateOf("")
    var resetCode by mutableStateOf("")
    var newPassword by mutableStateOf("")
    var policyError by mutableStateOf(false)
}

/** Leg 1: request the reset code (the response is uniform — enumeration resistance). */
@Composable
private fun RequestLeg(
    authViewModel: AuthViewModel,
    form: ForgotPasswordForm,
) {
    val requestState by authViewModel.requestResetState.collectAsState()
    val isRequesting = requestState is UiState.Loading

    OutlinedTextField(
        value = form.identifier,
        onValueChange = { form.identifier = it },
        label = { Text("Username or email") },
        singleLine = true,
        enabled = !isRequesting,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(Spacing.lg))

    BusySubmitButton(
        label = "Request reset code",
        enabled = form.identifier.isNotBlank() && !isRequesting,
        isBusy = isRequesting,
        onClick = { authViewModel.requestPasswordReset(form.identifier.trim()) },
    )

    when (val state = requestState) {
        is UiState.Success -> {
            Spacer(modifier = Modifier.height(Spacing.md))
            Text(
                text =
                    "Request received. If an account matches and email delivery is available, " +
                        "check its email for a reset code and enter it below. If no email arrives, " +
                        "contact your owner.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is UiState.Error -> {
            InlineError(state.message)
        }

        else -> {}
    }
}

/** Leg 2: redeem the code with a new password (client-side policy pre-check). */
@Composable
private fun ResetLeg(
    authViewModel: AuthViewModel,
    form: ForgotPasswordForm,
) {
    val resetState by authViewModel.resetPasswordState.collectAsState()
    val isResetting = resetState is UiState.Loading

    OutlinedTextField(
        value = form.resetCode,
        onValueChange = { form.resetCode = it },
        label = { Text("Reset code") },
        singleLine = true,
        enabled = !isResetting,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(Spacing.md))

    OutlinedTextField(
        value = form.newPassword,
        onValueChange = {
            form.newPassword = it
            form.policyError = false
        },
        label = { Text("New password") },
        supportingText = { Text("At least ${PasswordPolicy.MIN_LENGTH} characters.") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = form.policyError,
        enabled = !isResetting,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(Spacing.lg))

    BusySubmitButton(
        label = "Set new password",
        enabled = form.resetCode.isNotBlank() && form.newPassword.isNotBlank() && !isResetting,
        isBusy = isResetting,
        onClick = {
            if (PasswordPolicy.isValid(form.newPassword)) {
                authViewModel.resetPassword(form.resetCode.trim(), form.newPassword)
            } else {
                form.policyError = true
            }
        },
    )

    when (val state = resetState) {
        is UiState.Error -> {
            InlineError(state.message)
        }

        else -> {}
    }
}

/** The shared full-width submit button with its inline busy spinner (auth flow screens). */
@Composable
internal fun BusySubmitButton(
    label: String,
    enabled: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        enabled = enabled,
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(label)
        }
    }
}

@Composable
internal fun InlineError(message: String) {
    Spacer(modifier = Modifier.height(Spacing.md))
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
}
