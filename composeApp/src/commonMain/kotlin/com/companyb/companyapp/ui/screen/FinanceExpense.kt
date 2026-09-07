package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.FinanceReportsViewModel

private const val CREATED_BY_PREFIX_LENGTH = 8

/** #479 LPL burn — the expense mutations the section hosts perform, as one object. */
internal data class ExpenseActions(
    val onCreate: (String, String, String?, String?) -> Unit,
    val onUpdate: (ExpenseResponse, String, String, String?, String?) -> Unit,
    val onDelete: (ExpenseResponse, String) -> Unit,
    val onRestore: (ExpenseResponse, String?) -> Unit,
)

/** #479 LPL burn — a row's edit/delete/restore affordances as one object. */
internal data class ExpenseRowActions(
    val onEdit: () -> Unit,
    val onDelete: () -> Unit,
    val onRestore: () -> Unit,
)

/** #479 LPL burn — the open expense dialogs' targets as one object. */
internal data class ExpenseDialogTargets(
    val editing: ExpenseResponse?,
    val deleting: ExpenseResponse?,
    val restoring: ExpenseResponse?,
)

/** #479 LPL burn — the dialogs' dismiss/clear closures as one object. */
internal data class ExpenseDialogClosures(
    val onCreateClose: () -> Unit,
    val onEditClear: () -> Unit,
    val onDeleteClear: () -> Unit,
    val onRestoreClear: () -> Unit,
)

@Composable
internal fun ExpenseSection(
    viewModel: FinanceReportsViewModel,
    expenses: UiState<List<ExpenseResponse>>,
    ui: FinanceSectionUi,
    actions: ExpenseActions,
    onReload: () -> Unit,
) {
    var showExpenseDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseResponse?>(null) }
    var deleting by remember { mutableStateOf<ExpenseResponse?>(null) }
    var restoring by remember { mutableStateOf<ExpenseResponse?>(null) }
    val targets = ExpenseDialogTargets(editing = editing, deleting = deleting, restoring = restoring)
    ExpenseSectionEffects(
        viewModel = viewModel,
        ui = ui,
        targets = targets,
        closures =
            ExpenseDialogClosures(
                onCreateClose = { showExpenseDialog = false },
                onEditClear = { editing = null },
                onDeleteClear = { deleting = null },
                onRestoreClear = { restoring = null },
            ),
    )
    SectionHeader(
        title = "Expenses",
        actionLabel = "Add expense",
        onAction = { showExpenseDialog = true },
        showAction = !ui.readOnly,
    )
    ExpenseSectionStatus(expenses = expenses, onReload = onReload)
    if (expenses is UiState.Success) {
        ExpenseSuccessList(
            expenses = expenses.data,
            ui = ui,
            onEdit = { editing = it },
            onDelete = { deleting = it },
            onRestore = { restoring = it },
        )
    }
    ExpenseSectionDialogs(
        showExpenseDialog = showExpenseDialog,
        targets = targets,
        ui = ui,
        actions = actions,
        closures =
            ExpenseDialogClosures(
                onCreateClose = { showExpenseDialog = false },
                onEditClear = { editing = null },
                onDeleteClear = { deleting = null },
                onRestoreClear = { restoring = null },
            ),
    )
}

@Composable
private fun ExpenseSectionEffects(
    viewModel: FinanceReportsViewModel,
    ui: FinanceSectionUi,
    targets: ExpenseDialogTargets,
    closures: ExpenseDialogClosures,
) {
    // Pass-8 HARD — a success closes its dialog: a deliberate re-save would duplicate the
    // row (the POST is idempotent only per client UUID). The transition is in→out of the
    // key's in-flight set with no error for the key (a 409/network failure keeps it open).
    var createWasInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(ui.inFlight) {
        val busy = "expense:create" in ui.inFlight
        if (createWasInFlight && !busy && ui.errors["expense:create"] == null) closures.onCreateClose()
        createWasInFlight = busy
    }
    ExpenseMutationTracker(ui.inFlight, ui.errors, targets.editing, "expense:update", closures.onEditClear)
    ExpenseMutationTracker(ui.inFlight, ui.errors, targets.deleting, "expense:delete", closures.onDeleteClear)
    ExpenseMutationTracker(ui.inFlight, ui.errors, targets.restoring, "expense:restore", closures.onRestoreClear)
    // Pass-1/2 HARD — a 409 closes the open edit dialog: it holds a stale expectedVersion, so a
    // re-save would loop 409s; the reloaded row is the retry source. The key is consumed so a
    // repeat 409 on the same row re-emits and a persisted key can't slam a later dialog shut.
    LaunchedEffect(ui.conflicts) {
        val e = targets.editing
        val key = e?.let { "expense:update:${it.id}" }
        if (key != null && key in ui.conflicts) {
            closures.onEditClear()
            viewModel.consumeConflict(key)
        }
    }
}

@Composable
private fun ExpenseMutationTracker(
    inFlight: Set<String>,
    errors: Map<String, String>,
    target: ExpenseResponse?,
    keyPrefix: String,
    onClear: () -> Unit,
) {
    var wasInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(inFlight, target) {
        val busyKey = target?.let { "$keyPrefix:${it.id}" }
        val busy = busyKey != null && busyKey in inFlight
        val done = wasInFlight && !busy && target != null && errors[busyKey] == null
        if (done) onClear()
        wasInFlight = busy
    }
}

@Composable
private fun ExpenseSectionStatus(
    expenses: UiState<List<ExpenseResponse>>,
    onReload: () -> Unit,
) {
    when (expenses) {
        is UiState.Idle -> {}

        is UiState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(Spacing.sm), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.width(Spacing.lg).height(Spacing.lg), strokeWidth = 2.dp)
            }
        }

        is UiState.Error -> {
            logWarn("FinanceReportsScreen", "expenses=Error: ${expenses.message}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = expenses.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onReload) { Text("Retry") }
            }
        }

        is UiState.Success -> {}
    }
}

@Composable
private fun ExpenseSuccessList(
    expenses: List<ExpenseResponse>,
    ui: FinanceSectionUi,
    onEdit: (ExpenseResponse) -> Unit,
    onDelete: (ExpenseResponse) -> Unit,
    onRestore: (ExpenseResponse) -> Unit,
) {
    if (expenses.isEmpty()) {
        Text(
            text = "No expenses logged for this day.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            modifier = Modifier.padding(Spacing.sm),
        )
    }
    expenses.forEach { expense ->
        ExpenseRow(
            expense = expense,
            error =
                ui.errors["expense:update:${expense.id}"]
                    ?: ui.errors["expense:delete:${expense.id}"]
                    ?: ui.errors["expense:restore:${expense.id}"],
            busy =
                "expense:update:${expense.id}" in ui.inFlight ||
                    "expense:delete:${expense.id}" in ui.inFlight ||
                    "expense:restore:${expense.id}" in ui.inFlight,
            readOnly = ui.readOnly,
            actions =
                ExpenseRowActions(
                    onEdit = { onEdit(expense) },
                    onDelete = { onDelete(expense) },
                    onRestore = { onRestore(expense) },
                ),
        )
    }
    if (ui.errors["expense:create"] != null) {
        Text(
            text = ui.errors.getValue("expense:create"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(Spacing.xs),
        )
    }
}

@Composable
private fun ExpenseSectionDialogs(
    showExpenseDialog: Boolean,
    targets: ExpenseDialogTargets,
    ui: FinanceSectionUi,
    actions: ExpenseActions,
    closures: ExpenseDialogClosures,
) {
    if (showExpenseDialog) {
        ExpenseDialog(
            title = "Log expense",
            initial = null,
            progress = expenseProgress(ui, "expense:create"),
            onConfirm = actions.onCreate,
            onDismiss = closures.onCreateClose,
        )
    }
    val currentEdit = targets.editing
    if (currentEdit != null) {
        ExpenseDialog(
            title = "Edit expense",
            initial = currentEdit,
            progress = expenseProgress(ui, "expense:update:${currentEdit.id}"),
            onConfirm = { amount, category, notes, reason ->
                actions.onUpdate(currentEdit, amount, category, notes, reason)
            },
            onDismiss = closures.onEditClear,
        )
    }
    val currentDelete = targets.deleting
    if (currentDelete != null) {
        ReasonDialog(
            spec =
                ReasonDialogSpec(
                    title = "Delete expense?",
                    message = "Deletion requires a reason.",
                    requireReason = true,
                    progress = expenseProgress(ui, "expense:delete:${currentDelete.id}"),
                ),
            // requireReason=true guarantees the confirm never fires with null.
            onConfirm = { reason -> actions.onDelete(currentDelete, reason ?: "") },
            onDismiss = closures.onDeleteClear,
        )
    }
    val currentRestore = targets.restoring
    if (currentRestore != null) {
        ReasonDialog(
            spec =
                ReasonDialogSpec(
                    title = "Restore expense?",
                    message =
                        "The row returns to full edit/delete affordances. A reason is required only on remitted days.",
                    requireReason = false,
                    progress = expenseProgress(ui, "expense:restore:${currentRestore.id}"),
                ),
            onConfirm = { reason -> actions.onRestore(currentRestore, reason) },
            onDismiss = closures.onRestoreClear,
        )
    }
}

private fun expenseProgress(
    ui: FinanceSectionUi,
    key: String,
): DialogProgress = DialogProgress(busy = key in ui.inFlight, error = ui.errors[key])

@Composable
private fun ExpenseRow(
    expense: ExpenseResponse,
    error: String?,
    busy: Boolean,
    readOnly: Boolean,
    actions: ExpenseRowActions,
) {
    val deleted = expense.deletedAt != null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = expense.category.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (deleted) InkSubtle else MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .width(140.dp)
                    .then(if (deleted) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier),
        )
        Text(
            text = peso(expense.amount),
            style = MaterialTheme.typography.bodySmall,
            color = if (deleted) InkSubtle else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = expense.notes ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        // #101 D6 — createdBy + createdAt on the row.
        val createdByPrefix = expense.createdBy.take(CREATED_BY_PREFIX_LENGTH)
        Text(
            text = "$createdByPrefix · ${formatRelativeTimestamp(expense.createdAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            maxLines = 1,
        )
        Spacer(Modifier.width(Spacing.sm))
        ExpenseRowStatusActions(
            deleted = deleted,
            deletedReason = expense.deletedReason,
            readOnly = readOnly,
            busy = busy,
            actions = actions,
        )
    }
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
        )
    }
}

@Composable
private fun ExpenseRowStatusActions(
    deleted: Boolean,
    deletedReason: String?,
    readOnly: Boolean,
    busy: Boolean,
    actions: ExpenseRowActions,
) {
    if (deleted) {
        Text(
            text = "removed",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = deletedReason ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
        if (!readOnly) {
            Spacer(Modifier.width(Spacing.sm))
            TextButton(onClick = actions.onRestore, enabled = !busy) { Text("Restore") }
        }
    } else if (!readOnly) {
        TextButton(onClick = actions.onEdit, enabled = !busy) { Text("Edit") }
        TextButton(onClick = actions.onDelete, enabled = !busy) { Text("Delete") }
    }
}
