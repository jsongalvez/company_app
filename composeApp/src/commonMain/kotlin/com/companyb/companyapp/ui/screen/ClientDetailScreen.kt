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
import androidx.compose.foundation.layout.width
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
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.ClientViewModel
import com.companyb.companyapp.viewmodel.UiState

/** Editable fields on the client detail screen (D4) — identity + contact/health, per-field edit. */
private enum class ClientField {
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

    LaunchedEffect(Unit) {
        logInfo("ClientDetailScreen", "composable entered: clientId=$clientId")
        viewModel.loadClient(clientId)
    }

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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }

        when (val state = detailState) {
            is UiState.Idle, is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { viewModel.loadClient(clientId) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
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
                    viewModel = viewModel,
                )
            }
        }
    }
}

@Composable
private fun ClientDetailContent(
    client: ClientResponse,
    updateState: UiState<ClientResponse>,
    anonymizeState: UiState<Unit>,
    viewModel: ClientViewModel,
) {
    // D10 — null name pair is the only in-band anonymized signal (F3): render the husk, no edit
    // affordances, no anonymize button, no PATCH surface.
    if (client.firstName == null || client.lastName == null) {
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
        return
    }

    var editingField by remember { mutableStateOf<ClientField?>(null) }
    var draftValue by remember { mutableStateOf("") }
    var fieldError by remember { mutableStateOf<String?>(null) }
    var wasUpdateLoading by remember { mutableStateOf(false) }
    var showAnonymizeDialog by remember { mutableStateOf(false) }

    // D4 pessimistic axes — edit mode exits only on success; failure keeps the attempted value +
    // inline error and stays in edit; Loading → Idle without Success in between = 403 silent exit.
    LaunchedEffect(updateState) {
        when (val state = updateState) {
            is UiState.Success -> {
                editingField = null
                fieldError = null
                wasUpdateLoading = false
            }

            is UiState.Idle -> {
                if (wasUpdateLoading) {
                    editingField = null
                    draftValue = ""
                    fieldError = null
                }
                wasUpdateLoading = false
            }

            is UiState.Loading -> {
                wasUpdateLoading = true
            }

            is UiState.Error -> {
                // 409 exits silently via Idle (the VM reloads); a plain failure (400 validation,
                // 5xx) keeps edit mode with the inline error.
                fieldError = state.message
                wasUpdateLoading = false
            }
        }
    }

    // H4 — mutual exclusion with in-flight mutations: a new edit can't open while a PATCH or the
    // anonymize POST is in flight (an unrelated Success would otherwise force-exit the new edit,
    // silently discarding its draft; an anonymize in flight must never race a fresh PATCH).
    fun startEdit(field: ClientField) {
        if (updateState is UiState.Loading) return
        if (anonymizeState is UiState.Loading) return
        editingField = field
        draftValue = currentFieldValue(client, field)
        fieldError = null
    }

    fun exitEdit() {
        editingField = null
        draftValue = ""
        fieldError = null
    }

    fun commitEdit(field: ClientField) {
        if (editingField != field) return
        if (updateState is UiState.Loading) return
        if (anonymizeState is UiState.Loading) return
        val trimmed = draftValue.trim()
        if (trimmed == currentFieldValue(client, field)) {
            exitEdit()
            return
        }
        val patch = patchFor(field, trimmed) { fieldError = it }
        if (patch != null) {
            viewModel.updateClient(client.id, patch)
        }
    }

    // D4 — BP pair commit: the editor validates + builds the request; unchanged pair → silent exit.
    fun commitBpPair(patch: UpdateClientRequest) {
        if (editingField != ClientField.BP_PAIR) return
        if (updateState is UiState.Loading) return
        if (anonymizeState is UiState.Loading) return
        viewModel.updateClient(client.id, patch)
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
                        onDraftChange = { draftValue = it },
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                    )
                    ClientFieldEditor(
                        label = "Middle name",
                        value = client.middleName.orEmpty(),
                        field = ClientField.MIDDLE_NAME,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = { draftValue = it },
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                    )
                    ClientFieldEditor(
                        label = "Last name",
                        value = client.lastName.orEmpty(),
                        field = ClientField.LAST_NAME,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = { draftValue = it },
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                    )
                    ClientFieldEditor(
                        label = "Suffix",
                        value = client.suffix.orEmpty(),
                        field = ClientField.SUFFIX,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = { draftValue = it },
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                    )
                    GenderFieldEditor(
                        value = client.gender.displayName(),
                        field = ClientField.GENDER,
                        editingField = editingField,
                        draftValue = draftValue,
                        onDraftChange = { draftValue = it },
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                    )
                    ClientFieldEditor(
                        label = "Age",
                        value = client.age.toString(),
                        field = ClientField.AGE,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = { draftValue = it },
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
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
                        onDraftChange = { draftValue = it },
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                        keyboardType = KeyboardType.Phone,
                    )
                    ClientFieldEditor(
                        label = "Address",
                        value = client.address.orEmpty(),
                        field = ClientField.ADDRESS,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = { draftValue = it },
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                    )
                    // D4 BP pair rule — both fields or neither (backend 400s otherwise): the pair
                    // is one editor, committed together, so a null-BP client can gain BP values.
                    BpPairEditor(
                        systolic = client.systolicBp,
                        diastolic = client.diastolicBp,
                        editing = editingField == ClientField.BP_PAIR,
                        fieldError = if (editingField == ClientField.BP_PAIR) fieldError else null,
                        onError = { fieldError = it },
                        onStartEdit = { startEdit(ClientField.BP_PAIR) },
                        onCommitPair = ::commitBpPair,
                        onCancel = ::exitEdit,
                    )
                    ClientFieldEditor(
                        label = "Medical conditions",
                        value = client.medicalConditions.orEmpty(),
                        field = ClientField.MEDICAL_CONDITIONS,
                        editingField = editingField,
                        draftValue = draftValue,
                        fieldError = fieldError,
                        onDraftChange = { draftValue = it },
                        onStartEdit = ::startEdit,
                        onCommit = ::commitEdit,
                        onCancel = ::exitEdit,
                    )
                },
                actions = {
                    // D5 — destructive styling (error/onError tokens per the #96 badge precedent).
                    OutlinedButton(
                        onClick = { showAnonymizeDialog = true },
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
                            text = (anonymizeState as UiState.Error).message,
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
            // the soft-deleted row). Confirm stays disabled until the edit resolves.
            editInFlight = updateState is UiState.Loading,
            onConfirm = {
                showAnonymizeDialog = false
                viewModel.anonymizeClient(client.id)
            },
            onDismiss = { showAnonymizeDialog = false },
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
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCommit(field) }),
                    modifier =
                        Modifier
                            .weight(1f)
                            .onFocusChanged { if (!it.isFocused) onCommit(field) }
                            .onPreviewKeyEvent {
                                if (it.key == Key.Escape) {
                                    onCancel()
                                    true
                                } else {
                                    false
                                }
                            },
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
                            .clickable { onStartEdit(field) }
                            .padding(start = Spacing.xs),
                )
            }
        }
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * D4 — blood-pressure pair editor. The pair is one logical field: both inputs or neither
 * (backend 400s otherwise). Commits both values in one partial PATCH; the pair can be entered
 * from null (both empty → type both → commit).
 */
@Composable
private fun BpPairEditor(
    systolic: Short?,
    diastolic: Short?,
    editing: Boolean,
    fieldError: String?,
    onError: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommitPair: (UpdateClientRequest) -> Unit,
    onCancel: () -> Unit,
) {
    var draftSystolic by remember { mutableStateOf("") }
    var draftDiastolic by remember { mutableStateOf("") }
    // H3 — blur-commit fires only when the user actually typed: without this, tapping from
    // systolic to diastolic blurs field 1 with both drafts still holding the seeded values →
    // commitPair() sees "unchanged" and cancels the edit before the user typed anything.
    var dirty by remember { mutableStateOf(false) }

    // Re-seed drafts each time edit mode is entered (values may have changed via a 409 reload).
    LaunchedEffect(editing) {
        if (editing) {
            draftSystolic = systolic?.toString().orEmpty()
            draftDiastolic = diastolic?.toString().orEmpty()
            dirty = false
        }
    }

    fun commitPair() {
        if (!editing) return
        val sys = draftSystolic.trim()
        val dia = draftDiastolic.trim()
        if (sys.isEmpty() || dia.isEmpty()) {
            onError("Both BP fields are required")
            return
        }
        val sysVal = sys.toShortOrNull()
        val diaVal = dia.toShortOrNull()
        if (sysVal == null || diaVal == null) {
            onError("Enter a valid number")
            return
        }
        if (sysVal == systolic && diaVal == diastolic) {
            onCancel()
            return
        }
        onCommitPair(UpdateClientRequest(systolicBp = sysVal, diastolicBp = diaVal))
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draftSystolic,
                    onValueChange = {
                        draftSystolic = it
                        dirty = true
                    },
                    singleLine = true,
                    isError = fieldError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitPair() }),
                    modifier =
                        Modifier
                            .weight(1f)
                            // Blur commits only once the pair is complete AND the user typed —
                            // otherwise tapping the second field would prematurely surface
                            // "Both BP fields are required" or silently cancel the edit.
                            .onFocusChanged {
                                if (!it.isFocused && dirty && draftSystolic.isNotBlank() &&
                                    draftDiastolic.isNotBlank()
                                ) {
                                    commitPair()
                                }
                            }.onPreviewKeyEvent {
                                if (it.key == Key.Escape) {
                                    onCancel()
                                    true
                                } else {
                                    false
                                }
                            },
                )
                Text(
                    text = "/",
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = draftDiastolic,
                    onValueChange = {
                        draftDiastolic = it
                        dirty = true
                    },
                    singleLine = true,
                    isError = fieldError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitPair() }),
                    modifier =
                        Modifier
                            .weight(1f)
                            .onFocusChanged {
                                if (!it.isFocused && dirty && draftSystolic.isNotBlank() &&
                                    draftDiastolic.isNotBlank()
                                ) {
                                    commitPair()
                                }
                            }.onPreviewKeyEvent {
                                if (it.key == Key.Escape) {
                                    onCancel()
                                    true
                                } else {
                                    false
                                }
                            },
                )
            }
        } else {
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
                            .clickable(onClick = onStartEdit)
                            .padding(start = Spacing.xs),
                )
            }
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

/** D4 — gender edits via dropdown; commit fires on select ("commit on select"). */
@Composable
private fun GenderFieldEditor(
    value: String,
    field: ClientField,
    editingField: ClientField?,
    draftValue: String,
    onDraftChange: (String) -> Unit,
    onStartEdit: (ClientField) -> Unit,
    onCommit: (ClientField) -> Unit,
) {
    val editing = editingField == field
    var menuOpen by remember { mutableStateOf(false) }

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
            Box {
                OutlinedTextField(
                    value = if (draftValue == Gender.M.name) Gender.M.displayName() else Gender.F.displayName(),
                    onValueChange = {},
                    singleLine = true,
                    readOnly = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { menuOpen = true },
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(Gender.M.displayName()) },
                        onClick = {
                            menuOpen = false
                            onDraftChange(Gender.M.name)
                            onCommit(field)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(Gender.F.displayName()) },
                        onClick = {
                            menuOpen = false
                            onDraftChange(Gender.F.name)
                            onCommit(field)
                        },
                    )
                }
            }
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
                            .clickable { onStartEdit(field) }
                            .padding(start = Spacing.xs),
                )
            }
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
