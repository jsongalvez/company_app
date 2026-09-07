package com.companyb.companyapp.commerce.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.commerce.catalog.ProductViewModel
import com.companyb.companyapp.domain.InventoryMovementReason
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.ProductResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatRelativeTimestamp

/**
 * #392 — the Inventory write dialogs (the EditSlotDialog shape: client-side validation mirrors
 * the backend's 400s so an invalid input never leaves the dialog; Save hands parsed values to
 * the caller and closes immediately — the VM refreshes the list on success and a failure
 * surfaces in the screen's banner).
 */

internal fun reasonLabel(reason: InventoryMovementReason): String =
    when (reason) {
        InventoryMovementReason.TESTER -> "Tester"
        InventoryMovementReason.SAMPLE -> "Sample"
        InventoryMovementReason.MISSING -> "Missing"
        InventoryMovementReason.ADJUSTMENT -> "Adjustment"
        InventoryMovementReason.RESTOCK -> "Restock"
        InventoryMovementReason.SALE -> "Sale"
    }

@Composable
internal fun OptionalReasonField(
    editReason: String,
    onEditReasonChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = editReason,
        onValueChange = onEditReasonChange,
        label = { Text("Reason (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun UnitsField(
    label: String,
    unitsText: String,
    error: String?,
    onUnitsChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = unitsText,
        onValueChange = onUnitsChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
    )
}

@Composable
internal fun RestockDialog(
    card: BranchInventoryResponse,
    onDismiss: () -> Unit,
    onSave: (
        units: Int,
        editReason: String?,
    ) -> Unit,
) {
    var unitsText by remember { mutableStateOf("") }
    var editReason by remember { mutableStateOf("") }
    var unitsError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restock — ${card.productName}") },
        text = {
            Column {
                UnitsField("Units added", unitsText, unitsError) { unitsText = it }
                Spacer(Modifier.size(Spacing.sm))
                OptionalReasonField(editReason, { editReason = it })
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val error = restockUnitsError(unitsText)
                    if (error == null) onSave(unitsText.trim().toInt(), editReason) else unitsError = error
                },
            ) {
                Text("Restock")
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
private fun ReasonChips(
    selected: InventoryMovementReason,
    options: List<InventoryMovementReason>,
    onSelect: (InventoryMovementReason) -> Unit,
) {
    Row {
        options.forEach { candidate ->
            FilterChip(
                selected = selected == candidate,
                onClick = { onSelect(candidate) },
                label = { Text(reasonLabel(candidate)) },
            )
            Spacer(Modifier.size(Spacing.xs))
        }
    }
}

/** Text-field state for [MovementDialog] (the InviteMintForm shape). */
private class MovementForm(
    val allowedReasons: List<InventoryMovementReason>,
    initialReason: InventoryMovementReason,
) {
    var reason by mutableStateOf(initialReason)
    var unitsText by mutableStateOf("")
    var notes by mutableStateOf("")
    var editReason by mutableStateOf("")
    var unitsError by mutableStateOf<String?>(null)
    var notesError by mutableStateOf<String?>(null)
}

@Composable
private fun MovementFields(form: MovementForm) {
    Column {
        ReasonChips(form.reason, form.allowedReasons) { form.reason = it }
        Spacer(Modifier.size(Spacing.sm))
        UnitsField(
            if (form.reason == InventoryMovementReason.ADJUSTMENT) "Signed quantity change" else "Units moved out",
            form.unitsText,
            form.unitsError,
        ) { form.unitsText = it }
        Spacer(Modifier.size(Spacing.sm))
        OutlinedTextField(
            value = form.notes,
            onValueChange = { form.notes = it },
            label = { Text("Notes") },
            singleLine = true,
            isError = form.notesError != null,
            supportingText = { form.notesError?.let { Text(it) } },
        )
        Spacer(Modifier.size(Spacing.sm))
        OptionalReasonField(form.editReason, { form.editReason = it })
    }
}

@Composable
internal fun MovementDialog(
    card: BranchInventoryResponse,
    allowedReasons: List<InventoryMovementReason>,
    onDismiss: () -> Unit,
    onSave: (
        reason: InventoryMovementReason,
        units: Int,
        notes: String?,
        editReason: String?,
    ) -> Unit,
) {
    val form = remember { MovementForm(allowedReasons, allowedReasons.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record movement — ${card.productName}") },
        text = { MovementFields(form) },
        confirmButton = {
            TextButton(
                onClick = {
                    form.unitsError = movementUnitsError(form.reason, form.unitsText)
                    form.notesError = movementNotesError(form.reason, form.notes)
                    if (form.unitsError == null && form.notesError == null) {
                        onSave(form.reason, form.unitsText.trim().toInt(), form.notes, form.editReason)
                    }
                },
            ) {
                Text("Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * #395 — pick a catalog product that has no card at this branch and ensure it. The product read
 * rides the shared [ProductViewModel] (`GET /api/products`); options come from the pure
 * [productsWithoutCards] mapper over it and the already-loaded cards. Save hands the id to the
 * caller and closes immediately (the #392 dialog shape) — success refreshes, failure surfaces
 * in the screen's banner. The 201-no-body ensure is idempotent server-side, so a raced card is
 * harmless.
 */
@Composable
internal fun EnsureCardDialog(
    productViewModel: ProductViewModel,
    cards: List<BranchInventoryResponse>,
    onDismiss: () -> Unit,
    onSave: (productId: String) -> Unit,
) {
    val products by productViewModel.products.collectAsState()
    LaunchedEffect(Unit) { productViewModel.loadProducts() }
    var selectedId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add inventory card") },
        text = {
            when (val state = products) {
                is UiState.Idle,
                is UiState.Loading,
                -> {
                    DialogLoadingSpinner()
                }

                is UiState.Error -> {
                    DialogErrorRetry(state.message, onRetry = { productViewModel.loadProducts() })
                }

                is UiState.Success -> {
                    EnsureCardPicker(
                        options = productsWithoutCards(state.data, cards),
                        selectedId = selectedId,
                        onSelect = { selectedId = it },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedId != null,
                onClick = { selectedId?.let(onSave) },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** The ensure-card options list: carded-free catalog products as filter chips (#395). */
@Composable
private fun EnsureCardPicker(
    options: List<ProductResponse>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    if (options.isEmpty()) {
        Text(
            text = "Every catalog product already has a card at this branch",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column(
            modifier =
                Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            options.forEach { product ->
                FilterChip(
                    selected = selectedId == product.id,
                    onClick = { onSelect(product.id) },
                    label = { Text(product.name) },
                )
            }
        }
    }
}
