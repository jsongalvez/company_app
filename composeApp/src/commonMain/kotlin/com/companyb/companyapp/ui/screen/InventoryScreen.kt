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

/** Handles the extracted sections share (the header and overlay hosts). */
private class InventorySectionContext(
    val viewModel: InventoryViewModel,
    val productViewModel: ProductViewModel,
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
 * #397 — movements-history browsing: a "History" header affordance opens the branch's past
 * stock movements in a dialog, loaded on demand through its own VM leg (failure degrades to
 * inline retry there, never touching the list legs).
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
    val branchDayId by SessionState.branchDayId.collectAsState()
    var overlay by remember { mutableStateOf<InventoryOverlay?>(null) }
    val context = InventorySectionContext(viewModel, productViewModel, branchId)
    val writesDisabled = restockResult is UiState.Loading || movementResult is UiState.Loading

    InventoryLoadEffects(viewModel, branchId, restockResult, movementResult, cardResult)
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        InventoryHeader(context, inventoryState, cardResult, onOverlay = { overlay = it })
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
            rowActions =
                InventoryRowActions(
                    restockEnabled = branchDayId != null && canRestock(capabilities, branchId) && !writesDisabled,
                    movementEnabled =
                        branchDayId != null &&
                            allowedMovementReasons(capabilities, branchId).isNotEmpty() &&
                            !writesDisabled,
                ),
            onRetry = { if (branchId != null) viewModel.refresh(branchId) },
            onAction = { card, kind ->
                overlay =
                    if (kind == InventoryWriteKind.RESTOCK) {
                        InventoryOverlay.Write(InventoryWriteTarget.Restock(card))
                    } else {
                        InventoryOverlay.Write(InventoryWriteTarget.Movement(card))
                    }
            },
        )
    }
    InventoryOverlayHosts(
        overlay = overlay,
        context = context,
        cards = (inventoryState as? UiState.Success)?.data.orEmpty(),
        onClose = { overlay = null },
    )
}

/**
 * The screen header: title plus the History (#397), Add card (#395), and Refresh affordances,
 * each gated exactly where it was before the extraction.
 */
@Composable
private fun InventoryHeader(
    context: InventorySectionContext,
    inventoryState: UiState<List<BranchInventoryResponse>>,
    cardResult: UiState<*>,
    onOverlay: (InventoryOverlay) -> Unit,
) {
    val capabilities by SessionState.capabilities.collectAsState()
    val branchId = context.branchId
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Inventory", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (branchId != null) {
                TextButton(onClick = { onOverlay(InventoryOverlay.MovementsHistory) }) {
                    Text("History")
                }
            }
            if (branchId != null && canEnsureCard(capabilities, branchId)) {
                TextButton(
                    onClick = { onOverlay(InventoryOverlay.EnsureCard) },
                    enabled = cardResult !is UiState.Loading,
                ) {
                    Text("Add card")
                }
            }
            TextButton(
                onClick = { if (branchId != null) context.viewModel.refresh(branchId) },
                enabled = branchId != null && inventoryState !is UiState.Loading,
            ) {
                Text("Refresh")
            }
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
            InventoryWriteDialogs(overlay.target, context.viewModel, context.branchId, onClose)
        }

        InventoryOverlay.EnsureCard -> {
            EnsureCardDialog(
                productViewModel = context.productViewModel,
                cards = cards,
                onDismiss = onClose,
                onSave = { productId ->
                    onClose()
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

@Composable
private fun InventoryBody(
    state: UiState<List<BranchInventoryResponse>>,
    lowStockIds: Set<String>,
    rowActions: InventoryRowActions,
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
                    rowActions = rowActions,
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
    rowActions: InventoryRowActions,
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
                actions = rowActions,
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
