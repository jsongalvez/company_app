package com.companyb.companyapp.commerce.stock

import androidx.compose.foundation.layout.Arrangement
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
import com.companyb.companyapp.client.ClientViewModel
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.dto.ProductSaleResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn

/**
 * Buyer mode for a walk-in sale: anonymous (no record) or linked to a searched client.
 */
private enum class SaleBuyerMode { ANONYMOUS, LINKED }

/** Text-field state for the sale dialogs (the MovementForm shape). */
private class SaleForm {
    var quantityText by mutableStateOf("")
    var quantityError by mutableStateOf<String?>(null)
    var editReason by mutableStateOf("")
}

@Composable
private fun SaleQuantityReasonFields(
    form: SaleForm,
    availableStock: Int?,
) {
    UnitsField("Quantity", form.quantityText, form.quantityError) { form.quantityText = it }
    Spacer(Modifier.size(Spacing.sm))
    OptionalReasonField(form.editReason, { form.editReason = it })
    if (availableStock != null) {
        Spacer(Modifier.size(Spacing.xs))
        Text(
            text = "In stock: $availableStock",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * #419 — the out-of-session sale dialog (the #392 dialog shape: client-side validation mirrors
 * the backend's 400s so an impossible sale never leaves the dialog; Save hands parsed values to
 * the caller and closes immediately — the screen refreshes on success and a failure surfaces in
 * its banner). Quantity, buyer chips (Anonymous default — BR marks linked as the rule, anonymous
 * rare), an optional reason. Save hands `(quantity, clientId?, reason)`; the walk-in flag is
 * implied by this dialog's shape.
 */
@Composable
internal fun WalkInSaleDialog(
    card: BranchInventoryResponse,
    clientViewModel: ClientViewModel,
    onDismiss: () -> Unit,
    onSave: (
        quantity: Int,
        clientId: String?,
        editReason: String?,
    ) -> Unit,
) {
    val form = remember { SaleForm() }
    var buyerMode by remember { mutableStateOf(SaleBuyerMode.ANONYMOUS) }
    var selectedClient by remember { mutableStateOf<ClientResponse?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sell — ${card.productName}") },
        text = {
            Column {
                SaleQuantityReasonFields(form, card.currentStock)
                Spacer(Modifier.size(Spacing.sm))
                Row {
                    FilterChip(
                        selected = buyerMode == SaleBuyerMode.ANONYMOUS,
                        onClick = {
                            buyerMode = SaleBuyerMode.ANONYMOUS
                            selectedClient = null
                        },
                        label = { Text("Anonymous") },
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    FilterChip(
                        selected = buyerMode == SaleBuyerMode.LINKED,
                        onClick = { buyerMode = SaleBuyerMode.LINKED },
                        label = { Text("Linked client") },
                    )
                }
                if (buyerMode == SaleBuyerMode.LINKED) {
                    Spacer(Modifier.size(Spacing.xs))
                    SaleClientPicker(clientViewModel, selectedClient, onSelect = { selectedClient = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = buyerMode == SaleBuyerMode.ANONYMOUS || selectedClient != null,
                onClick = {
                    val error = saleQuantityError(form.quantityText, card.currentStock)
                    if (error == null) {
                        val quantity = form.quantityText.trim().toInt()
                        onSave(quantity, selectedClient?.id, form.editReason)
                    } else {
                        form.quantityError = error
                    }
                },
            ) {
                Text("Record sale")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** The linked-client search: query field + single-select result list (the EnsureCardPicker shape). */
@Composable
private fun SaleClientPicker(
    clientViewModel: ClientViewModel,
    selectedClient: ClientResponse?,
    onSelect: (ClientResponse) -> Unit,
) {
    val query by clientViewModel.query.collectAsState()
    val results by clientViewModel.searchResults.collectAsState()

    OutlinedTextField(
        value = query,
        onValueChange = clientViewModel::onQueryChange,
        label = { Text("Find client (name or phone)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    when (val state = results) {
        is UiState.Loading -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(Modifier.heightIn(max = 24.dp))
            }
        }

        is UiState.Error -> {
            TextButton(onClick = clientViewModel::retrySearch) { Text("Retry search") }
        }

        is UiState.Success -> {
            if (state.data.isEmpty()) {
                Text(
                    text = "No clients found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier =
                        Modifier
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    state.data.forEach { client ->
                        FilterChip(
                            selected = selectedClient?.id == client.id,
                            onClick = { onSelect(client) },
                            label = { Text(listOfNotNull(client.firstName, client.lastName).joinToString(" ")) },
                        )
                    }
                }
            }
        }

        is UiState.Idle -> {
            Unit
        }
    }
}

/**
 * #419 — the in-session sale dialog (the #392 dialog shape): session-linked structurally — no
 * buyer picker, `isWalkIn=false` — with the product picked from the branch's inventory cards so
 * stock and version ride along.
 */
@Composable
internal fun SessionSaleDialog(
    inventoryState: UiState<List<BranchInventoryResponse>>,
    branchId: String,
    inventoryViewModel: InventoryViewModel,
    onDismiss: () -> Unit,
    onSave: (
        card: BranchInventoryResponse,
        quantity: Int,
        editReason: String?,
    ) -> Unit,
) {
    val form = remember { SaleForm() }
    var selectedCard by remember { mutableStateOf<BranchInventoryResponse?>(null) }

    // On-demand leg: the pane loads the cards only when this dialog opens (the movements
    // history shape); the Retry affordance refires the same leg.
    LaunchedEffect(branchId) { inventoryViewModel.loadInventory(branchId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record product sale") },
        text = {
            SessionSaleDialogContent(inventoryState, branchId, inventoryViewModel, selectedCard) {
                selectedCard = it
            }
            if (selectedCard != null) {
                Spacer(Modifier.size(Spacing.sm))
                SaleQuantityReasonFields(form, selectedCard?.currentStock)
            }
        },
        confirmButton = {
            TextButton(
                enabled = selectedCard != null,
                onClick = {
                    val card = selectedCard
                    if (card == null) return@TextButton
                    val error = saleQuantityError(form.quantityText, card.currentStock)
                    if (error == null) {
                        onSave(card, form.quantityText.trim().toInt(), form.editReason)
                    } else {
                        form.quantityError = error
                    }
                },
            ) {
                Text("Record sale")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/** The dialog body: the branch's inventory state with the product picker (extracted verbatim). */
@Composable
private fun SessionSaleDialogContent(
    inventoryState: UiState<List<BranchInventoryResponse>>,
    branchId: String,
    inventoryViewModel: InventoryViewModel,
    selectedCard: BranchInventoryResponse?,
    onSelectCard: (BranchInventoryResponse) -> Unit,
) {
    Column {
        when (val state = inventoryState) {
            is UiState.Idle,
            is UiState.Loading,
            -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(Modifier.heightIn(max = 24.dp))
                }
            }

            is UiState.Error -> {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = { inventoryViewModel.loadInventory(branchId) }) { Text("Retry") }
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    Text(
                        text = "No inventory cards at this branch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    SaleCardPicker(
                        cards = state.data,
                        selectedCard = selectedCard,
                        onSelect = onSelectCard,
                    )
                }
            }
        }
    }
}

/**
 * #419 — the one-shot product-sale drain (the #382 shape): any terminal outcome reloads
 * authoritatively, then clears the sticky flow so it cannot replay into a re-entered pane.
 */
@Composable
internal fun SaleEffects(
    productSaleVm: ProductSaleViewModel,
    saleResult: UiState<ProductSaleResponse>,
    refreshSession: () -> Unit,
) {
    LaunchedEffect(saleResult) {
        when (val result = saleResult) {
            is UiState.Success -> {
                logInfo("SessionDetailVM", "product sale landed — refreshing session")
                refreshSession()
            }

            is UiState.Error -> {
                logWarn("SessionDetailVM", "product sale failed: ${result.message}")
                refreshSession()
            }

            else -> {
                return@LaunchedEffect
            }
        }
        productSaleVm.clearSaleResult()
    }
}

/** The product picker: one chip per carded product, newest stock line in the label. */
@Composable
private fun SaleCardPicker(
    cards: List<BranchInventoryResponse>,
    selectedCard: BranchInventoryResponse?,
    onSelect: (BranchInventoryResponse) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.heightIn(max = 240.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(cards.sortedBy { it.productName.lowercase() }, key = { it.id }) { card ->
            FilterChip(
                selected = selectedCard?.id == card.id,
                onClick = { onSelect(card) },
                label = { Text("${card.productName} · ${card.currentStock} in stock") },
            )
        }
    }
}

/**
 * The pane sale flow's wiring (#419): the request's clocked-in day plus the side-loaded
 * inventory/product-sale pair, bundled so the host stays low-arity (the #412 bundling shape).
 */
internal class PaneSaleContext(
    val branchDayId: String?,
    val inventoryViewModel: InventoryViewModel,
    val productSaleViewModel: ProductSaleViewModel,
)

/**
 * The pane-side host (#419): renders [SessionSaleDialog] while the pane's target slot is open,
 * loading nothing until then (the movements-history on-demand shape), and submits the
 * session-linked request through the shared VM before clearing the slot.
 */
@Composable
internal fun PaneSaleDialogHost(
    visible: Boolean,
    session: DashboardSessionResponse,
    sale: PaneSaleContext,
    onClose: () -> Unit,
) {
    if (!visible) return
    val inventory by sale.inventoryViewModel.inventory.collectAsState()
    SessionSaleDialog(
        inventoryState = inventory,
        branchId = session.branchId,
        inventoryViewModel = sale.inventoryViewModel,
        onDismiss = onClose,
        onSave = { card, quantity, editReason ->
            onClose()
            if (sale.branchDayId != null) {
                sale.productSaleViewModel.sell(
                    buildSaleRequest(
                        SaleDraft(
                            card = card,
                            quantity = quantity,
                            clientId = null,
                            sessionId = session.id,
                            isWalkIn = false,
                            reason = editReason,
                            branchDayId = sale.branchDayId,
                        ),
                    ),
                )
            }
        },
    )
}
