package com.companyb.companyapp.commerce.stock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.client.ClientSearchApi
import com.companyb.companyapp.commerce.catalog.ProductViewModel
import com.companyb.companyapp.contracts.commerce.AddInventoryCardRequest
import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.contract.InlineStatus
import com.companyb.companyapp.ui.contract.InlineStatusKind
import com.companyb.companyapp.ui.contract.OperationalUiContract
import com.companyb.companyapp.ui.contract.SecondaryActionButton
import com.companyb.companyapp.ui.contract.TertiaryActionButton
import com.companyb.companyapp.ui.contract.operationalField
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing

/** Which write flow a row tap opens (#392, #419 sale). */
internal enum class InventoryWriteKind { RESTOCK, MOVEMENT, SELL }

/** One open write dialog (#392); null = none. */
internal sealed interface InventoryWriteTarget {
    data class Restock(
        val card: BranchInventoryResponse,
    ) : InventoryWriteTarget

    data class Movement(
        val card: BranchInventoryResponse,
    ) : InventoryWriteTarget

    /** #419 — the out-of-session (walk-in) sale from this row. */
    data class Sell(
        val card: BranchInventoryResponse,
    ) : InventoryWriteTarget
}

/**
 * The four inventory write terminals (#392 restock/movement, #395 ensure-card, #419 sale),
 * bundled so every consumer takes one value (the #412 [PaneResults] shape).
 */
internal data class InventoryWriteResults(
    val restockResult: UiState<*>,
    val movementResult: UiState<*>,
    val cardResult: UiState<*>,
    val saleResult: UiState<*>,
)

/**
 * The screen's single open overlay (#392 write dialogs, #395 ensure-card, #397 movements
 * history); null = none. Mutually exclusive by construction — one state replaces the three
 * independent booleans the sections used to pass around.
 */
internal sealed interface InventoryOverlay {
    data class Write(
        val target: InventoryWriteTarget,
    ) : InventoryOverlay

    data object EnsureCard : InventoryOverlay

    data object MovementsHistory : InventoryOverlay
}

/** Handles the extracted sections share (the header, overlay hosts, and write dialogs). */
internal class InventorySectionContext(
    val viewModel: InventoryViewModel,
    val productViewModel: ProductViewModel,
    // #419 — the sale pair rides the shared section context so the overlay/dialog hosts stay
    // low-arity. #610 — the buyer search is the narrow client-owned [ClientSearchApi], never
    // the full edit/anonymize surface.
    val productSaleViewModel: ProductSaleViewModel,
    val clientSearch: ClientSearchApi,
    val branchId: String?,
)

/**
 * #391 — branch Inventory list (replacing the dead "pending build ticket" stub).
 * Branch-scoped to the clocked-in branch (`selectedBranchId`, the Remittance shape); the route
 * gate lives at the NavHost call sites (any-context EDIT_BRANCH_DATA — mirrors the #108 drawer
 * item), with the backend's branch-scoped read gate authoritative.
 *
 * #392 — write half: per-row Restock (MANAGE_PRODUCTS at the branch) and movement recording
 * (Tester/Sample/Missing on EDIT_BRANCH_DATA, Adjustment on MANAGE_PRODUCTS — the exact-scope
 * predicates mirror the backend's #157 branch-scoped route filters). Affordances hide
 * fail-closed without a clocked-in branchDayId.
 *
 * #395 — ensure-card half: an "Add product to branch" header affordance (MANAGE_PRODUCTS at
 * the branch, [canEnsureCard] mirroring the POST /inventory route filter — no day-state leg)
 * opens the product picker.
 *
 * #396 — low-stock surfacing: the auxiliary `GET .../inventory/low-stock` read rides every
 * load (separate UiState — its failure degrades to an inline retry strip); rows whose product
 * id the backend marks low render a "Low" marker and a count summary line sits under the
 * header. Membership is backend-authoritative; the client mirrors nothing.
 *
 * #397 — movements-history browsing: a "History" header affordance opens the branch's past
 * stock movements in a dialog, loaded on demand through its own VM leg (failure degrades to
 * inline retry there, never touching the list legs).
 *
 * #419 — sale half: a per-row "Sell" affordance (the exact branch-or-day EDIT_BRANCH_DATA
 * mirror of `POST /api/product-sales`, fail-closed without a clocked-in branchDayId) opens the
 * walk-in sale dialog.
 *
 * #676 — lossless, spatially stable, client-first: every write dialog stays mounted through
 * submission (close only on confirmed success, same operationId for Retry, inline errors
 * with correction); the walk-in sale starts in Find-client mode with quantity 1; rows lead
 * with product name + available quantity (primary) + unit charge + visible Sell, with the
 * ledger breakdown behind a Details disclosure and Restock/Record movement in the row's
 * More menu; the header carries search + Low stock filter + History + Add product to branch;
 * populated lists stay mounted across refreshes with Updating/failure bands (write success
 * vs refresh failure reads distinctly); search/low-stock/empty states read distinctly;
 * compact widths stack identity-first instead of horizontal scrolling.
 *
 * States: load-on-entry + Refresh button (the Remittance D7 axis); cold Loading spinner;
 * cold error → shared ErrorCard retry; retained list + Updating/failure bands on refresh;
 * empty hint; rows sorted by product name (toInventoryRows pins the presentation rules,
 * desktopTest-packeted).
 */
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    productViewModel: ProductViewModel,
    productSaleViewModel: ProductSaleViewModel,
    clientSearch: ClientSearchApi,
    branchId: String?,
) {
    val inventoryState by viewModel.inventory.collectAsState()
    val lowStockState by viewModel.lowStock.collectAsState()
    val restockResult by viewModel.restockResult.collectAsState()
    val movementResult by viewModel.movementResult.collectAsState()
    val cardResult by viewModel.cardResult.collectAsState()
    val saleResult by productSaleViewModel.saleResult.collectAsState()
    val snapshot by AppSessionState.snapshot.collectAsState()
    val capabilities = snapshot.capabilities
    val branchDayId = snapshot.clock?.branchDayId
    // #676 — branch-keyed working context (#671 rekey rule): an open dialog, the retained
    // list, the write label, and the scroll anchor never surface another branch's data.
    var overlay by remember(branchId) { mutableStateOf<InventoryOverlay?>(null) }
    val context =
        InventorySectionContext(viewModel, productViewModel, productSaleViewModel, clientSearch, branchId)
    val writeResults = InventoryWriteResults(restockResult, movementResult, cardResult, saleResult)
    val writesDisabled = writesDisabled(restockResult, movementResult, saleResult)
    // #676 — header query/filter survive back + refresh (screen-held, keyed by branch so a
    // branch switch never surfaces another branch's query).
    var query by rememberSaveable(branchId) { mutableStateOf("") }
    var lowStockOnly by rememberSaveable(branchId) { mutableStateOf(false) }
    // #676 — retained populated list: follow-up reloads never replace usable content.
    // Branch-keyed with the overlay above so a branch switch never flashes old rows.
    var lastCards by remember(branchId) { mutableStateOf<List<BranchInventoryResponse>>(emptyList()) }
    LaunchedEffect(inventoryState) {
        if (inventoryState is UiState.Success) {
            // SAFETY: is-check above; delegated State value doesn't smart-cast #467
            lastCards = (inventoryState as UiState.Success<List<BranchInventoryResponse>>).data
        }
    }
    // #676 — write-success vs refresh-failure distinction: the label of the last landed
    // write survives until the next successful list load, so a refresh failure after a
    // confirmed write reads "Sale recorded; quantities could not refresh — Retry."
    var lastWriteLabel by remember(branchId) { mutableStateOf<String?>(null) }
    LaunchedEffect(restockResult) { if (restockResult is UiState.Success) lastWriteLabel = "Restocked" }
    LaunchedEffect(movementResult) { if (movementResult is UiState.Success) lastWriteLabel = "Movement recorded" }
    LaunchedEffect(cardResult) { if (cardResult is UiState.Success) lastWriteLabel = "Card added" }
    LaunchedEffect(saleResult) { if (saleResult is UiState.Success) lastWriteLabel = "Sale recorded" }
    LaunchedEffect(inventoryState) { if (inventoryState is UiState.Success) lastWriteLabel = null }
    // #676 — the list anchor survives dialog roundtrips and refreshes; branch-keyed so a
    // switch resets instead of jumping to a meaningless offset.
    val listState = remember(branchId) { LazyListState() }

    InventoryLoadEffects(viewModel, branchId, writeResults)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        InventoryHeader(
            context = context,
            inventoryState = inventoryState,
            cardResult = writeResults.cardResult,
            query = query,
            onQueryChange = { query = it },
            lowStockOnly = lowStockOnly,
            onLowStockToggle = { lowStockOnly = !lowStockOnly },
            onOverlay = { overlay = it },
        )
        LowStockStrip(
            inventoryState = inventoryState,
            lowStockState = lowStockState,
            branchId = branchId,
            onRetryLowStock = { if (branchId != null) viewModel.loadLowStock(branchId) },
        )
        RefreshStatusBand(
            inventoryState = inventoryState,
            hasRetainedCards = lastCards.isNotEmpty(),
            lastWriteLabel = lastWriteLabel,
            onRetry = { if (branchId != null) viewModel.refresh(branchId) },
            onDismissWriteLabel = { lastWriteLabel = null },
        )
        InventoryBody(
            state = inventoryState,
            retainedCards = lastCards,
            lowStockIds = (lowStockState as? UiState.Success)?.data.orEmpty().mapTo(mutableSetOf()) { it.productId },
            lowStockKnown = lowStockState is UiState.Success,
            query = query,
            lowStockOnly = lowStockOnly,
            onClearFilters = {
                query = ""
                lowStockOnly = false
            },
            rowActions = inventoryRowActions(capabilities, branchId, branchDayId, writesDisabled),
            listState = listState,
            onRetry = { if (branchId != null) viewModel.refresh(branchId) },
            // #676 — every open starts from Idle: stale errors/successes never resurface on
            // a fresh form, and the next Sell can never auto-close on a sticky Success.
            onAction = { card, kind ->
                viewModel.clearWriteResults()
                productSaleViewModel.clearSaleResult()
                overlay = writeOverlayFor(card, kind)
            },
        )
    }
    InventoryOverlayHosts(
        overlay = overlay,
        context = context,
        cards = (inventoryState as? UiState.Success)?.data ?: lastCards,
        onClose = { overlay = null },
    )
}

/** The row tap → open-overlay mapping (the #392 write kinds plus the #419 sale). */
private fun writeOverlayFor(
    card: BranchInventoryResponse,
    kind: InventoryWriteKind,
): InventoryOverlay =
    when (kind) {
        InventoryWriteKind.RESTOCK -> InventoryOverlay.Write(InventoryWriteTarget.Restock(card))
        InventoryWriteKind.MOVEMENT -> InventoryOverlay.Write(InventoryWriteTarget.Movement(card))
        InventoryWriteKind.SELL -> InventoryOverlay.Write(InventoryWriteTarget.Sell(card))
    }

/**
 * The screen header (#676): title plus search, Low stock filter, History (#397),
 * Add product to branch (#395), and Refresh affordances.
 */
@Suppress("LongParameterList") // #676 header carries search/filter/history/add/refresh in one row per #535.
@Composable
private fun InventoryHeader(
    context: InventorySectionContext,
    inventoryState: UiState<List<BranchInventoryResponse>>,
    cardResult: UiState<*>,
    query: String,
    onQueryChange: (String) -> Unit,
    lowStockOnly: Boolean,
    onLowStockToggle: () -> Unit,
    onOverlay: (InventoryOverlay) -> Unit,
) {
    val snapshot by AppSessionState.snapshot.collectAsState()
    val capabilities = snapshot.capabilities
    val branchId = context.branchId
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Inventory", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (branchId != null) {
                    TertiaryActionButton(label = "History", onClick = { onOverlay(InventoryOverlay.MovementsHistory) })
                }
                if (branchId != null && canEnsureCard(capabilities, branchId)) {
                    TertiaryActionButton(
                        label = "Add product to branch",
                        onClick = { onOverlay(InventoryOverlay.EnsureCard) },
                        enabled = cardResult !is UiState.Loading,
                    )
                }
                TertiaryActionButton(
                    label = "Refresh",
                    onClick = { if (branchId != null) context.viewModel.refresh(branchId) },
                    enabled = branchId != null && inventoryState !is UiState.Loading,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                label = { Text("Search products") },
                singleLine = true,
                modifier = Modifier.weight(1f).operationalField(),
            )
            FilterChip(
                selected = lowStockOnly,
                onClick = onLowStockToggle,
                label = { Text("Low stock") },
            )
        }
        // The ensure-card leg carries its own header disablement; surface its busy state
        // without moving surrounding chrome.
        if (cardResult is UiState.Loading) {
            InlineStatus(message = "Adding card…", kind = InlineStatusKind.UPDATING)
        }
    }
}

/**
 * #676 — refresh vs write-success distinction band. Cold loads render nothing here (the
 * body owns spinner/error); retained lists get Updating… while a refresh is in flight,
 * and a refresh failure after populated data reads distinctly — with the confirmed-write
 * label first when one is owed ("Sale recorded; quantities could not refresh — Retry."),
 * never a rollback or re-submission prompt.
 */
@Composable
private fun RefreshStatusBand(
    inventoryState: UiState<List<BranchInventoryResponse>>,
    hasRetainedCards: Boolean,
    lastWriteLabel: String?,
    onRetry: () -> Unit,
    onDismissWriteLabel: () -> Unit,
) {
    if (!hasRetainedCards) return
    when (inventoryState) {
        is UiState.Loading -> {
            InlineStatus(message = "Updating…", kind = InlineStatusKind.UPDATING)
        }

        is UiState.Error -> {
            val message =
                if (lastWriteLabel != null) {
                    "$lastWriteLabel; quantities could not refresh"
                } else {
                    "Could not update"
                }
            InlineStatus(message = message, kind = InlineStatusKind.FAILURE, onRetry = onRetry)
            if (lastWriteLabel != null) {
                TertiaryActionButton(label = "Dismiss", onClick = onDismissWriteLabel)
            }
        }

        else -> {
            Unit
        }
    }
}

/** Renders whichever overlay is open (the #394 dialog-hosts shape). */
@Composable
private fun InventoryOverlayHosts(
    overlay: InventoryOverlay?,
    context: InventorySectionContext,
    cards: List<BranchInventoryResponse>,
    onClose: () -> Unit,
) {
    when (overlay) {
        is InventoryOverlay.Write -> {
            InventoryWriteDialogs(
                overlay.target,
                context,
                cards,
                onClose,
            )
        }

        InventoryOverlay.EnsureCard -> {
            val cardResult by context.viewModel.cardResult.collectAsState()
            LaunchedEffect(cardResult) {
                if (cardResult is UiState.Success) onClose()
            }
            EnsureCardDialog(
                productViewModel = context.productViewModel,
                cards = cards,
                result = cardResult,
                onDismiss = {
                    if (cardResult !is UiState.Loading) onClose()
                },
                onSubmit = { productId ->
                    val branchId = context.branchId
                    if (branchId != null) {
                        context.viewModel.ensureCard(branchId, AddInventoryCardRequest(productId))
                    }
                },
            )
        }

        InventoryOverlay.MovementsHistory -> {
            val branchId = context.branchId
            if (branchId != null) {
                MovementsHistoryDialog(
                    viewModel = context.viewModel,
                    branchId = branchId,
                    cards = cards,
                    onDismiss = onClose,
                )
            }
        }

        null -> {
            Unit
        }
    }
}

@Suppress("LongParameterList") // #676 body threads retained list + filter + actions per #535.
@Composable
private fun InventoryBody(
    state: UiState<List<BranchInventoryResponse>>,
    retainedCards: List<BranchInventoryResponse>,
    lowStockIds: Set<String>,
    lowStockKnown: Boolean,
    query: String,
    lowStockOnly: Boolean,
    onClearFilters: () -> Unit,
    rowActions: InventoryRowActions,
    listState: LazyListState,
    onRetry: () -> Unit,
    onAction: (BranchInventoryResponse, InventoryWriteKind) -> Unit,
) {
    when (state) {
        // Defensive: the drawer is only composed post-clock-in, so a null branch here means a
        // direct nav without a clocked-in branch — never issue a request.
        is UiState.Idle -> {
            CenteredInventoryHint("No branch selected")
        }

        is UiState.Loading -> {
            // #676 — retained populated lists stay mounted with the band above; only cold
            // loads take the spinner.
            if (retainedCards.isNotEmpty()) {
                InventorySuccessList(
                    cards = filterInventoryCards(retainedCards, query, lowStockOnly, lowStockIds),
                    lowStockIds = lowStockIds,
                    lowStockKnown = lowStockKnown,
                    query = query,
                    lowStockOnly = lowStockOnly,
                    onClearFilters = onClearFilters,
                    rowActions = rowActions,
                    listState = listState,
                    onAction = onAction,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }

        is UiState.Error -> {
            // #676 — a refresh failure over populated data keeps the list (the band above
            // carries Retry); only a cold failure becomes the error card.
            if (retainedCards.isNotEmpty()) {
                InventorySuccessList(
                    cards = filterInventoryCards(retainedCards, query, lowStockOnly, lowStockIds),
                    lowStockIds = lowStockIds,
                    lowStockKnown = lowStockKnown,
                    query = query,
                    lowStockOnly = lowStockOnly,
                    onClearFilters = onClearFilters,
                    rowActions = rowActions,
                    listState = listState,
                    onAction = onAction,
                )
            } else {
                ErrorCard(state.message, onRetry = onRetry)
            }
        }

        is UiState.Success -> {
            if (state.data.isEmpty()) {
                CenteredInventoryHint("No inventory cards yet")
            } else {
                InventorySuccessList(
                    cards = filterInventoryCards(state.data, query, lowStockOnly, lowStockIds),
                    lowStockIds = lowStockIds,
                    lowStockKnown = lowStockKnown,
                    query = query,
                    lowStockOnly = lowStockOnly,
                    onClearFilters = onClearFilters,
                    rowActions = rowActions,
                    listState = listState,
                    onAction = onAction,
                )
            }
        }
    }
}

@Suppress("LongParameterList") // #676 list threads filter + empty states + actions per #535.
@Composable
private fun InventorySuccessList(
    cards: List<BranchInventoryResponse>,
    lowStockIds: Set<String>,
    lowStockKnown: Boolean,
    query: String,
    lowStockOnly: Boolean,
    onClearFilters: () -> Unit,
    rowActions: InventoryRowActions,
    listState: LazyListState,
    onAction: (BranchInventoryResponse, InventoryWriteKind) -> Unit,
) {
    // #676 — distinct empty states: search-no-match vs low-stock-empty vs no cards.
    if (cards.isEmpty()) {
        val message =
            when {
                query.trim().isNotEmpty() && lowStockOnly -> {
                    "No matches for \"${query.trim()}\" among low-stock products"
                }

                query.trim().isNotEmpty() -> {
                    "No matches for \"${query.trim()}\""
                }

                // #676 — unknown (failed/loading leg) never asserts absence: the strip above
                // carries the Retry while this names the uncertainty.
                lowStockOnly && !lowStockKnown -> {
                    "Low stock unavailable — retry above"
                }

                lowStockOnly -> {
                    "No low-stock products"
                }

                else -> {
                    "No inventory cards yet"
                }
            }
        Column(
            modifier = Modifier.fillMaxSize().padding(top = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CenteredInventoryHint(message)
            if (query.trim().isNotEmpty() || lowStockOnly) {
                TertiaryActionButton(label = "Clear filters", onClick = onClearFilters)
            }
        }
        return
    }
    // #676 — the list anchor survives dialog roundtrips and refreshes (remembered
    // LazyListState); repeated Sell actions return to the same stable position.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = OperationalUiContract.isCompactViewport(maxWidth)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(cards.toInventoryRows(lowStockIds), key = { it.id }) { model ->
                val card = cards.first { it.id == model.id }
                InventoryRow(
                    model = model,
                    card = card,
                    actions = rowActions,
                    isCompact = isCompact,
                    onAction = { kind -> onAction(card, kind) },
                )
            }
        }
    }
}

/**
 * #676 — row hierarchy: product name, available quantity (primary number), unit customer
 * charge, visible Sell action. The ledger breakdown rides a secondary Details disclosure;
 * Restock and Record movement live in the row's labeled More menu. Compact widths stack
 * identity-first instead of horizontally scrolling the ledger.
 */
@Composable
private fun InventoryRow(
    model: InventoryRowModel,
    card: BranchInventoryResponse,
    actions: InventoryRowActions,
    isCompact: Boolean,
    onAction: (InventoryWriteKind) -> Unit,
) {
    var detailsOpen by remember(model.id) { mutableStateOf(false) }
    var moreOpen by remember(model.id) { mutableStateOf(false) }
    // Hairline border instead of shadow per DESIGN.md (the RemittanceDetail row-card idiom).
    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md).fillMaxWidth()) {
            if (isCompact) {
                InventoryRowIdentity(model, card)
                Spacer(Modifier.height(Spacing.sm))
                InventoryRowActionsBar(actions, moreOpen, onMoreChange = { moreOpen = it }, onAction)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        InventoryRowIdentity(model, card)
                    }
                    InventoryRowActionsBar(actions, moreOpen, onMoreChange = { moreOpen = it }, onAction)
                }
            }
            if (detailsOpen) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = model.stockLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TertiaryActionButton(
                label = if (detailsOpen) "Hide details" else "Details",
                onClick = { detailsOpen = !detailsOpen },
            )
        }
    }
}

@Composable
private fun InventoryRowIdentity(
    model: InventoryRowModel,
    card: BranchInventoryResponse,
) {
    Column {
        Text(
            text = model.productName,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.xs))
        // Available quantity is the primary number; unit charge is the secondary value.
        Text(
            text = "Available: ${card.currentStock}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = model.priceLine + " each",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (model.isLow) {
            Text(
                text = "Low",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun InventoryRowActionsBar(
    actions: InventoryRowActions,
    moreOpen: Boolean,
    onMoreChange: (Boolean) -> Unit,
    onAction: (InventoryWriteKind) -> Unit,
) {
    Column(horizontalAlignment = Alignment.End) {
        // #676 — Sell stays visible; Restock/Record movement ride the labeled More menu.
        if (actions.sellEnabled) {
            SecondaryActionButton(
                label = "Sell",
                onClick = { onAction(InventoryWriteKind.SELL) },
            )
        }
        if (actions.restockEnabled || actions.movementEnabled) {
            Box {
                TertiaryActionButton(
                    label = "More",
                    onClick = { onMoreChange(true) },
                )
                DropdownMenu(expanded = moreOpen, onDismissRequest = { onMoreChange(false) }) {
                    if (actions.restockEnabled) {
                        DropdownMenuItem(
                            text = { Text("Restock") },
                            onClick = {
                                onMoreChange(false)
                                onAction(InventoryWriteKind.RESTOCK)
                            },
                        )
                    }
                    if (actions.movementEnabled) {
                        DropdownMenuItem(
                            text = { Text("Record movement") },
                            onClick = {
                                onMoreChange(false)
                                onAction(InventoryWriteKind.MOVEMENT)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryWriteDialogs(
    target: InventoryWriteTarget,
    context: InventorySectionContext,
    cards: List<BranchInventoryResponse>,
    onDone: () -> Unit,
) {
    val snapshot by AppSessionState.snapshot.collectAsState()
    val branchDayId = snapshot.clock?.branchDayId

    // #676 — conflict reconcile without draft loss: the dialog's submit reads the freshest
    // card version/stock for the same id (a background refresh under the modal updates the
    // snapshot), while typed units/reasons/selections stay retained. Retry re-sends the same
    // operationId against fresh versions so a 409 can converge instead of looping.
    fun fresh(
        id: String,
        fallback: BranchInventoryResponse,
    ): BranchInventoryResponse = cards.firstOrNull { it.id == id } ?: fallback
    when (target) {
        is InventoryWriteTarget.Restock -> {
            RestockWriteDialog(fresh(target.card.id, target.card), context, branchDayId, onDone)
        }

        is InventoryWriteTarget.Movement -> {
            MovementWriteDialog(fresh(target.card.id, target.card), context, branchDayId, onDone)
        }

        is InventoryWriteTarget.Sell -> {
            WalkInWriteDialog(fresh(target.card.id, target.card), context, branchDayId, onDone)
        }
    }
}

@Composable
private fun CenteredInventoryHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
