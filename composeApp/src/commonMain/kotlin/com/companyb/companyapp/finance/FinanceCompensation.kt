package com.companyb.companyapp.finance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.dto.BranchDayUserResponse
import com.companyb.companyapp.dto.CompensationResponse
import com.companyb.companyapp.ui.screen.peso
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logWarn

/** #479 LPL burn — the compensation mutations the section performs, as one object. */
internal data class CompensationActions(
    val onCreate: (String, String, String?, String?) -> Unit,
    val onUpdate: (CompensationResponse, String, String?, String?) -> Unit,
)

/** #479 LPL burn — the open compensation dialogs' flags as one object. */
internal data class CompensationDialogUi(
    val showAssign: Boolean,
    val editing: CompensationResponse?,
)

/** #479 LPL burn — the compensation dialogs' dismiss closures as one object. */
internal data class CompensationDialogClosures(
    val onAssignDismiss: () -> Unit,
    val onEditDismiss: () -> Unit,
)

/** #479 LPL burn — the compensation dialog's static spec + progress as one object. */
internal data class CompensationDialogSpec(
    val title: String,
    val users: UiState<List<BranchDayUserResponse>>,
    val initial: CompensationResponse?,
    val progress: DialogProgress,
)

@Composable
internal fun CompensationSection(
    viewModel: FinanceReportsViewModel,
    compensations: UiState<List<CompensationResponse>>,
    users: UiState<List<BranchDayUserResponse>>,
    ui: FinanceAssignUi,
    actions: CompensationActions,
) {
    var showAssign by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CompensationResponse?>(null) }
    CompensationSectionEffects(
        viewModel = viewModel,
        ui = ui,
        editing = editing,
        onEditingClear = { editing = null },
        onAssignClose = { showAssign = false },
    )
    SectionHeader(
        title = "Compensation",
        actionLabel = "Assign compensation",
        onAction = { showAssign = true },
        showAction = ui.canAssign && !ui.readOnly && users is UiState.Success && users.data.isNotEmpty(),
    )
    when (compensations) {
        is UiState.Idle -> {}

        is UiState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(Spacing.sm), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.width(Spacing.lg).height(Spacing.lg), strokeWidth = 2.dp)
            }
        }

        is UiState.Error -> {
            logWarn("FinanceReportsScreen", "compensations=Error: ${compensations.message}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = compensations.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { viewModel.reloadSection(EditSection.COMPENSATIONS) }) { Text("Retry") }
            }
        }

        is UiState.Success -> {
            CompensationSuccessList(
                compensations = compensations.data,
                ui = ui,
                onEdit = { editing = it },
            )
        }
    }
    CompensationSectionDialogs(
        dialogs = CompensationDialogUi(showAssign = showAssign, editing = editing),
        users = users,
        ui = ui,
        actions = actions,
        closures =
            CompensationDialogClosures(
                onAssignDismiss = { showAssign = false },
                onEditDismiss = { editing = null },
            ),
    )
}

@Composable
private fun CompensationSectionEffects(
    viewModel: FinanceReportsViewModel,
    ui: FinanceAssignUi,
    editing: CompensationResponse?,
    onEditingClear: () -> Unit,
    onAssignClose: () -> Unit,
) {
    LaunchedEffect(ui.conflicts) {
        val e = editing
        val key = e?.let { "comp:update:${it.id}" }
        if (key != null && key in ui.conflicts) {
            onEditingClear()
            viewModel.consumeConflict(key)
        }
    }
    var assignWasInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(ui.inFlight) {
        val busy = "comp:create" in ui.inFlight
        if (assignWasInFlight && !busy && ui.errors["comp:create"] == null) onAssignClose()
        assignWasInFlight = busy
    }
    var editWasInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(ui.inFlight, editing) {
        val busyKey = editing?.let { "comp:update:${it.id}" }
        val busy = busyKey != null && busyKey in ui.inFlight
        val editDone = editWasInFlight && !busy && editing != null && ui.errors[busyKey] == null
        if (editDone) onEditingClear()
        editWasInFlight = busy
    }
}

@Composable
private fun CompensationSuccessList(
    compensations: List<CompensationResponse>,
    ui: FinanceAssignUi,
    onEdit: (CompensationResponse) -> Unit,
) {
    if (compensations.isEmpty()) {
        Text(
            text = "No compensation assigned for this day.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            modifier = Modifier.padding(Spacing.sm),
        )
    }
    compensations.forEach { compensation ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = compensation.userName ?: compensation.userId,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = peso(compensation.amount),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = compensation.note ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
                maxLines = 1,
            )
            // #101 D5 — assignedAt on the row.
            Text(
                text = formatRelativeTimestamp(compensation.assignedAt),
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
                maxLines = 1,
            )
            if (!ui.readOnly) {
                TextButton(
                    onClick = { onEdit(compensation) },
                    enabled = "comp:update:${compensation.id}" !in ui.inFlight,
                ) {
                    Text("Edit")
                }
            }
        }
        val error = ui.errors["comp:update:${compensation.id}"]
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
            )
        }
    }
}

@Composable
private fun CompensationSectionDialogs(
    dialogs: CompensationDialogUi,
    users: UiState<List<BranchDayUserResponse>>,
    ui: FinanceAssignUi,
    actions: CompensationActions,
    closures: CompensationDialogClosures,
) {
    if (dialogs.showAssign) {
        CompensationDialog(
            spec =
                CompensationDialogSpec(
                    title = "Assign compensation",
                    users = users,
                    initial = null,
                    progress = DialogProgress(busy = "comp:create" in ui.inFlight, error = ui.errors["comp:create"]),
                ),
            onConfirm = { userId, amount, note, reason -> actions.onCreate(userId, amount, note, reason) },
            onDismiss = closures.onAssignDismiss,
        )
    }
    val current = dialogs.editing
    if (current != null) {
        CompensationDialog(
            spec =
                CompensationDialogSpec(
                    title = "Edit compensation",
                    users = users,
                    initial = current,
                    progress =
                        DialogProgress(
                            busy = "comp:update:${current.id}" in ui.inFlight,
                            error = ui.errors["comp:update:${current.id}"],
                        ),
                ),
            onConfirm = { userId, amount, note, reason -> actions.onUpdate(current, amount, note, reason) },
            onDismiss = closures.onEditDismiss,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompensationDialog(
    spec: CompensationDialogSpec,
    onConfirm: (String, String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var userId by remember {
        mutableStateOf(spec.initial?.userId ?: (spec.users as? UiState.Success)?.data?.firstOrNull()?.userId ?: "")
    }
    var amount by remember { mutableStateOf(spec.initial?.amount ?: "") }
    var note by remember { mutableStateOf(spec.initial?.note ?: "") }
    var reason by remember { mutableStateOf("") }
    val amountError = compensationAmountError(amount)
    val usersList = (spec.users as? UiState.Success)?.data.orEmpty()

    AlertDialog(
        onDismissRequest = { if (!spec.progress.busy) onDismiss() },
        title = { Text(spec.title) },
        text = {
            CompensationDialogFields(
                fields =
                    CompensationFieldState(
                        usersList = usersList,
                        userId = userId,
                        amount = amount,
                        amountError = amountError,
                        note = note,
                        reason = reason,
                        error = spec.progress.error,
                    ),
                handlers =
                    CompensationFieldHandlers(
                        onUserSelected = { userId = it },
                        onAmountChange = { amount = it },
                        onNoteChange = { note = it },
                        onReasonChange = { reason = it },
                    ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (amountError == null && userId.isNotBlank()) {
                        onConfirm(userId, amount, note.trim().ifBlank { null }, reason.trim().ifBlank { null })
                    }
                },
                enabled = !spec.progress.busy && amountError == null && userId.isNotBlank(),
            ) {
                Text(if (spec.progress.busy) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !spec.progress.busy) { Text("Cancel") }
        },
    )
}

/** #479 LPL burn — the compensation dialog's editable field state as one object. */
internal data class CompensationFieldState(
    val usersList: List<BranchDayUserResponse>,
    val userId: String,
    val amount: String,
    val amountError: String?,
    val note: String,
    val reason: String,
    val error: String?,
)

/** #479 LPL burn — the compensation dialog's field handlers as one object. */
internal data class CompensationFieldHandlers(
    val onUserSelected: (String) -> Unit,
    val onAmountChange: (String) -> Unit,
    val onNoteChange: (String) -> Unit,
    val onReasonChange: (String) -> Unit,
)

@Composable
private fun CompensationDialogFields(
    fields: CompensationFieldState,
    handlers: CompensationFieldHandlers,
) {
    Column {
        UserDropdown(users = fields.usersList, selectedUserId = fields.userId, onSelected = handlers.onUserSelected)
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = fields.amount,
            onValueChange = handlers.onAmountChange,
            label = { Text("Amount (₱, non-negative)") },
            singleLine = true,
            isError = fields.amountError != null,
            supportingText = { if (fields.amountError != null) Text(fields.amountError) },
        )
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = fields.note,
            onValueChange = handlers.onNoteChange,
            label = { Text("Note (optional)") },
        )
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = fields.reason,
            onValueChange = handlers.onReasonChange,
            label = { Text("Reason (required on remitted days)") },
        )
        if (fields.error != null) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = fields.error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UserDropdown(
    users: List<BranchDayUserResponse>,
    selectedUserId: String,
    onSelected: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selectedName = users.firstOrNull { it.userId == selectedUserId }?.displayName ?: "Select user"
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("User (worked this day)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            users.forEach { user ->
                DropdownMenuItem(
                    text = { Text(user.displayName) },
                    onClick = {
                        onSelected(user.userId)
                        open = false
                    },
                )
            }
        }
    }
}
