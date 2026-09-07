package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.state.ClientState
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.ClientViewModel

/**
 * #113 — Client detail screen per locked #99 D4/D5/D10.
 *
 * - D4: two-column desktop / single-column mobile layout via the [ClientDetailLayout] expect
 *   (ClientScreenParts.kt); inline per-field edit, **pessimistic** (ADR-0022) — edit mode exits
 *   only on PATCH success; failure keeps the attempted value + inline error and stays in edit;
 *   403 silent-exits (Loading → Idle transition without Success); 409 reloads + shows the
 *   changed-elsewhere banner. Partial PATCH — one control, one field (BP pair commits both).
 * - D5: destructive anonymize confirm dialog (name shown, "cannot be undone" copy, no typing).
 * - D10: null-name detail = anonymized husk (gender + age only, no edit/anonymize affordances).
 */
@Composable
fun ClientDetailScreen(
    clientId: String,
    viewModel: ClientViewModel,
    onBack: () -> Unit,
    onAnonymized: () -> Unit,
) {
    val detailState by viewModel.clientDetail.collectAsState()
    val updateState by viewModel.updateClientState.collectAsState()
    val anonymizeState by viewModel.anonymizeState.collectAsState()
    val changedNotice by viewModel.detailChangedNotice.collectAsState()
    val navigationLocked by ClientState.clientMutationInFlight.collectAsState()
    ClientDetailBackHandler(navigationLocked)

    LaunchedEffect(Unit) {
        logInfo("ClientDetailScreen", "composable entered: clientId=$clientId")
        viewModel.loadClient(clientId, publishMutation = true)
    }

    ClientDetailScreenStatusEffects(
        anonymizeState = anonymizeState,
        detailState = detailState,
        updateState = updateState,
        onAnonymized = onAnonymized,
    )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        ClientDetailBackRow(onBack = onBack, navigationLocked = navigationLocked)

        when (val state = detailState) {
            is UiState.Idle, is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                ClientDetailLoadError(
                    message = state.message,
                    onRetry = { viewModel.loadClient(clientId, publishMutation = true) },
                )
            }

            is UiState.Success -> {
                if (changedNotice) {
                    Text(
                        text = "Client was updated elsewhere — changes reloaded",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = Spacing.sm),
                    )
                }
                ClientDetailContent(
                    client = state.data,
                    updateState = updateState,
                    anonymizeState = anonymizeState,
                    navigationLocked = navigationLocked,
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun ClientDetailBackRow(
    onBack: () -> Unit,
    navigationLocked: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, enabled = !navigationLocked) {
            Text("Back")
        }
    }
}

@Composable
private fun ClientDetailScreenStatusEffects(
    anonymizeState: UiState<Unit>,
    detailState: UiState<ClientResponse>,
    updateState: UiState<ClientResponse>,
    onAnonymized: () -> Unit,
) {
    // D1 — 204 → pop back to search; the confirmation snackbar is shown by ClientsScreen
    // (ClientState.anonymizeNotice).
    LaunchedEffect(anonymizeState) {
        when (val state = anonymizeState) {
            is UiState.Success -> {
                onAnonymized()
            }

            is UiState.Error -> {
                logWarn("ClientDetailScreen", "anonymizeState=Error: ${state.message}")
            }

            else -> {}
        }
    }

    LaunchedEffect(detailState) {
        (detailState as? UiState.Error)?.let {
            logWarn("ClientDetailScreen", "detailState=Error: ${it.message}")
        }
    }

    LaunchedEffect(updateState) {
        (updateState as? UiState.Error)?.let {
            logWarn("ClientDetailScreen", "updateState=Error: ${it.message}")
        }
    }
}

@Composable
private fun ClientDetailLoadError(
    message: String,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = onRetry) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
private fun ClientDetailUpdateEffects(
    updateState: UiState<ClientResponse>,
    editingField: ClientField?,
    pendingEditField: ClientField?,
    callbacks: ClientUpdateCallbacks,
) {
    var wasUpdateLoading by remember { mutableStateOf(false) }
    // Whether a landing PATCH outcome concerns the field currently being edited. Both commit
    // paths set the pending field synchronously before dispatching, so a non-null pending field
    // always identifies the in-flight PATCH.
    // D4 pessimistic axes — edit mode exits only on success; failure keeps the attempted value +
    // inline error and stays in edit; Loading → Idle without Success in between = 403 silent exit.
    // Per-field ownership: a resolved PATCH touches edit state only when it belongs to the
    // currently-editing field.
    LaunchedEffect(updateState) {
        val resolvesCurrentEdit = editingField == pendingEditField
        when (val state = updateState) {
            is UiState.Success -> {
                if (resolvesCurrentEdit) callbacks.onEditClear()
                callbacks.onClearPending()
                wasUpdateLoading = false
            }

            is UiState.Idle -> {
                if (wasUpdateLoading && resolvesCurrentEdit) callbacks.onEditClear()
                callbacks.onClearPending()
                wasUpdateLoading = false
            }

            is UiState.Loading -> {
                wasUpdateLoading = true
            }

            is UiState.Error -> {
                // 409 exits silently via Idle (the VM reloads); a plain failure (400 validation,
                // 5xx) keeps edit mode with the inline error — but only when the failing PATCH
                // belongs to the field being edited.
                if (resolvesCurrentEdit) callbacks.onFieldError(state.message)
                callbacks.onClearPending()
                wasUpdateLoading = false
            }
        }
    }
}

@Composable
private fun ClientDetailContent(
    client: ClientResponse,
    updateState: UiState<ClientResponse>,
    anonymizeState: UiState<Unit>,
    navigationLocked: Boolean,
    viewModel: ClientViewModel,
) {
    if (client.firstName == null || client.lastName == null) {
        ClientDetailAnonymizedHusk(client)
        return
    }

    // Edit ownership lives in the session (#476 Cyclomatic burn — the local commit helpers
    // carried this composable to 35/15; members own their own budgets now).
    val session = remember { ClientEditSession() }
    var showAnonymizeDialog by remember { mutableStateOf(false) }
    val deps =
        ClientEditDeps(
            navigationLocked = navigationLocked,
            updateState = updateState,
            anonymizeState = anonymizeState,
            liveDetailState = viewModel.clientDetail::value,
            liveUpdateState = viewModel.updateClientState::value,
            onPatch = { viewModel.updateClient(client.id, it) },
        )

    ClientDetailUpdateEffects(
        updateState = updateState,
        editingField = session.editingField,
        pendingEditField = session.pendingEditField,
        callbacks =
            ClientUpdateCallbacks(
                onEditClear = session::exitEdit,
                onClearPending = { session.pendingEditField = null },
                onFieldError = { session.fieldError = it },
            ),
    )

    Column(modifier = Modifier.fillMaxSize()) {
        ClientDetailContentBody(
            client = client,
            edit = session.snapshot(navigationLocked),
            draft = session.bpDraft,
            anonymizeState = anonymizeState,
            callbacks = session.callbacks(client, deps) { showAnonymizeDialog = true },
        )
    }

    ClientDetailAnonymizeHost(
        show = showAnonymizeDialog,
        client = client,
        editInFlight = navigationLocked || updateState is UiState.Loading,
        onConfirm = {
            showAnonymizeDialog = false
            viewModel.anonymizeClient(client.id)
        },
        onDismiss = { showAnonymizeDialog = false },
    )
}

@Composable
private fun ClientDetailAnonymizeHost(
    show: Boolean,
    client: ClientResponse,
    editInFlight: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!show) return
    AnonymizeDialog(
        clientName = clientDisplayName(client),
        // H4 — clicking Anonymize blurs an editing field, which blur-commits a PATCH in
        // flight; confirming while that PATCH is still saving would race it against the
        // anonymize POST (a slow PATCH could land after the anonymize and re-populate PII on
        // the soft-deleted row). Confirm stays disabled until the blur-committed PATCH
        // resolves.
        editInFlight = editInFlight,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

// D10 — null name pair is the only in-band anonymized signal (F3): render the husk, no edit
// affordances, no anonymize button, no PATCH surface.
@Composable
private fun ClientDetailAnonymizedHusk(client: ClientResponse) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Anonymized",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.size(Spacing.sm))
        Text(
            text = "Gender: ${client.gender.displayName()}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Age: ${client.age}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** D5 — destructive confirm: client name shown for verification, no typing-to-confirm. */
@Composable
private fun AnonymizeDialog(
    clientName: String,
    editInFlight: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Anonymize client?") },
        text = {
            Column {
                Text(
                    text = clientName,
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.size(Spacing.xs))
                Text(
                    text =
                        "This permanently removes the client's personal data " +
                            "(name, contact, health info). This cannot be undone. " +
                            "Gender and age are kept for reporting.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (editInFlight) {
                    Spacer(Modifier.size(Spacing.xs))
                    Text(
                        text = "Saving your edit…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !editInFlight,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
