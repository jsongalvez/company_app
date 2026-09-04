package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.domain.Gender
import com.companyb.companyapp.dto.ClientResponse
import com.companyb.companyapp.dto.UpdateClientRequest
import com.companyb.companyapp.state.ClientState
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.ClientViewModel
import com.companyb.companyapp.viewmodel.UiState

/** Editable fields on the client detail screen (D4) — identity + contact/health, per-field edit. */
internal enum class ClientField {
    FIRST_NAME,
    MIDDLE_NAME,
    LAST_NAME,
    SUFFIX,
    GENDER,
    AGE,
    PHONE,
    ADDRESS,

    // D4 — BP is a pair, edited + committed together (both-or-neither rule).
    BP_PAIR,
    MEDICAL_CONDITIONS,
}

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
    onEditClear: () -> Unit,
    onClearPending: () -> Unit,
    onFieldError: (String) -> Unit,
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
                if (resolvesCurrentEdit) onEditClear()
                onClearPending()
                wasUpdateLoading = false
            }

            is UiState.Idle -> {
                if (wasUpdateLoading && resolvesCurrentEdit) onEditClear()
                onClearPending()
                wasUpdateLoading = false
            }

            is UiState.Loading -> {
                wasUpdateLoading = true
            }

            is UiState.Error -> {
                // 409 exits silently via Idle (the VM reloads); a plain failure (400 validation,
                // 5xx) keeps edit mode with the inline error — but only when the failing PATCH
                // belongs to the field being edited.
                if (resolvesCurrentEdit) onFieldError(state.message)
                onClearPending()
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

    var editingField by remember { mutableStateOf<ClientField?>(null) }
    var draftValue by remember { mutableStateOf("") }
    var fieldError by remember { mutableStateOf<String?>(null) }
    var showAnonymizeDialog by remember { mutableStateOf(false) }
    val bpDraft = remember { BpDraftState() }
    // The field whose PATCH is in flight. With per-field edit ownership, a PATCH's Success/Error
    // only resolves the edit of ITS field: if the user has already switched to another field
    // (startEdit commits the superseded draft first), the landing response must not force-exit
    // the new field's edit nor pollute it with the old field's error.
    var pendingEditField by remember { mutableStateOf<ClientField?>(null) }

    // The last dispatched PATCH's payload, recorded synchronously at dispatch. The supersede
    // gate compares the current draft against it: an unchanged draft whose PATCH already failed
    // abandons on switch (no phantom re-dispatch); a modified draft is a fresh attempt and
    // dispatches. Written only at dispatch, so validation errors (which never dispatch) can
    // never match. Cleared when the edit session ends (exit/Esc/landing exits) — a re-entered
    // identical value is a fresh attempt, not the same failure.
    var lastDispatched by remember { mutableStateOf<DispatchedDraft?>(null) }

    ClientDetailUpdateEffects(
        updateState = updateState,
        editingField = editingField,
        pendingEditField = pendingEditField,
        onEditClear = {
            editingField = null
            draftValue = ""
            fieldError = null
            lastDispatched = null
        },
        onClearPending = { pendingEditField = null },
        onFieldError = { fieldError = it },
    )

    // H4 — mutual exclusion with in-flight mutations: a new edit can't open while a PATCH or the
    // anonymize POST is in flight (an anonymize in flight must never race a fresh PATCH).
    // Switching fields commits the superseded field's draft first — its typed value must not be
    // silently dropped by the disposal-blur (which the editingField != field guard would drop).
    fun exitEdit() {
        editingField = null
        draftValue = ""
        fieldError = null
        lastDispatched = null
    }

    // Typing clears the live error: a draft modified after a failed PATCH is a fresh attempt
    // (the supersede gate compares the dispatch record), and a validation error vanishes once
    // the input is corrected.
    fun handleDraftChange(value: String) {
        draftValue = value
        fieldError = null
    }

    // Synchronous record of the last dispatched PATCH's payload — the supersede gate compares
    // the current draft against it (no composition-lagged reads in the gate).
    fun recordDispatchedDraft(
        field: ClientField,
        value: String,
        bpDiastolic: String = "",
    ) {
        lastDispatched = DispatchedDraft(field = field, value = value, bpDiastolic = bpDiastolic)
    }

    // Returns false when the draft is invalid — the caller (startEdit's supersede) then aborts
    // the field switch so the error stays visible on the field that owns it (the draft is not
    // silently dropped, and the error write is not instantly wiped).
    fun commitEdit(field: ClientField): Boolean {
        if (editingField != field) return true
        if (navigationLocked) return true
        if (updateState is UiState.Loading) return true
        if (anonymizeState is UiState.Loading) return true
        // 409/404 reload guard: the reload's own PATCH already failed and the detail is being
        // re-fetched (clientDetail Loading). The composed updateState has already flipped to
        // Idle by the time the disposal-blur fires on the reload's unmount, so the Loading
        // guard above passes — without this, the identical failed payload would re-dispatch,
        // clobber the reloaded (elsewhere-changed) record and clear the changed-elsewhere
        // banner. The reload owns the exit (the unmount discards the edit state). Live flow
        // value — synchronous, no composition lag.
        if (shouldBailOnReload(viewModel.clientDetail.value)) return true
        val trimmed = draftValue.trim()
        if (trimmed == currentFieldValue(client, field)) {
            exitEdit()
            return true
        }
        val patch = patchFor(field, trimmed) { fieldError = it }
        if (patch == null) return false
        recordDispatchedDraft(field, trimmed)
        pendingEditField = field
        viewModel.updateClient(client.id, patch)
        return true
    }

    // D4 — BP pair commit (owned here, not in the editor): validates the hoisted drafts, builds
    // the request; unchanged pair → silent exit. Enter/blur/supersede all route through this.
    // Returns false on invalid drafts (see [commitEdit]).
    fun commitBpDrafts(): Boolean {
        if (editingField != ClientField.BP_PAIR) return true
        if (navigationLocked) return true
        if (updateState is UiState.Loading) return true
        if (anonymizeState is UiState.Loading) return true
        if (shouldBailOnReload(viewModel.clientDetail.value)) return true
        val sys = bpDraft.systolic.trim()
        val dia = bpDraft.diastolic.trim()
        // Unchanged vs the seeded values (string compare — drafts equal the seed, e.g. the
        // untouched null-BP pair: empty drafts vs null values). The blank-validation would
        // otherwise misreport the untouched null-BP pair as "Both BP fields are required" and
        // abort a supersede, trapping the user in the editor (no Esc on mobile). Note the
        // string compare is stricter than a numeric one: a leading-zero alias of the seed
        // ("0120" vs "120") counts as an edit and dispatches a redundant-but-idempotent PATCH,
        // and clearing a seeded pair reports "Both BP fields are required" (not "valid
        // number") — both acceptable; the abandon gate canonicalizes numerically downstream.
        val seedSystolic = client.systolicBp?.toString().orEmpty()
        val seedDiastolic = client.diastolicBp?.toString().orEmpty()
        val sysVal = sys.toShortOrNull()
        val diaVal = dia.toShortOrNull()
        if (sys == seedSystolic && dia == seedDiastolic) {
            exitEdit()
        } else {
            fieldError =
                when {
                    sys.isEmpty() || dia.isEmpty() -> "Both BP fields are required"
                    sysVal == null || diaVal == null -> "Enter a valid number"
                    else -> null
                }
            if (fieldError != null) return false
            recordDispatchedDraft(ClientField.BP_PAIR, sys, dia)
            pendingEditField = ClientField.BP_PAIR
            viewModel.updateClient(client.id, UpdateClientRequest(systolicBp = sysVal!!, diastolicBp = diaVal!!))
        }
        return true
    }

    fun startEdit(field: ClientField) {
        if (navigationLocked) return
        if (updateState is UiState.Loading) return
        if (anonymizeState is UiState.Loading) return
        if (editingField != null && editingField != field) {
            // Commit the superseded field's draft first — its typed value must not be silently
            // dropped by the disposal-blur (which the editingField != field guard would drop).
            // The pair routes through its own commit: its drafts live in [bpDraft], not the
            // shared single-field draft, so commitEdit would see an "unchanged" empty draft.
            // An invalid draft aborts the switch — the error stays on the field that owns it.
            // An UNCHANGED failed draft abandons on switch: re-dispatching the identical failed
            // value would be a phantom retry whose second failure is suppressed after the switch
            // (resolvesCurrentEdit false) — the attempted value and error would vanish with zero
            // feedback, breaching D4. The gate is synchronous (the VM's live flow value + the
            // dispatch record + the current draft — no composition-lagged reads): a draft
            // modified since its failure dispatches as a fresh attempt; a validation error
            // (never dispatched) can never match the record, so it aborts instead.
            val committed =
                when {
                    shouldAbandonFailedDraft(
                        updateState = viewModel.updateClientState.value,
                        lastDispatchedField = lastDispatched?.field,
                        editingField = editingField,
                        draftMatchesLastDispatched =
                            lastDispatched?.matches(draftValue, bpDraft.systolic, bpDraft.diastolic) == true,
                    ) -> true

                    editingField == ClientField.BP_PAIR -> commitBpDrafts()

                    else -> commitEdit(editingField!!)
                }
            if (!committed) return
        }
        editingField = field
        draftValue = currentFieldValue(client, field)
        fieldError = null
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = clientDisplayName(client),
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.size(Spacing.xs))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        // Scroll container wraps the layout split — both actuals stay scrollable as a unit.
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
        ) {
            ClientDetailLayout(
                identity = {
                    Spacer(Modifier.size(Spacing.sm))
                    SectionLabel("Identity")
                    ClientFieldEditor(
                        label = "First name",
                        value = client.firstName.orEmpty(),
                        field = ClientField.FIRST_NAME,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = ::handleDraftChange,
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                        enabled = !navigationLocked,
                    )
                    ClientFieldEditor(
                        label = "Middle name",
                        value = client.middleName.orEmpty(),
                        field = ClientField.MIDDLE_NAME,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = ::handleDraftChange,
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                        enabled = !navigationLocked,
                    )
                    ClientFieldEditor(
                        label = "Last name",
                        value = client.lastName.orEmpty(),
                        field = ClientField.LAST_NAME,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = ::handleDraftChange,
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                        enabled = !navigationLocked,
                    )
                    ClientFieldEditor(
                        label = "Suffix",
                        value = client.suffix.orEmpty(),
                        field = ClientField.SUFFIX,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = ::handleDraftChange,
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                        enabled = !navigationLocked,
                    )
                    GenderFieldEditor(
                        value = client.gender.displayName(),
                        field = ClientField.GENDER,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = ::handleDraftChange,
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        enabled = !navigationLocked,
                    )
                    ClientFieldEditor(
                        label = "Age",
                        value = client.age.toString(),
                        field = ClientField.AGE,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = ::handleDraftChange,
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                        enabled = !navigationLocked,
                        keyboardType = KeyboardType.Number,
                    )
                },
                contactHealth = {
                    Spacer(Modifier.size(Spacing.sm))
                    SectionLabel("Contact + Health")
                    ClientFieldEditor(
                        label = "Phone",
                        value = client.phoneNumber.orEmpty(),
                        field = ClientField.PHONE,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = ::handleDraftChange,
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                        enabled = !navigationLocked,
                        keyboardType = KeyboardType.Phone,
                    )
                    ClientFieldEditor(
                        label = "Address",
                        value = client.address.orEmpty(),
                        field = ClientField.ADDRESS,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = ::handleDraftChange,
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                        enabled = !navigationLocked,
                    )
                    // D4 BP pair rule — both fields or neither (backend 400s otherwise): the pair
                    // is one editor, committed together, so a null-BP client can gain BP values.
                    BpPairEditor(
                        systolic = client.systolicBp,
                        diastolic = client.diastolicBp,
                        editing = editingField == ClientField.BP_PAIR,
                        fieldError = if (editingField == ClientField.BP_PAIR) fieldError else null,
                        draft = bpDraft,
                        onStartEdit = { startEdit(ClientField.BP_PAIR) },
                        onCommit = ::commitBpDrafts,
                        onCancel = ::exitEdit,
                        onDraftChanged = { fieldError = null },
                        enabled = !navigationLocked,
                    )
                    ClientFieldEditor(
                        label = "Medical conditions",
                        value = client.medicalConditions.orEmpty(),
                        field = ClientField.MEDICAL_CONDITIONS,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = ::handleDraftChange,
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                        enabled = !navigationLocked,
                    )
                },
                actions = {
                    // D5 — destructive styling (error/onError tokens per the #96 badge precedent).
                    // Disabled while a POST is in flight — re-tapping mid-anonymize would fire a
                    // second destructive request (a redundant 404 after the first 204).
                    OutlinedButton(
                        onClick = { showAnonymizeDialog = true },
                        enabled = !navigationLocked && anonymizeState !is UiState.Loading,
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        border =
                            BorderStroke(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text("Anonymize")
                    }
                    if (anonymizeState is UiState.Error) {
                        Spacer(Modifier.size(Spacing.xs))
                        Text(
                            text = anonymizeState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )
        }
    }

    if (showAnonymizeDialog) {
        AnonymizeDialog(
            clientName = clientDisplayName(client),
            // H4 — clicking Anonymize blurs an editing field, which blur-commits a PATCH in
            // flight; confirming while that PATCH is still saving would race it against the
            // anonymize POST (a slow PATCH could land after the anonymize and re-populate PII on
            // the soft-deleted row). Confirm stays disabled until the blur-committed PATCH
            // resolves.
            editInFlight = navigationLocked || updateState is UiState.Loading,
            onConfirm = {
                showAnonymizeDialog = false
                viewModel.anonymizeClient(client.id)
            },
            onDismiss = { showAnonymizeDialog = false },
        )
    }
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Esc cancels an in-progress edit — shared by the single-field and BP-pair editors. */
private fun Modifier.escapeCancels(onCancel: () -> Unit): Modifier =
    onPreviewKeyEvent {
        if (it.key == Key.Escape) {
            onCancel()
            true
        } else {
            false
        }
    }

/**
 * Reload bail guard: while the detail flow is Loading, a commit must not dispatch — the
 * 409/404 reload's unmount fires the editing field's disposal-blur, and the composed
 * updateState has already flipped to Idle by then (the 409 handler wrote Idle + triggered the
 * reload in one turn), so the commit's Loading guard alone would let the identical failed
 * payload re-dispatch and clobber the reloaded record. The reload owns the exit. Internal for
 * the unit test (commonTest friend path).
 */
internal fun shouldBailOnReload(detailState: UiState<*>): Boolean = detailState is UiState.Loading

/**
 * Supersede gate: abandon (don't re-dispatch) a draft whose PATCH already failed UNCHANGED.
 *
 * [updateState] must be the VM's LIVE flow value (not a composed snapshot) and
 * [draftMatchesLastDispatched] must compare the current draft against the last dispatched
 * PATCH's payload — both are synchronous reads at the switch. A stale Error from a superseded
 * field (lastDispatchedField ≠ editingField) or a modified draft (no match) falls through to
 * the normal commit path; a validation error never dispatches, so it can never match.
 *
 * Internal for the unit test (commonTest friend path).
 */
internal fun shouldAbandonFailedDraft(
    updateState: UiState<*>,
    lastDispatchedField: ClientField?,
    editingField: ClientField?,
    draftMatchesLastDispatched: Boolean,
): Boolean =
    updateState is UiState.Error &&
        lastDispatchedField != null &&
        lastDispatchedField == editingField &&
        draftMatchesLastDispatched

/**
 * Synchronous record of the last dispatched PATCH's payload (see [shouldAbandonFailedDraft]).
 * For BP pairs, [value] holds the systolic and [bpDiastolic] the diastolic; for single fields,
 * [value] holds the field's value and [bpDiastolic] is unused. The comparison canonicalizes
 * numeric fields by parsing (a leading-zero alias like "0121" vs "121" is the same payload, not
 * a modification) and trims before comparing (trailing spaces tolerated on both numeric and
 * string drafts), so a cosmetic draft difference can't masquerade as a modification. INVARIANT:
 * [value] and [bpDiastolic] always hold validated, parseable payloads — both dispatch sites
 * record only after validation, which is what makes the parse-based comparison null-safe.
 * Internal for the unit test (commonTest friend path).
 */
internal data class DispatchedDraft(
    val field: ClientField,
    val value: String,
    val bpDiastolic: String = "",
) {
    fun matches(
        draftValue: String,
        bpSystolic: String,
        bpDiastolic: String,
    ): Boolean =
        when (field) {
            ClientField.BP_PAIR -> {
                bpSystolic.trim().toShortOrNull() == value.toShortOrNull() &&
                    bpDiastolic.trim().toShortOrNull() == this.bpDiastolic.toShortOrNull()
            }

            ClientField.AGE -> {
                draftValue.trim().toIntOrNull() == value.toIntOrNull()
            }

            else -> {
                draftValue.trim() == value
            }
        }
}

/**
 * D4 — inline per-field editor: pencil affordance → edit in place → commit on Enter/blur.
 * Pessimistic: the display value only changes via PATCH success (the VM state); while editing,
 * the draft is the source of truth. Esc cancels the edit.
 */
@Composable
private fun ClientFieldEditor(
    label: String,
    value: String,
    field: ClientField,
    editingField: ClientField?,
    draftValue: String,
    fieldError: String?,
    onDraftChange: (String) -> Unit,
    onStartEdit: (ClientField) -> Unit,
    onCommit: (ClientField) -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val editing = editingField == field
    val error = if (editing) fieldError else null

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (editing) {
                OutlinedTextField(
                    value = draftValue,
                    onValueChange = onDraftChange,
                    singleLine = true,
                    isError = error != null,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommit(field) }),
                    modifier =
                        Modifier
                            .weight(1f)
                            .onFocusChanged { if (!it.isFocused) onCommit(field) }
                            .escapeCancels(onCancel),
                )
            } else {
                Text(
                    text = if (value.isBlank()) "—" else value,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (value.isBlank()) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    modifier = Modifier.weight(1f),
                )
                // Pencil affordance — Canvas-drawn (no material-icons dependency, #107 precedent).
                PencilIcon(
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .clickable(enabled = enabled) { onStartEdit(field) }
                            .padding(start = Spacing.xs),
                )
            }
        }
        ClientFieldErrorText(error)
    }
}

@Composable
private fun ClientFieldErrorText(error: String?) {
    if (error != null) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * D4 — blood-pressure pair draft state, hoisted into [ClientDetailContent]: a field-switch
 * supersede must be able to commit the pair's typed drafts (the editor's local state would be
 * unreachable from `startEdit`). The pair is one logical field: both inputs or neither
 * (backend 400s otherwise); commits both values in one partial PATCH; enterable from null.
 *
 * Blur-commit fires only when BOTH sides were typed in this edit session: (a) with no typing at
 * all, tapping systolic→diastolic blurs field 1 with both drafts holding the seeded values —
 * commitPair would see "unchanged" and cancel the edit before the user typed anything; (b) with
 * only one side typed, tapping the other side would blur-commit the pair with the seeded value
 * for the side the user is on their way to edit — same premature-commit class. With both sides
 * typed, blur commits (or surfaces the inline pair-required error, matching the single-field
 * "Value required" on blank). Enter (Done) always commits explicitly, unchanged pair included
 * (silent exit).
 */
private class BpDraftState {
    var systolic by mutableStateOf("")
    var diastolic by mutableStateOf("")
    var dirtySystolic by mutableStateOf(false)
    var dirtyDiastolic by mutableStateOf(false)

    fun seed(
        systolic: Short?,
        diastolic: Short?,
    ) {
        this.systolic = systolic?.toString().orEmpty()
        this.diastolic = diastolic?.toString().orEmpty()
        dirtySystolic = false
        dirtyDiastolic = false
    }
}

@Composable
private fun BpPairEditor(
    systolic: Short?,
    diastolic: Short?,
    editing: Boolean,
    fieldError: String?,
    draft: BpDraftState,
    onStartEdit: () -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onDraftChanged: () -> Unit,
    enabled: Boolean,
) {
    // Re-seed drafts each time edit mode is entered. No value-change key needed: any reload
    // (409/404 reload, entry re-fetch) writes UiState.Loading into the detail flow, which
    // unmounts this whole editor (ClientDetailScreen renders ClientDetailContent only on
    // Success) — the drafts die with it, and re-entry seeds fresh. The only client-value change
    // that keeps the editor mounted is the user's own PATCH success (transform commits the
    // detail before updateState flips), and there the drafts already equal the committed values.
    LaunchedEffect(editing) {
        if (editing) {
            draft.seed(systolic, diastolic)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = "Blood pressure",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (editing) {
            BpPairEditRow(
                draft = draft,
                fieldError = fieldError,
                enabled = enabled,
                onCommit = onCommit,
                onCancel = onCancel,
                onDraftChanged = onDraftChanged,
            )
        } else {
            BpPairDisplayRow(
                systolic = systolic,
                diastolic = diastolic,
                enabled = enabled,
                onStartEdit = onStartEdit,
            )
        }
        if (fieldError != null) {
            Text(
                text = fieldError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BpPairEditRow(
    draft: BpDraftState,
    fieldError: String?,
    enabled: Boolean,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onDraftChanged: () -> Unit,
) {
    // Blur-commit lives on the ROW, not the fields: isFocused on the row is false only
    // when the whole pair lost focus (moving systolic↔diastolic keeps a descendant
    // focused — a per-field handler would blur-commit mid-correction the moment the user
    // taps back into the other side after typing both). Fires once per pair-focus-loss,
    // disposal included. Esc cancels the edit.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .onFocusChanged {
                    if (!it.isFocused && draft.dirtySystolic && draft.dirtyDiastolic) {
                        onCommit()
                    }
                }.escapeCancels(onCancel),
    ) {
        OutlinedTextField(
            value = draft.systolic,
            onValueChange = {
                draft.systolic = it
                draft.dirtySystolic = true
                onDraftChanged()
            },
            singleLine = true,
            isError = fieldError != null,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "/",
            modifier = Modifier.padding(horizontal = Spacing.xs),
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = draft.diastolic,
            onValueChange = {
                draft.diastolic = it
                draft.dirtyDiastolic = true
                onDraftChanged()
            },
            singleLine = true,
            isError = fieldError != null,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BpPairDisplayRow(
    systolic: Short?,
    diastolic: Short?,
    enabled: Boolean,
    onStartEdit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        val display =
            when {
                systolic != null && diastolic != null -> "$systolic / $diastolic"
                systolic != null -> "$systolic / —"
                diastolic != null -> "— / $diastolic"
                else -> "—"
            }
        Text(
            text = display,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (systolic == null && diastolic == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            modifier = Modifier.weight(1f),
        )
        PencilIcon(
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier =
                Modifier
                    .clickable(enabled = enabled, onClick = onStartEdit)
                    .padding(start = Spacing.xs),
        )
    }
}

/** D4 — gender edits via dropdown; commit fires on select ("commit on select"). */
@Composable
private fun GenderFieldEditor(
    value: String,
    field: ClientField,
    editingField: ClientField?,
    draftValue: String,
    fieldError: String?,
    onDraftChange: (String) -> Unit,
    onStartEdit: (ClientField) -> Unit,
    onCommit: (ClientField) -> Unit,
    enabled: Boolean,
) {
    val editing = editingField == field
    val error = if (editing) fieldError else null

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = "Gender",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (editing) {
            GenderEditDropdown(
                draftValue = draftValue,
                field = field,
                enabled = enabled,
                error = error,
                onDraftChange = onDraftChange,
                onCommit = onCommit,
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                PencilIcon(
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .clickable(enabled = enabled) { onStartEdit(field) }
                            .padding(start = Spacing.xs),
                )
            }
        }
        // D4 inline error — the dropdown has no blur/Enter surface, so a failed PATCH must
        // surface here or it is invisible (re-selecting the same value exits the edit).
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GenderEditDropdown(
    draftValue: String,
    field: ClientField,
    enabled: Boolean,
    error: String?,
    onDraftChange: (String) -> Unit,
    onCommit: (ClientField) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = if (draftValue == Gender.M.name) Gender.M.displayName() else Gender.F.displayName(),
            onValueChange = {},
            singleLine = true,
            readOnly = true,
            isError = error != null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { menuOpen = true },
            enabled = enabled,
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text(Gender.M.displayName()) },
                enabled = enabled,
                onClick = {
                    menuOpen = false
                    onDraftChange(Gender.M.name)
                    onCommit(field)
                },
            )
            DropdownMenuItem(
                text = { Text(Gender.F.displayName()) },
                enabled = enabled,
                onClick = {
                    menuOpen = false
                    onDraftChange(Gender.F.name)
                    onCommit(field)
                },
            )
        }
    }
}

// Pencil glyph drawn via Canvas — the project has no material-icons dependency (only the #107
// hamburger precedent exists); a compact 45°-rotated body + tip triangle reads as a pencil.
@Suppress("MagicNumber") // geometry literals for the pencil proportions — one-time drawing constants
@Composable
private fun PencilIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier =
            modifier
                .size(14.dp)
                .rotate(-45f),
    ) {
        val w = size.width
        val h = size.height
        val bodyH = h * 0.30f
        val bodyTop = (h - bodyH) / 2f
        val tipLen = w * 0.25f
        val eraserLen = w * 0.15f

        // eraser
        drawRect(
            color = tint,
            topLeft = Offset(0f, bodyTop),
            size = Size(eraserLen, bodyH),
        )
        // body
        drawRect(
            color = tint,
            topLeft = Offset(eraserLen, bodyTop),
            size = Size(w - eraserLen - tipLen, bodyH),
        )
        // tip (triangle pointing right)
        drawLine(
            color = tint,
            start = Offset(w - eraserLen - tipLen, bodyTop),
            end = Offset(w - eraserLen, h / 2f),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = tint,
            start = Offset(w - eraserLen - tipLen, bodyTop + bodyH),
            end = Offset(w - eraserLen, h / 2f),
            strokeWidth = 1.dp.toPx(),
        )
    }
}

/** D4 — the field's current display value; also the draft's starting value + unchanged check. */
private fun currentFieldValue(
    client: ClientResponse,
    field: ClientField,
): String =
    when (field) {
        ClientField.FIRST_NAME -> client.firstName.orEmpty()
        ClientField.MIDDLE_NAME -> client.middleName.orEmpty()
        ClientField.LAST_NAME -> client.lastName.orEmpty()
        ClientField.SUFFIX -> client.suffix.orEmpty()
        ClientField.GENDER -> client.gender.name
        ClientField.AGE -> client.age.toString()
        ClientField.PHONE -> client.phoneNumber.orEmpty()
        ClientField.ADDRESS -> client.address.orEmpty()
        ClientField.BP_PAIR -> ""
        ClientField.MEDICAL_CONDITIONS -> client.medicalConditions.orEmpty()
    }

/**
 * D4 — build the partial PATCH for one committed field (only changed fields are sent; the request
 * is all-nullable). Validation errors are surfaced inline via [onError] and return null (no PATCH).
 *
 * Blank commits are rejected for every field: the backend's UpdateClientRequest treats null as
 * "don't update" (kotlinx serialization explicitNulls), so clearing a value is inexpressible —
 * "Value required" is the honest message rather than a silent no-op. Blank *names* additionally
 * 400 on the backend. BP commits as a pair via [BpPairEditor] (both-or-neither), so it has no
 * branch here.
 */
private fun patchFor(
    field: ClientField,
    trimmed: String,
    onError: (String) -> Unit,
): UpdateClientRequest? =
    when (field) {
        ClientField.FIRST_NAME -> {
            requireNonBlank(trimmed, "Name can't be blank", onError) { UpdateClientRequest(firstName = it) }
        }

        ClientField.LAST_NAME -> {
            requireNonBlank(trimmed, "Name can't be blank", onError) { UpdateClientRequest(lastName = it) }
        }

        ClientField.MIDDLE_NAME -> {
            requireNonBlank(trimmed, onError) { UpdateClientRequest(middleName = it) }
        }

        ClientField.SUFFIX -> {
            requireNonBlank(trimmed, onError) { UpdateClientRequest(suffix = it) }
        }

        ClientField.PHONE -> {
            requireNonBlank(trimmed, onError) { UpdateClientRequest(phoneNumber = it) }
        }

        ClientField.ADDRESS -> {
            requireNonBlank(trimmed, onError) { UpdateClientRequest(address = it) }
        }

        ClientField.MEDICAL_CONDITIONS -> {
            requireNonBlank(trimmed, onError) { UpdateClientRequest(medicalConditions = it) }
        }

        ClientField.GENDER -> {
            UpdateClientRequest(gender = if (trimmed == Gender.M.name) Gender.M else Gender.F)
        }

        ClientField.AGE -> {
            val age = trimmed.toIntOrNull()
            if (age == null) {
                onError("Enter a valid age")
                null
            } else {
                UpdateClientRequest(age = age)
            }
        }

        ClientField.BP_PAIR -> {
            null
        }
    }

private inline fun requireNonBlank(
    value: String,
    onError: (String) -> Unit,
    build: (String) -> UpdateClientRequest,
): UpdateClientRequest? =
    if (value.isEmpty()) {
        onError("Value required")
        null
    } else {
        build(value)
    }

private inline fun requireNonBlank(
    value: String,
    errorMessage: String,
    onError: (String) -> Unit,
    build: (String) -> UpdateClientRequest,
): UpdateClientRequest? =
    if (value.isEmpty()) {
        onError(errorMessage)
        null
    } else {
        build(value)
    }
