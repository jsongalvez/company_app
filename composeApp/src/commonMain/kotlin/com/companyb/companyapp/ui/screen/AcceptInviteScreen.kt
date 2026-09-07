package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.network.TokenStore
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.validation.PasswordPolicy
import com.companyb.companyapp.viewmodel.AuthViewModel

/**
 * #350 — public accept-invite screen. The admin-minted code is the only credential for this
 * flow: the invitee pastes it, sets their own password (PasswordPolicy enforced inline before
 * the call; the backend re-checks), and lands back at Login to sign in — no auto-session
 * (the code proves identity once; login proves memorization).
 *
 * Reached from LoginScreen's "have an invite code?" link. Deep links are out of scope (#350).
 */
@Composable
fun AcceptInviteScreen(
    authViewModel: AuthViewModel,
    tokenStore: TokenStore,
    onDone: () -> Unit,
) {
    val form = remember { AcceptInviteForm() }
    val acceptState by authViewModel.acceptInviteState.collectAsState()

    AcceptInviteEffects(authViewModel, tokenStore, onDone)

    val isLoading = acceptState is UiState.Loading

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Set up your account",
            style = MaterialTheme.typography.headlineLarge,
        )

        Spacer(modifier = Modifier.height(Spacing.xs))

        Text(
            text = "Paste the invite code an administrator shared with you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(Spacing.xxl))

        AcceptInviteFields(form, isLoading)

        Spacer(modifier = Modifier.height(Spacing.lg))

        BusySubmitButton(
            label = "Set password",
            enabled = form.inviteCode.isNotBlank() && form.password.isNotBlank() && !isLoading,
            isBusy = isLoading,
            onClick = {
                if (PasswordPolicy.isValid(form.password)) {
                    authViewModel.acceptInvite(form.inviteCode.trim(), form.password)
                } else {
                    form.policyError = true
                }
            },
        )

        when (val state = acceptState) {
            is UiState.Error -> {
                InlineError(state.message)
            }

            else -> {}
        }

        Spacer(modifier = Modifier.height(Spacing.lg))

        TextButton(onClick = onDone, enabled = !isLoading) {
            Text("Back to login")
        }
    }
}

/** Entry log + the success landing (stale-token clear, return to Login) + error logging. */
@Composable
private fun AcceptInviteEffects(
    authViewModel: AuthViewModel,
    tokenStore: TokenStore,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        logInfo("AcceptInviteScreen", "composable entered (first composition)")
    }

    // A stale token in storage must not survive into a fresh session started from here.
    val hasToken = tokenStore.getToken() != null

    val acceptState by authViewModel.acceptInviteState.collectAsState()
    LaunchedEffect(acceptState) {
        when (val state = acceptState) {
            is UiState.Success -> {
                logInfo("AcceptInviteScreen", "invite accepted; returning to Login")
                if (hasToken) tokenStore.clearToken()
                onDone()
            }

            is UiState.Error -> {
                logWarn("AcceptInviteScreen", "acceptState=Error: ${state.message}")
            }

            else -> {}
        }
    }
}

/** The screen's fields plus the policy-error flag; one holder for the field group. */
private class AcceptInviteForm {
    var inviteCode by mutableStateOf("")
    var password by mutableStateOf("")
    var policyError by mutableStateOf(false)
}

@Composable
private fun AcceptInviteFields(
    form: AcceptInviteForm,
    isLoading: Boolean,
) {
    OutlinedTextField(
        value = form.inviteCode,
        onValueChange = { form.inviteCode = it },
        label = { Text("Invite code") },
        singleLine = true,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(Spacing.md))

    OutlinedTextField(
        value = form.password,
        onValueChange = {
            form.password = it
            form.policyError = false
        },
        label = { Text("New password") },
        supportingText = { Text("At least ${PasswordPolicy.MIN_LENGTH} characters.") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        isError = form.policyError,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
    )
}
