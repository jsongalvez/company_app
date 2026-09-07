package com.companyb.companyapp.finance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.companyb.companyapp.contracts.finance.ExpenseResponse
import com.companyb.companyapp.ui.theme.Spacing

/** #479 LPL burn — the expense dialog's editable field state as one object. */
internal data class ExpenseFieldState(
    val amount: String,
    val amountError: String?,
    val dialogCategories: List<Pair<String, String>>,
    val categoryIndex: Int,
    val notes: String,
    val reason: String,
    val error: String?,
)

/** #479 LPL burn — the expense dialog's field handlers as one object. */
internal data class ExpenseFieldHandlers(
    val onAmountChange: (String) -> Unit,
    val onCategorySelected: (Int) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onReasonChange: (String) -> Unit,
)

@Composable
internal fun ExpenseDialog(
    title: String,
    initial: ExpenseResponse?,
    progress: DialogProgress,
    onConfirm: (String, String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(initial?.amount ?: "") }
    val dialogCategories = remember(initial) { expenseDialogCategories(initial) }
    var categoryIndex by remember { mutableStateOf(expenseCategoryIndex(initial, dialogCategories)) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var reason by remember { mutableStateOf("") }
    val amountError = expenseAmountError(amount)

    AlertDialog(
        onDismissRequest = { if (!progress.busy) onDismiss() },
        title = { Text(title) },
        text = {
            ExpenseDialogFields(
                fields =
                    ExpenseFieldState(
                        amount = amount,
                        amountError = amountError,
                        dialogCategories = dialogCategories,
                        categoryIndex = categoryIndex,
                        notes = notes,
                        reason = reason,
                        error = progress.error,
                    ),
                handlers =
                    ExpenseFieldHandlers(
                        onAmountChange = { amount = it },
                        onCategorySelected = { categoryIndex = it },
                        onNotesChange = { notes = it },
                        onReasonChange = { reason = it },
                    ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (amountError == null) {
                        onConfirm(
                            amount,
                            dialogCategories[categoryIndex].first,
                            notes.trim().ifBlank { null },
                            reason.trim().ifBlank { null },
                        )
                    }
                },
                enabled = !progress.busy && amountError == null,
            ) {
                Text(if (progress.busy) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !progress.busy) { Text("Cancel") }
        },
    )
}

// Pass-3 HARD — an unknown backend category is shown AND sent as its raw code (never
// silently rewritten to PANTRY by an indexOf fallback); the pair list is augmented so the
// dropdown displays the raw code too.
private fun expenseDialogCategories(initial: ExpenseResponse?): List<Pair<String, String>> =
    initial
        ?.category
        ?.name
        ?.takeIf { it !in expenseCategoryCodes }
        ?.let { code -> expenseCategories + (code to code) }
        ?: expenseCategories

private fun expenseCategoryIndex(
    initial: ExpenseResponse?,
    dialogCategories: List<Pair<String, String>>,
): Int =
    initial
        ?.category
        ?.name
        ?.let { code -> dialogCategories.indexOfFirst { it.first == code } }
        ?.takeIf { it >= 0 }
        ?: 0

@Composable
private fun ExpenseDialogFields(
    fields: ExpenseFieldState,
    handlers: ExpenseFieldHandlers,
) {
    Column {
        OutlinedTextField(
            value = fields.amount,
            onValueChange = handlers.onAmountChange,
            label = { Text("Amount (₱, positive)") },
            singleLine = true,
            isError = fields.amountError != null,
            supportingText = { if (fields.amountError != null) Text(fields.amountError) },
        )
        Spacer(Modifier.height(Spacing.xs))
        CategoryDropdown(
            selected = fields.dialogCategories[fields.categoryIndex].second,
            categories = fields.dialogCategories,
            onSelected = handlers.onCategorySelected,
        )
        Spacer(Modifier.height(Spacing.xs))
        OutlinedTextField(
            value = fields.notes,
            onValueChange = handlers.onNotesChange,
            label = { Text("Notes (optional)") },
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
private fun CategoryDropdown(
    selected: String,
    categories: List<Pair<String, String>>,
    onSelected: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            categories.forEachIndexed { index, (_, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(index)
                        open = false
                    },
                )
            }
        }
    }
}
