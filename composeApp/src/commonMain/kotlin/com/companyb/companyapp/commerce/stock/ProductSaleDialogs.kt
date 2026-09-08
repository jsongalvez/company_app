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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.client.ClientSearchApi
import com.companyb.companyapp.client.ClientState
import com.companyb.companyapp.client.clientPrimaryName
import com.companyb.companyapp.client.clientSecondaryLine
import com.companyb.companyapp.client.movePickerFocus
import com.companyb.companyapp.contracts.client.ClientResponse
import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
import com.companyb.companyapp.contracts.commerce.ProductSaleResponse
import com.companyb.companyapp.contracts.session.DashboardSessionResponse
import com.companyb.companyapp.ui.contract.InlineStatus
import com.companyb.companyapp.ui.contract.InlineStatusKind
import com.companyb.companyapp.ui.contract.OperationalDialog
import com.companyb.companyapp.ui.contract.operationalField
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Buyer mode for a walk-in sale: find-client (the normal domain case, #676 default) or
 * buyer-not-identified (the explicit secondary exception — no made-up client details).
 */
private enum class SaleBuyerMode { BUYER_NOT_IDENTIFIED, FIND_CLIENT }

@Composable
private fun SaleQuantityReasonFields(
    quantityText: String,
    quantityError: String?,
    onQuantityChange: (String) -> Unit,
    editReason: String,
    onEditReasonChange: (String) -> Unit,
    availableStock: Int?,
    card: BranchInventoryResponse,
    quantityFocus: FocusRequester? = null,
) {
    UnitsField("Quantity", quantityText, quantityError, onQuantityChange, quantityFocus)
    Spacer(Modifier.size(Spacing.sm))
    SalePriceLines(card, quantityText)
    Spacer(Modifier.size(Spacing.sm))
    OptionalReasonField(editReason, onEditReasonChange)
    if (availableStock != null) {
        Spacer(Modifier.size(Spacing.xs))
        Text(
            text = "In stock: $availableStock",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** #676 — product name and quantity lead; unit/total clear, commission secondary. */
@Composable
private fun SalePriceLines(
    card: BranchInventoryResponse,
    quantityText: String,
) {
    val quantity = quantityText.trim().toIntOrNull()?.takeIf { it >= 1 }
    val display = salePriceDisplay(card, quantity ?: 1)
    Column {
        Text(
            text = display.unitLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (quantity != null) display.totalLine else "Total — enter quantity",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        display.commissionLine?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * #419 — the out-of-session sale dialog (the #392 dialog shape: client-side validation mirrors
 * the backend's 400s so an impossible sale never leaves the dialog).
 * #676 — retained lossless shape: Find client is the default with quantity 1 (known-client
 * sales are the normal domain case); "Buyer not identified" is the explicit secondary
 * exception. The form stays mounted through submission, Save keeps bounds with progress,
 * close happens only on confirmed success, and failures render inline with Retry on the
 * same operationId. Save hands `(quantity, clientId?, reason, operationId)`; the walk-in
 * flag is implied by this dialog's shape.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun WalkInSaleDialog(
    card: BranchInventoryResponse,
    clientSearch: ClientSearchApi,
    saleResult: UiState<ProductSaleResponse>,
    onDismiss: () -> Unit,
    onSubmit: (
        quantity: Int,
        clientId: String?,
        editReason: String?,
        operationId: String,
    ) -> Unit,
) {
    // #676 — text drafts survive rotation (saveable, keyed by card); the operationId is
    // stable per open for same-ID Retry. The picked client is entry-local: anonymize-away
    // selections clear below so a dead clientId never submits.
    var quantityText by rememberSaveable(card.id) { mutableStateOf("1") }
    var quantityError by remember(card.id) { mutableStateOf<String?>(null) }
    var editReason by rememberSaveable(card.id) { mutableStateOf("") }
    var buyerMode by remember(card.id) { mutableStateOf(SaleBuyerMode.FIND_CLIENT) }
    var selectedClient by remember(card.id) { mutableStateOf<ClientResponse?>(null) }
    val operationId = remember(card.id) { Uuid.random().toString() }
    val quantityFocus = remember { FocusRequester() }
    val isBusy = saleResult is UiState.Loading
    val cachedClients by clientSearch.freshestResults.collectAsState()
    // #676 — a picked client anonymized/removed while the picker is open clears the
    // selection (forward-compatible with the #684 unknown-client 404: pick another).
    LaunchedEffect(cachedClients, selectedClient?.id) {
        val picked = selectedClient
        val cached = cachedClients
        if (picked != null && cached != null && cached.none { it.id == picked.id }) {
            selectedClient = null
        }
    }

    fun submit() {
        val error = saleQuantityError(quantityText, card.currentStock)
        if (buyerMode == SaleBuyerMode.FIND_CLIENT && selectedClient == null) {
            quantityError = error
            return
        }
        if (error == null) {
            quantityError = null
            val quantity = quantityText.trim().toInt()
            val clientId = if (buyerMode == SaleBuyerMode.FIND_CLIENT) selectedClient?.id else null
            onSubmit(quantity, clientId, editReason, operationId)
        } else {
            quantityError = error
        }
    }

    OperationalDialog(
        title = "Sell — ${card.productName}",
        onDismiss = onDismiss,
        confirmLabel = "Record sale",
        onConfirm = ::submit,
        isBusy = isBusy,
        allowCancelWhenBusy = false,
        confirmEnabled = !isBusy && (buyerMode == SaleBuyerMode.BUYER_NOT_IDENTIFIED || selectedClient != null),
        contentFocus = quantityFocus,
        content = {
            Column {
                SaleQuantityReasonFields(
                    quantityText,
                    quantityError,
                    { quantityText = it },
                    editReason,
                    { editReason = it },
                    card.currentStock,
                    card,
                    quantityFocus,
                )
                Spacer(Modifier.size(Spacing.sm))
                Row {
                    FilterChip(
                        selected = buyerMode == SaleBuyerMode.FIND_CLIENT,
                        onClick = { buyerMode = SaleBuyerMode.FIND_CLIENT },
                        label = { Text("Find client") },
                    )
                    Spacer(Modifier.size(Spacing.xs))
                    FilterChip(
                        selected = buyerMode == SaleBuyerMode.BUYER_NOT_IDENTIFIED,
                        onClick = {
                            buyerMode = SaleBuyerMode.BUYER_NOT_IDENTIFIED
                            selectedClient = null
                        },
                        label = { Text("Buyer not identified") },
                    )
                }
                if (buyerMode == SaleBuyerMode.FIND_CLIENT) {
                    Spacer(Modifier.size(Spacing.xs))
                    SaleClientPicker(
                        clientSearch = clientSearch,
                        selectedClient = selectedClient,
                        onSelect = { selectedClient = it },
                        // #676 — the dialog owns initial focus (quantity first field);
                        // the picker stays keyboard-reachable without stealing it.
                        autoFocusSearch = false,
                    )
                    if (selectedClient == null) {
                        Text(
                            text = "Select a client to continue, or choose “Buyer not identified”.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val saleError = saleResult as? UiState.Error
                if (saleError != null) {
                    val hint = writeErrorHint(saleError.message)
                    val message =
                        if (isAmbiguousWriteError(saleError.message)) {
                            AMBIGUOUS_WRITE_MESSAGE
                        } else if (hint != null) {
                            "${saleError.message} $hint"
                        } else {
                            saleError.message
                        }
                    InlineStatus(message = message, kind = InlineStatusKind.FAILURE, onRetry = ::submit)
                }
            }
        },
    )
}

/** The linked-client search: query field + single-select result list (the EnsureCardPicker shape). */
@Composable
private fun SaleClientPicker(
    clientSearch: ClientSearchApi,
    selectedClient: ClientResponse?,
    onSelect: (ClientResponse) -> Unit,
    // #676 — the walk-in dialog owns initial focus (quantity first); the picker only
    // auto-focuses where no earlier field claims it.
    autoFocusSearch: Boolean = true,
) {
    val query by clientSearch.query.collectAsState()
    val resultsState by clientSearch.searchResults.collectAsState()
    val cached by clientSearch.freshestResults.collectAsState()

    // #610 — privacy reconciliation: anonymize/update mutations published while the picker is
    // open refresh this entry's searcher (the Clients/SessionCreate collect shape).
    LaunchedEffect(Unit) {
        ClientState.clientMutation.collect { mutation ->
            mutation?.let(clientSearch::applyClientMutation)
        }
    }

    // #673 — search focuses on entry where no earlier field claims it; stale rows stay
    // visible but are not selectable.
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(autoFocusSearch) {
        if (autoFocusSearch) searchFocus.requestFocus()
    }
    OutlinedTextField(
        value = query,
        onValueChange = { clientSearch.onQueryChange(it) },
        label = { Text("Find client (name or phone)") },
        singleLine = true,
        modifier = Modifier.operationalField().focusRequester(searchFocus),
    )
    val isLoading = resultsState is UiState.Loading
    if (isLoading && cached.isNullOrEmpty()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(Modifier.heightIn(max = 24.dp))
        }
        return
    }
    when (val state = resultsState) {
        is UiState.Error -> {
            if (cached.isNullOrEmpty()) {
                TextButton(onClick = { clientSearch.retrySearch() }) { Text("Retry search") }
            } else {
                SaleClientIdentityList(
                    results = cached.orEmpty(),
                    selectedClient = selectedClient,
                    enabled = false,
                    onSelect = onSelect,
                )
            }
        }

        is UiState.Success -> {
            if (state.data.isEmpty()) {
                Text(
                    text = "No clients found",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SaleClientIdentityList(
                    results = cached ?: state.data,
                    selectedClient = selectedClient,
                    enabled = !isLoading,
                    onSelect = onSelect,
                )
            }
        }

        is UiState.Loading -> {
            // Keep-last: stale list stays visible with the spinner as the only busy signal.
            SaleClientIdentityList(
                results = cached.orEmpty(),
                selectedClient = selectedClient,
                enabled = false,
                onSelect = onSelect,
            )
        }

        is UiState.Idle -> {
            Text(
                text = "Type at least 2 characters to search",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SaleClientIdentityList(
    results: List<ClientResponse>,
    selectedClient: ClientResponse?,
    enabled: Boolean,
    onSelect: (ClientResponse) -> Unit,
) {
    // #673 — no default selection; arrows move focus, Enter selects.
    var focusedIndex by remember(results) { mutableStateOf(-1) }
    Column(
        modifier =
            Modifier
                .heightIn(max = 200.dp)
                .verticalScroll(rememberScrollState())
                .onPreviewKeyEvent { event ->
                    when (event.key) {
                        Key.DirectionDown -> {
                            focusedIndex = movePickerFocus(focusedIndex, 1, results.size)
                            true
                        }

                        Key.DirectionUp -> {
                            focusedIndex = movePickerFocus(focusedIndex, -1, results.size)
                            true
                        }

                        Key.Enter, Key.NumPadEnter -> {
                            val target = results.getOrNull(focusedIndex)
                            if (enabled && target != null) {
                                onSelect(target)
                                true
                            } else {
                                false
                            }
                        }

                        else -> {
                            false
                        }
                    }
                },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        results.forEachIndexed { index, client ->
            FilterChip(
                selected = selectedClient?.id == client.id,
                enabled = enabled,
                onClick = {
                    focusedIndex = index
                    onSelect(client)
                },
                label = {
                    Text(
                        // #673 — one identity presentation: primary + secondary, never
                        // clinical concerns or raw IDs; missing reads as an em dash.
                        text =
                            clientPrimaryName(client) +
                                " · " + clientSecondaryLine(client, results),
                    )
                },
            )
        }
    }
}

/**
 * #419 — the in-session sale dialog (the #392 dialog shape): session-linked structurally — no
 * buyer picker, `isWalkIn=false` — with the product picked from the branch's inventory cards so
 * stock and version ride along. The session binds its client; anonymous switching is
 * structurally impossible here.
 * #676 — retained lossless shape with quantity 1 default, price lines, and same-ID Retry.
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
internal fun SessionSaleDialog(
    inventoryState: UiState<List<BranchInventoryResponse>>,
    branchId: String,
    sessionId: String,
    inventoryViewModel: InventoryViewModel,
    saleResult: UiState<ProductSaleResponse>,
    onDismiss: () -> Unit,
    onSubmit: (
        card: BranchInventoryResponse,
        quantity: Int,
        editReason: String?,
        operationId: String,
    ) -> Unit,
) {
    // #676 — drafts are keyed by session (never leak across sessions); the idempotency key
    // is per selected product (switching products after a failure starts a fresh operation
    // instead of retrying product B under product A's key); text survives rotation.
    var quantityText by rememberSaveable(branchId, sessionId) { mutableStateOf("1") }
    var quantityError by remember(branchId, sessionId) { mutableStateOf<String?>(null) }
    var editReason by rememberSaveable(branchId, sessionId) { mutableStateOf("") }
    var selectedCard by remember(branchId, sessionId) { mutableStateOf<BranchInventoryResponse?>(null) }
    val operationId = remember(branchId, sessionId, selectedCard?.id) { Uuid.random().toString() }
    val quantityFocus = remember { FocusRequester() }
    val isBusy = saleResult is UiState.Loading

    // On-demand leg: the pane loads the cards only when this dialog opens (the movements
    // history shape); the Retry affordance refires the same leg.
    LaunchedEffect(branchId) { inventoryViewModel.loadInventory(branchId) }

    fun submit() {
        val card = selectedCard ?: return
        val error = saleQuantityError(quantityText, card.currentStock)
        if (error == null) {
            quantityError = null
            onSubmit(card, quantityText.trim().toInt(), editReason, operationId)
        } else {
            quantityError = error
        }
    }

    OperationalDialog(
        title = "Record product sale",
        onDismiss = onDismiss,
        confirmLabel = "Record sale",
        onConfirm = ::submit,
        isBusy = isBusy,
        allowCancelWhenBusy = false,
        confirmEnabled = selectedCard != null && !isBusy,
        contentFocus = quantityFocus,
        content = {
            SessionSaleDialogContent(inventoryState, branchId, inventoryViewModel, selectedCard) {
                selectedCard = it
            }
            if (selectedCard != null) {
                Spacer(Modifier.size(Spacing.sm))
                SaleQuantityReasonFields(
                    quantityText,
                    quantityError,
                    { quantityText = it },
                    editReason,
                    { editReason = it },
                    selectedCard?.currentStock,
                    selectedCard!!,
                    quantityFocus,
                )
            }
            val saleError = saleResult as? UiState.Error
            if (saleError != null) {
                val hint = writeErrorHint(saleError.message)
                val message =
                    if (isAmbiguousWriteError(saleError.message)) {
                        AMBIGUOUS_WRITE_MESSAGE
                    } else if (hint != null) {
                        "${saleError.message} $hint"
                    } else {
                        saleError.message
                    }
                InlineStatus(message = message, kind = InlineStatusKind.FAILURE, onRetry = ::submit)
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
 * loading nothing until then (the movements-history on-demand shape).
 * #676 — retained: submit keeps the dialog mounted; close happens only on confirmed
 * success with the same operationId reused for Retry (never a fresh identifier).
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
    val saleResult by sale.productSaleViewModel.saleResult.collectAsState()
    LaunchedEffect(saleResult) {
        if (saleResult is UiState.Success) onClose()
    }
    SessionSaleDialog(
        inventoryState = inventory,
        branchId = session.branchId,
        sessionId = session.id,
        inventoryViewModel = sale.inventoryViewModel,
        saleResult = saleResult,
        onDismiss = {
            if (saleResult !is UiState.Loading) onClose()
        },
        onSubmit = { card, quantity, editReason, operationId ->
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
                            operationId = operationId,
                        ),
                    ),
                )
            }
        },
    )
}
