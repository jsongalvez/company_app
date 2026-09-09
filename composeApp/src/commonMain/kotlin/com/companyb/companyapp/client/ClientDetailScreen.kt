package com.companyb.companyapp.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.ui.contract.DestructiveConfirmDialog
import com.companyb.companyapp.ui.contract.OperationalUiContract
import com.companyb.companyapp.ui.contract.PrimaryActionButton
import com.companyb.companyapp.ui.contract.SecondaryActionButton
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn

/**
 * #113 — Client detail screen per locked #99 D4/D5/D10, reshaped by #673.
 *
 * - #673: Identity / Contact / Health sections with one labeled Edit each, deliberate
 *   Save changes / Cancel, one section at a time, focus on first field, Tab/blur never
 *   writes, Save emits one request ([ClientSectionSession.saveSection]), BP paired,
 *   pending saves preserve drafts, failures stay editable with Retry, conflicts preserve
 *   values with Reload latest, Anonymize lives in More with Save/Discard gating and no
 *   incidental PATCH, revoked GLOBAL access reads as Unavailable + Back with no cached
 *   profile (aligns with #653, never widens permissions).
 * - D4 pessimistic axes preserved (ADR-0022): edit exits only on PATCH success; 403
 *   silent-exits; 409 reloads + preserves drafts with a Reload-latest offer.
 * - D5: destructive anonymize confirm names the client, irreversible effect, no typing.
 * - D10: null-name detail = anonymized husk (gender + age only, no edit/anonymize).
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

    // #673 — hoisted above the detail Loading branch so a 409/404 reload never
    // discards entered values (canceling profile edits never touches intake either —
    // section drafts are local to this entry).
    val session = remember { ClientSectionSession() }
    var showAnonymizeDialog by remember { mutableStateOf(false) }
    var switchGate by remember { mutableStateOf<ClientSection?>(null) }
    var autoSwitch by remember { mutableStateOf<ClientSection?>(null) }
    var anonGate by remember { mutableStateOf(false) }
    var autoAnon by remember { mutableStateOf(false) }

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

    val detailClient = (detailState as? UiState.Success)?.data
    if (detailClient != null) {
        ClientSectionUpdateEffects(
            updateState = updateState,
            session = session,
            changedNotice = changedNotice,
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        ClientDetailBackRow(onBack = onBack, navigationLocked = navigationLocked)

        when (val state = detailState) {
            is UiState.Idle, is UiState.Loading -> {
                // Stale detail stays unmounted during reload; section drafts survive
                // above in [session] and re-seed only on explicit Reload latest.
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                ClientDetailLoadError(
                    message = state.message,
                    isUnavailable = isUnavailableError(state.message),
                    onRetry = { viewModel.loadClient(clientId, publishMutation = true) },
                )
            }

            is UiState.Success -> {
                ClientDetailContent(
                    client = state.data,
                    session = session,
                    updateState = updateState,
                    anonymizeState = anonymizeState,
                    changedNotice = changedNotice,
                    navigationLocked = navigationLocked,
                    viewModel = viewModel,
                    onStartSection = { section ->
                        val current = session.editingSection
                        if (current == null || current == section) {
                            session.startSection(section, state.data)
                        } else if (navigationLocked || updateState is UiState.Loading) {
                            // Mid-flight: sibling Edits are already disabled; ignore the
                            // switch so a PATCH in flight can never be discarded by a gate.
                        } else if (session.isDirty(state.data)) {
                            switchGate = section
                        } else {
                            session.exitEdit()
                            session.startSection(section, state.data)
                        }
                    },
                    onAnonymizeClick = {
                        // P3 HARD-4 — gate only on dirty edits; a clean opened section
                        // exits silently. Opening Anonymize never PATCHes by itself.
                        val editing = session.editingSection
                        if (editing == null) {
                            showAnonymizeDialog = true
                        } else if (navigationLocked || updateState is UiState.Loading) {
                            // Mid-flight: More is already disabled; ignore.
                        } else if (session.isDirty(state.data)) {
                            anonGate = true
                        } else {
                            session.exitEdit()
                            showAnonymizeDialog = true
                        }
                    },
                )
            }
        }
    }

    // Auto-land after a gate Save dispatched: the gate closes immediately, the target
    // waits here, and lands when the save's Success clears the edit (or immediately for
    // an unchanged silent exit). Validation failure clears the wait (errors stay visible).
    LaunchedEffect(session.editingSection, updateState) {
        val switchTarget = autoSwitch
        val detail = detailClient
        // #695 — named auto-land gate: same save-dispatch landing rule, one readable branch.
        val canAutoLand =
            switchTarget != null && detail != null &&
                session.editingSection == null &&
                updateState !is UiState.Loading && !navigationLocked
        if (canAutoLand) {
            autoSwitch = null
            session.startSection(switchTarget, detail)
        }
        // #695 — read after the landing above: a just-started edit blocks the anonymize auto-open.
        val canAutoAnon =
            autoAnon && session.editingSection == null &&
                updateState !is UiState.Loading && !navigationLocked
        if (canAutoAnon) {
            autoAnon = false
            showAnonymizeDialog = true
        }
    }

    // Save/Discard gate for section switches (#673: only one section edits at a time).
    // Discard/save stay disabled while a save is in flight (mirrors the section Cancel).
    val switchTarget = switchGate
    val switchClient = detailClient
    val switchSaving = navigationLocked || updateState is UiState.Loading
    if (switchTarget != null && switchClient != null) {
        SaveDiscardDialog(
            busy = switchSaving,
            onSave = {
                val deps = sectionDeps(viewModel, switchClient.id, navigationLocked, updateState, anonymizeState)
                val ok = session.saveSection(switchClient, deps)
                switchGate = null
                if (!ok) {
                    autoSwitch = null
                } else if (session.editingSection == null) {
                    autoSwitch = null
                    session.startSection(switchTarget, switchClient)
                } else {
                    autoSwitch = switchTarget
                }
            },
            onDiscard = {
                if (!switchSaving) {
                    switchGate = null
                    autoSwitch = null
                    session.exitEdit()
                    session.startSection(switchTarget, switchClient)
                }
            },
            onCancel = {
                switchGate = null
            },
        )
    }

    // Save/Discard gate before anonymize (#673: resolve edits first, never incidental PATCH).
    val anonClient = detailClient
    val anonSaving = navigationLocked || updateState is UiState.Loading
    if (anonGate && anonClient != null) {
        SaveDiscardDialog(
            busy = anonSaving,
            onSave = {
                val deps = sectionDeps(viewModel, anonClient.id, navigationLocked, updateState, anonymizeState)
                val ok = session.saveSection(anonClient, deps)
                anonGate = false
                if (!ok) {
                    autoAnon = false
                } else if (session.editingSection == null) {
                    autoAnon = false
                    showAnonymizeDialog = true
                } else {
                    autoAnon = true
                }
            },
            onDiscard = {
                if (!anonSaving) {
                    anonGate = false
                    autoAnon = false
                    session.exitEdit()
                    showAnonymizeDialog = true
                }
            },
            onCancel = { anonGate = false },
        )
    }

    if (showAnonymizeDialog && anonClient != null) {
        ClientDetailAnonymizeHost(
            show = true,
            client = anonClient,
            editInFlight = navigationLocked || updateState is UiState.Loading,
            onConfirm = {
                showAnonymizeDialog = false
                viewModel.anonymizeClient(anonClient.id)
            },
            onDismiss = { showAnonymizeDialog = false },
        )
    }
}

private fun sectionDeps(
    viewModel: ClientViewModel,
    clientId: String,
    navigationLocked: Boolean,
    updateState: UiState<ClientResponse>,
    anonymizeState: UiState<Unit>,
): ClientEditDeps =
    ClientEditDeps(
        navigationLocked = navigationLocked,
        updateState = updateState,
        anonymizeState = anonymizeState,
        liveDetailState = viewModel.clientDetail::value,
        liveUpdateState = viewModel.updateClientState::value,
        onPatch = { viewModel.updateClient(clientId, it) },
    )

internal fun isUnavailableError(message: String): Boolean = message == CLIENT_DETAIL_UNAVAILABLE

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
    isUnavailable: Boolean,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    // #673 + #653 — revoked GLOBAL access reads as Unavailable + Back
                    // (Back stays mounted above); no cached protected profile is shown
                    // and no Retry is offered for revoked access.
                    text = if (isUnavailable) "Unavailable" else message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (!isUnavailable) {
                    TextButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientSectionUpdateEffects(
    updateState: UiState<ClientResponse>,
    session: ClientSectionSession,
    changedNotice: Boolean,
) {
    var wasUpdateLoading by remember { mutableStateOf(false) }
    // D4 pessimistic axes for sections — success exits, failure stays editable with
    // Retry (pending kept so the banner + Retry stay mounted), Loading→Idle without
    // Success is the 403 silent exit (changedNotice false) or the 409 conflict reload
    // (changedNotice true — drafts preserved, Reload latest offered, never blind
    // overwrites). Per-section ownership: only the pending section's outcome touches
    // edit state.
    LaunchedEffect(updateState) {
        val resolvesCurrent = session.pendingSection != null && session.pendingSection == session.editingSection
        when (updateState) {
            is UiState.Success -> {
                if (resolvesCurrent) session.exitEdit()
                session.clearPending()
                wasUpdateLoading = false
            }

            is UiState.Idle -> {
                if (wasUpdateLoading && resolvesCurrent) {
                    if (changedNotice) {
                        // 409 conflict reload: keep entered values for review.
                        session.clearPending()
                    } else {
                        session.exitEdit()
                        session.clearPending()
                    }
                }
                wasUpdateLoading = false
            }

            is UiState.Loading -> {
                wasUpdateLoading = true
            }

            is UiState.Error -> {
                // Plain failure (400 validation, 5xx) keeps edit mode with drafts +
                // Retry — pending is kept so the section banner stays mounted.
                wasUpdateLoading = false
            }
        }
    }
}

@Composable
private fun ClientDetailContent(
    client: ClientResponse,
    session: ClientSectionSession,
    updateState: UiState<ClientResponse>,
    anonymizeState: UiState<Unit>,
    changedNotice: Boolean,
    navigationLocked: Boolean,
    viewModel: ClientViewModel,
    onStartSection: (ClientSection) -> Unit,
    onAnonymizeClick: () -> Unit,
) {
    if (client.firstName == null || client.lastName == null) {
        ClientDetailAnonymizedHusk(client)
        return
    }

    val deps =
        ClientEditDeps(
            navigationLocked = navigationLocked,
            updateState = updateState,
            anonymizeState = anonymizeState,
            liveDetailState = viewModel.clientDetail::value,
            liveUpdateState = viewModel.updateClientState::value,
            onPatch = { viewModel.updateClient(client.id, it) },
        )

    Column(modifier = Modifier.fillMaxSize()) {
        ClientDetailContentBody(
            client = client,
            session = session,
            updateState = updateState,
            anonymizeState = anonymizeState,
            changedNotice = changedNotice,
            navigationLocked = navigationLocked,
            callbacks =
                SectionScreenCallbacks(
                    onStartSection = onStartSection,
                    onCancelSection = session::exitEdit,
                    onSaveSection = { session.saveSection(client, deps) },
                    onRetrySection = { session.retryPending(deps) },
                    onReloadLatest = {
                        session.reseed(client)
                        session.clearPending()
                    },
                    onDraftChange = { field, value ->
                        session.handleDraftChange(field, value)
                    },
                    onBpChange = { sys, value ->
                        if (sys) {
                            session.bpDraft.systolic = value
                        } else {
                            session.bpDraft.diastolic = value
                        }
                        session.fieldErrors.remove(ClientField.BP_PAIR)
                    },
                    onAnonymizeClick = onAnonymizeClick,
                ),
        )
    }
}

@Composable
private fun SaveDiscardDialog(
    busy: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    // #673 — three-way gate (Save / Discard / Cancel) honoring the shared dialog rules
    // (#670: max 560dp via widthIn, scrolling body, fixed actions, contract buttons with
    // stable busy geometry, no entrance animation). Esc/Back cancels (stays editing);
    // Discard drops section drafts; Save dispatches one request and stays in the section
    // on validation failure. Save/Discard lock while a save is in flight.
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancelFocus.requestFocus() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("Save or discard changes?", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "You have unsent changes. Save them, discard them, or stay editing.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            PrimaryActionButton(label = "Save changes", onClick = onSave, enabled = !busy, isBusy = false)
        },
        dismissButton = {
            Row {
                SecondaryActionButton(label = "Discard", onClick = onDiscard, enabled = !busy)
                TertiaryActionButton(
                    label = "Cancel",
                    onClick = onCancel,
                    enabled = !busy,
                    modifier = Modifier.focusRequester(cancelFocus),
                )
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .widthIn(max = OperationalUiContract.dialogMaxWidth)
                .padding(horizontal = Spacing.md),
        shape = MaterialTheme.shapes.medium,
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
    // H4 — confirming while a PATCH is still saving would race it against the
    // anonymize POST (a slow PATCH could land after the anonymize and re-populate
    // PII on the soft-deleted row). Confirm stays disabled until the save resolves.
    // With section editing there is no blur-commit, so opening Anonymize never
    // incidentally PATCHes — the Save/Discard gate above owns the ordering.
    DestructiveConfirmDialog(
        title = "Anonymize client?",
        body =
            clientPrimaryName(client) +
                "\n\nThis permanently removes the client's personal data " +
                "(name, contact, health info). This cannot be undone. " +
                "Gender and age are kept for reporting." +
                if (editInFlight) "\n\nSaving your edit…" else "",
        confirmLabel = "Confirm",
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isBusy = false,
        confirmEnabled = !editInFlight,
    )
}

// D10 — null name pair is the only in-band anonymized signal (F3): render the husk, no edit
// affordances, no anonymize button, no PATCH surface.
@Composable
private fun ClientDetailAnonymizedHusk(client: ClientResponse) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = CLIENT_ANONYMIZED_LABEL,
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
