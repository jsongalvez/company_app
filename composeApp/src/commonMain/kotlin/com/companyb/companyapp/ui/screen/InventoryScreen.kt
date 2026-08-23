package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.AddInventoryCardRequest
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.viewmodel.InventoryViewModel
import com.companyb.companyapp.viewmodel.ProductViewModel
import com.companyb.companyapp.viewmodel.UiState

/** Which write flow a row tap opens (#392). */
internal enum class InventoryWriteKind { RESTOCK, MOVEMENT }

/** Per-row affordance enablement: visibility AND mid-flight disablement merged fail-closed. */
internal data class InventoryRowActions(
    val restockEnabled: Boolean,
    val movementEnabled: Boolean,
)

/** One open write dialog (#392); null = none. */
internal sealed interface InventoryWriteTarget {
    data class Restock(
        val card: BranchInventoryResponse,
    ) : InventoryWriteTarget

    data class Movement(
        val card: BranchInventoryResponse,
    ) : InventoryWriteTarget
}

/**
 * #391 — branch Inventory list (replacing the dead "pending build ticket" stub).
 * Branch-scoped to the clocked-in branch (`selectedBranchId`, the Remittance shape); the route
 * gate lives at the NavHost call sites (any-context EDIT_BRANCH_DATA — mirrors the #108 drawer
 * item), with the backend's branch-scoped read gate authoritative.
 *
 * #392 — write half: per-row Restock (MANAGE_PRODUCTS at the branch) and movement recording
 * (Tester/Sample/Missing on EDIT_BRANCH_DATA, Adjustment on MANAGE_PRODUCTS — the exact-scope
 * predicates mirror the backend's #157 branch-scoped route filters). Affordances hide
 * fail-closed without a clocked-in branchDayId. Success closes + refreshes; write failures
 * surface in a dismissable banner above the list.
 *
 * #395 — ensure-card half: an "Add card" header affordance (MANAGE_PRODUCTS at the branch,
 * [canEnsureCard] mirroring the POST /inventory route filter — no day-state leg) opens the
 * product picker; success refreshes, failure joins the same banner.
 *
 * #396 — low-stock surfacing: the auxiliary `GET .../inventory/low-stock` read rides every
 * load (separate UiState — its failure degrades to an inline retry strip); rows whose product
 * id the backend marks low render a "Low" marker and a count summary line sits under the
 * header. Membership is backend-authoritative; the client mirrors nothing.
 *
 * States: load-on-entry + Refresh button (the Remittance D7 axis); Loading spinner;
 * error → shared ErrorCard retry; empty hint; rows sorted by product name (toInventoryRows
 * pins the presentation rules, desktopTest-packeted).
 */
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    productViewModel: ProductViewModel,
    branchId: String?,
) {
    val inventoryState by viewModel.inventory.collectAsState()
    val lowStockState by viewModel.lowStock.collectAsState()
    val restockResult by viewModel.restockResult.collectAsState()
    val movementResult by viewModel.movementResult.collectAsState()
    val cardResult by viewModel.cardResult.collectAsState()
    val capabilities by SessionState.capabilities.collectAsState()
    var writeTarget by remember { mutableStateOf<InventoryWriteTarget?>(null) }
    var showEnsureCard by remember { mutableStateOf(false) }

    InventoryLoadEffects(viewModel, branchId, restockResult, movementResult, cardResult)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Inventory", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (branchId != null && canEnsureCard(capabilities, branchId)) {
                    TextButton(
                        onClick = { showEnsureCard = true },
                        enabled = cardResult !is UiState.Loading,
                    ) {
                        Text("Add card")
                    }
                }
                TextButton(
                    onClick = { if (branchId != null) viewModel.refresh(branchId) },
                    enabled = branchId != null && inventoryState !is UiState.Loading,
                ) {
                    Text("Refresh")
                }
            }
        }
        WriteErrorBanner(restockResult, movementResult, cardResult, viewModel)
        LowStockStrip(
            inventoryState = inventoryState,
            lowStockState = lowStockState,
            branchId = branchId,
            onRetryLowStock = { if (branchId != null) viewModel.loadLowStock(branchId) },
        )
        InventoryBody(
            state = inventoryState,
            lowStockIds = (lowStockState as? UiState.Success)?.data.orEmpty().mapTo(mutableSetOf()) { it.productId },
            branchId = branchId,
            writesDisabled = restockResult is UiState.Loading || movementResult is UiState.Loading,
            onRetry = { if (branchId != null) viewModel.refresh(branchId) },
            onAction = { card, kind ->
                writeTarget =
                    if (kind == InventoryWriteKind.RESTOCK) {
                        InventoryWriteTarget.Restock(card)
                    } else {
                        InventoryWriteTarget.Movement(card)
                    }
            },
        )
    }
    writeTarget?.let { target ->
        InventoryWriteDialogs(target, viewModel, branchId) { writeTarget = null }
    }
    if (showEnsureCard) {
        EnsureCardDialog(
            productViewModel = productViewModel,
            cards = (inventoryState as? UiState.Success)?.data.orEmpty(),
            onDismiss = { showEnsureCard = false },
            onSave = { productId ->
                showEnsureCard = false
                if (branchId != null) {
                    viewModel.ensureCard(branchId, AddInventoryCardRequest(productId))
                }
            },
        )
    }
}

/** Entry load plus the #392 write-success legs (+#395 ensure-card): clear + authoritative refresh. */
@Composable
private fun InventoryLoadEffects(
    viewModel: InventoryViewModel,
    branchId: String?,
    restockResult: UiState<*>,
    movementResult: UiState<*>,
    cardResult: UiState<*>,
) {
    LaunchedEffect(branchId) {
        logInfo("InventoryScreen", "composable entered (branchId=$branchId)")
        if (branchId != null) viewModel.refresh(branchId)
    }
    LaunchedEffect(restockResult) {
        if (restockResult is UiState.Success) {
            logInfo("InventoryScreen", "restock landed — refreshing inventory")
            viewModel.clearWriteResults()
            if (branchId != null) viewModel.refresh(branchId)
        }
    }
    LaunchedEffect(movementResult) {
        if (movementResult is UiState.Success) {
            logInfo("InventoryScreen", "movement landed — refreshing inventory")
            viewModel.clearWriteResults()
            if (branchId != null) viewModel.refresh(branchId)
        }
    }
    LaunchedEffect(cardResult) {
        if (cardResult is UiState.Success) {
            logInfo("InventoryScreen", "ensure-card landed — refreshing inventory")
            viewModel.clearWriteResults()
            if (branchId != null) viewModel.refresh(branchId)
        }
    }
}

/**
 * #396 — the low-stock surface between the header and the list: a count summary line when any
 * displayed card is low, or an inline retry strip when only the auxiliary leg failed (the list
 * leg's data stays up). Renders nothing while the legs are loading/absent.
 */
@Composable
private fun LowStockStrip(
    inventoryState: UiState<List<BranchInventoryResponse>>,
    lowStockState: UiState<List<BranchInventoryResponse>>,
    branchId: String?,
    onRetryLowStock: () -> Unit,
) {
    val cards = (inventoryState as? UiState.Success)?.data.orEmpty()
    when (lowStockState) {
        is UiState.Error -> {
            if (branchId != null && inventoryState !is UiState.Error) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Low stock unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onRetryLowStock) { Text("Retry") }
                }
            }
        }

        is UiState.Success -> {
            val summary =
                lowStockSummaryLine(
                    cards,
                    lowStockState.data.mapTo(mutableSetOf()) { it.productId },
                )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }

        else -> {
            Unit
        }
    }
}

/** Inline surface for write failures (the EditSlotDialog inline-error shape, screen-level). */
@Composable
private fun WriteErrorBanner(
    restockResult: UiState<*>,
    movementResult: UiState<*>,
    cardResult: UiState<*>,
    viewModel: InventoryViewModel,
) {
    val error =
        (restockResult as? UiState.Error)
            ?: (movementResult as? UiState.Error)
            ?: (cardResult as? UiState.Error)
            ?: return
    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = error.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::clearWriteResults) { Text("Dismiss") }
        }
    }
}

@Composable
private fun InventoryBody(
    state: UiState<List<BranchInventoryResponse>>,
    lowStockIds: Set<String>,
    branchId: String?,
    writesDisabled: Boolean,
    onRetry: () -> Unit,
    onAction: (BranchInventoryResponse, InventoryWriteKind) -> Unit,
) {
    val capabilities by SessionState.capabilities.collectAsState()
    val branchDayId by SessionState.branchDayId.collectAsState()
    when (state) {
        // Defensive: the drawer is only composed post-clock-in, so a null branch here means a
        // direct nav without a clocked-in branch — never issue a request.
        is UiState.Idle -> {
            CenteredInventoryHint("No branch selected")
        }

        is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            ErrorCard(state.message, onRetry = onRetry)
        }

        is UiState.Success -> {
            if (state.data.isEmpty()) {
                CenteredInventoryHint("No inventory cards yet")
            } else {
                InventorySuccessList(
                    cards = state.data,
                    lowStockIds = lowStockIds,
                    restockEnabled = branchDayId != null && canRestock(capabilities, branchId) && !writesDisabled,
                    movementEnabled =
                        branchDayId != null &&
                            allowedMovementReasons(capabilities, branchId).isNotEmpty() &&
                            !writesDisabled,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun InventorySuccessList(
    cards: List<BranchInventoryResponse>,
    lowStockIds: Set<String>,
    restockEnabled: Boolean,
    movementEnabled: Boolean,
    onAction: (BranchInventoryResponse, InventoryWriteKind) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(cards.toInventoryRows(lowStockIds), key = { it.id }) { model ->
            val card = cards.first { it.id == model.id }
            InventoryRow(
                model = model,
                actions = InventoryRowActions(restockEnabled, movementEnabled),
                onAction = { kind -> onAction(card, kind) },
            )
        }
    }
}

@Composable
private fun InventoryRow(
    model: InventoryRowModel,
    actions: InventoryRowActions,
    onAction: (InventoryWriteKind) -> Unit,
) {
    // Hairline border instead of shadow per DESIGN.md (the RemittanceDetail row-card idiom);
    // the peso price stays visible regardless of which affordances the caller holds.
    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = model.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = model.stockLine,
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
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = model.priceLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (actions.restockEnabled) {
                    TextButton(onClick = { onAction(InventoryWriteKind.RESTOCK) }) {
                        Text("Restock")
                    }
                }
                if (actions.movementEnabled) {
                    TextButton(onClick = { onAction(InventoryWriteKind.MOVEMENT) }) {
                        Text("Record movement")
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryWriteDialogs(
    target: InventoryWriteTarget,
    viewModel: InventoryViewModel,
    branchId: String?,
    onDone: () -> Unit,
) {
    val capabilities by SessionState.capabilities.collectAsState()
    val branchDayId by SessionState.branchDayId.collectAsState()
    when (target) {
        is InventoryWriteTarget.Restock -> {
            RestockDialog(
                card = target.card,
                onDismiss = onDone,
                onSave = { units, editReason ->
                    // Save closes immediately (the ProfileScreen precedent); ids are fresh
                    // client-generated UUIDs and branchDayId is the clocked-in day.
                    onDone()
                    val dayId = branchDayId
                    if (branchId != null && dayId != null) {
                        viewModel.restock(
                            branchId = branchId,
                            productId = target.card.productId,
                            request =
                                buildRestockRequest(
                                    RestockDraft(target.card, units, editReason, dayId),
                                ),
                        )
                    }
                },
            )
        }

        is InventoryWriteTarget.Movement -> {
            MovementDialog(
                card = target.card,
                allowedReasons = allowedMovementReasons(capabilities, branchId),
                onDismiss = onDone,
                onSave = { reason, units, notes, editReason ->
                    onDone()
                    val dayId = branchDayId
                    if (branchId != null && dayId != null) {
                        viewModel.recordMovement(
                            branchId = branchId,
                            productId = target.card.productId,
                            request =
                                buildMovementRequest(
                                    MovementDraft(target.card, reason, units, notes, editReason, dayId),
                                ),
                        )
                    }
                },
            )
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
