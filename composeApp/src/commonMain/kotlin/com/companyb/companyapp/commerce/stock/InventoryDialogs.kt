package com.companyb.companyapp.commerce.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.commerce.catalog.ProductViewModel
import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
import com.companyb.companyapp.contracts.commerce.InventoryMovementReason
import com.companyb.companyapp.contracts.commerce.ProductResponse
import com.companyb.companyapp.ui.contract.InlineStatus
import com.companyb.companyapp.ui.contract.InlineStatusKind
import com.companyb.companyapp.ui.contract.OperationalDialog
import com.companyb.companyapp.ui.contract.operationalField
import com.companyb.companyapp.ui.theme.Spacing
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * #392 — the Inventory write dialogs (the EditSlotDialog shape: client-side validation mirrors
 * the backend's 400s so an invalid input never leaves the dialog).
 * #676 — retained lossless shape: forms stay mounted while submitting, Save keeps fixed
 * bounds with progress and disables duplicates, close happens only on confirmed success
 * (the host observes the result leg), and failures render beside submission with
 * correction/Retry reusing the same operationId — never a fresh identifier.
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
        modifier = Modifier.fillMaxWidth().operationalField(),
    )
}

@Composable
internal fun UnitsField(
    label: String,
    unitsText: String,
    error: String?,
    onUnitsChange: (String) -> Unit,
    focus: FocusRequester? = null,
) {
    OutlinedTextField(
        value = unitsText,
        onValueChange = onUnitsChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        modifier =
            if (focus != null) {
                Modifier.fillMaxWidth().operationalField().focusRequester(focus)
            } else {
                Modifier.fillMaxWidth().operationalField()
            },
    )
}

/** #676 — retained write-error copy: authoritative hint or ambiguous confirm-state, draft kept. */
@Composable
internal fun RetainedWriteError(
    result: UiState<*>,
    onRetry: () -> Unit,
    showRetry: Boolean = true,
) {
    val error = result as? UiState.Error ?: return
    val hint = writeErrorHint(error.message)
    val message =
        if (isAmbiguousWriteError(error.message)) {
            AMBIGUOUS_WRITE_MESSAGE
        } else if (hint != null) {
            "${error.message} $hint"
        } else {
            error.message
        }
    InlineStatus(
        message = message,
        kind = InlineStatusKind.FAILURE,
        onRetry = if (showRetry) onRetry else null,
    )
}

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun RestockDialog(
    card: BranchInventoryResponse,
    result: UiState<*>,
    onDismiss: () -> Unit,
    onSubmit: (
        units: Int,
        editReason: String?,
        operationId: String,
    ) -> Unit,
) {
    // #676 — text drafts survive rotation (saveable, keyed by card); the operationId is
    // stable per open for same-ID Retry.
    var unitsText by rememberSaveable(card.id) { mutableStateOf("") }
    var editReason by rememberSaveable(card.id) { mutableStateOf("") }
    var unitsError by remember(card.id) { mutableStateOf<String?>(null) }
    val operationId = remember(card.id) { Uuid.random().toString() }
    val unitsFocus = remember { FocusRequester() }
    val isBusy = result is UiState.Loading

    fun submit() {
        val error = restockUnitsError(unitsText)
        if (error == null) {
            unitsError = null
            onSubmit(unitsText.trim().toInt(), editReason, operationId)
        } else {
            unitsError = error
        }
    }

    OperationalDialog(
        title = "Restock — ${card.productName}",
        onDismiss = onDismiss,
        confirmLabel = "Restock",
        onConfirm = ::submit,
        isBusy = isBusy,
        allowCancelWhenBusy = false,
        confirmEnabled = !isBusy,
        contentFocus = unitsFocus,
        content = {
            UnitsField("Units added", unitsText, unitsError, { unitsText = it }, unitsFocus)
            Spacer(Modifier.size(Spacing.sm))
            OptionalReasonField(editReason, { editReason = it })
            RetainedWriteError(result, onRetry = ::submit)
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

@Composable
private fun MovementFields(
    reason: InventoryMovementReason,
    allowedReasons: List<InventoryMovementReason>,
    onReasonChange: (InventoryMovementReason) -> Unit,
    unitsText: String,
    unitsError: String?,
    onUnitsChange: (String) -> Unit,
    notes: String,
    notesError: String?,
    onNotesChange: (String) -> Unit,
    editReason: String,
    onEditReasonChange: (String) -> Unit,
    focus: FocusRequester,
) {
    Column {
        ReasonChips(reason, allowedReasons, onReasonChange)
        Spacer(Modifier.size(Spacing.sm))
        UnitsField(
            if (reason == InventoryMovementReason.ADJUSTMENT) "Signed quantity change" else "Units moved out",
            unitsText,
            unitsError,
            onUnitsChange,
            focus,
        )
        Spacer(Modifier.size(Spacing.sm))
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("Notes") },
            singleLine = true,
            isError = notesError != null,
            supportingText = { notesError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth().operationalField(),
        )
        Spacer(Modifier.size(Spacing.sm))
        OptionalReasonField(editReason, onEditReasonChange)
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun MovementDialog(
    card: BranchInventoryResponse,
    allowedReasons: List<InventoryMovementReason>,
    result: UiState<*>,
    onDismiss: () -> Unit,
    onSubmit: (
        reason: InventoryMovementReason,
        units: Int,
        notes: String?,
        editReason: String?,
        operationId: String,
    ) -> Unit,
) {
    // #676 — text drafts survive rotation (saveable, keyed by card). An empty reason list is
    // fail-closed (the affordance hides it; this backstop never crashes on first()).
    var reason by remember(card.id) { mutableStateOf(allowedReasons.firstOrNull()) }
    var unitsText by rememberSaveable(card.id) { mutableStateOf("") }
    var notes by rememberSaveable(card.id) { mutableStateOf("") }
    var editReason by rememberSaveable(card.id) { mutableStateOf("") }
    var unitsError by remember(card.id) { mutableStateOf<String?>(null) }
    var notesError by remember(card.id) { mutableStateOf<String?>(null) }
    val operationId = remember(card.id) { Uuid.random().toString() }
    val unitsFocus = remember { FocusRequester() }
    val isBusy = result is UiState.Loading
    val activeReason = reason

    fun submit() {
        val current = activeReason ?: return
        unitsError = movementUnitsError(current, unitsText)
        notesError = movementNotesError(current, notes)
        if (unitsError == null && notesError == null) {
            onSubmit(current, unitsText.trim().toInt(), notes, editReason, operationId)
        }
    }

    OperationalDialog(
        title = "Record movement — ${card.productName}",
        onDismiss = onDismiss,
        confirmLabel = "Record",
        onConfirm = ::submit,
        isBusy = isBusy,
        allowCancelWhenBusy = false,
        confirmEnabled = !isBusy && activeReason != null,
        contentFocus = unitsFocus,
        content = {
            if (activeReason == null) {
                Text(
                    text = "Not authorized to record movements at this branch.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                MovementFields(
                    activeReason,
                    allowedReasons,
                    { reason = it },
                    unitsText,
                    unitsError,
                    { unitsText = it },
                    notes,
                    notesError,
                    { notes = it },
                    editReason,
                    { editReason = it },
                    unitsFocus,
                )
            }
            RetainedWriteError(result, onRetry = ::submit)
        },
    )
}

/**
 * #395 — pick a catalog product that has no card at this branch and ensure it. The product read
 * rides the shared [ProductViewModel] (`GET /api/products`); options come from the pure
 * [productsWithoutCards] mapper over it and the already-loaded cards.
 * #676 — retained: the picker stays mounted through the save; success closes via the host,
 * failure renders inline with Retry on the same selection. The 201-no-body ensure is
 * idempotent server-side, so a raced card is harmless.
 */
@Composable
internal fun EnsureCardDialog(
    productViewModel: ProductViewModel,
    cards: List<BranchInventoryResponse>,
    result: UiState<*>,
    onDismiss: () -> Unit,
    onSubmit: (productId: String) -> Unit,
) {
    val products by productViewModel.products.collectAsState()
    LaunchedEffect(Unit) { productViewModel.loadProducts() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val isBusy = result is UiState.Loading

    fun submit() {
        selectedId?.let(onSubmit)
    }

    OperationalDialog(
        title = "Add inventory card",
        onDismiss = onDismiss,
        confirmLabel = "Add",
        onConfirm = ::submit,
        isBusy = isBusy,
        allowCancelWhenBusy = false,
        confirmEnabled = selectedId != null && !isBusy,
        content = {
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
            // #676 — Retry without a selection is a no-op: keep the error text for
            // correction but offer Retry only once a product is picked.
            RetainedWriteError(result, onRetry = ::submit, showRetry = selectedId != null)
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
