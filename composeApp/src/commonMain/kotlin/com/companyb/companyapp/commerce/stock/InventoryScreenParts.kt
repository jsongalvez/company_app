package com.companyb.companyapp.commerce.stock

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo

/** Entry load plus the #392 write-success legs (+#395 ensure-card): clear + authoritative refresh. */
@Composable
internal fun InventoryLoadEffects(
    viewModel: InventoryViewModel,
    branchId: String?,
    results: InventoryWriteResults,
) {
    LaunchedEffect(branchId) {
        logInfo("InventoryScreen", "composable entered (branchId=$branchId)")
        if (branchId != null) viewModel.refresh(branchId)
    }
    LaunchedEffect(results.restockResult) {
        consumeWriteSuccess(viewModel, branchId, results.restockResult, "restock")
    }
    LaunchedEffect(results.movementResult) {
        consumeWriteSuccess(viewModel, branchId, results.movementResult, "movement")
    }
    LaunchedEffect(results.cardResult) {
        consumeWriteSuccess(viewModel, branchId, results.cardResult, "ensure-card")
    }
    // #419 — a landed sale refreshes the same legs: the decrement and any new low-stock row
    // repaint without a manual Refresh.
    LaunchedEffect(results.saleResult) {
        consumeWriteSuccess(viewModel, branchId, results.saleResult, "product sale")
    }
}

/**
 * #396 — the low-stock surface between the header and the list: a count summary line when any
 * displayed card is low, or an inline retry strip when only the auxiliary leg failed (the list
 * leg's data stays up). Renders nothing while the legs are loading/absent.
 */
@Composable
internal fun LowStockStrip(
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

/** The restock arm (#392), extracted verbatim from [InventoryWriteDialogs]. */
@Composable
internal fun RestockWriteDialog(
    card: BranchInventoryResponse,
    context: InventorySectionContext,
    branchDayId: String?,
    onDone: () -> Unit,
) {
    RestockDialog(
        card = card,
        onDismiss = onDone,
        onSave = { units, editReason ->
            // Save closes immediately (the ProfileScreen precedent); the policy owns the
            // fail-closed branch/day guard, the request build, and the VM call.
            onDone()
            submitRestock(context.viewModel, context.branchId, branchDayId, card, units, editReason)
        },
    )
}

/** The movement arm (#392), extracted verbatim from [InventoryWriteDialogs]. */
@Composable
internal fun MovementWriteDialog(
    card: BranchInventoryResponse,
    context: InventorySectionContext,
    branchDayId: String?,
    onDone: () -> Unit,
) {
    val snapshot by AppSessionState.snapshot.collectAsState()
    val capabilities = snapshot.capabilities
    MovementDialog(
        card = card,
        allowedReasons = allowedMovementReasons(capabilities, context.branchId),
        onDismiss = onDone,
        onSave = { reason, units, notes, editReason ->
            onDone()
            submitMovement(
                context.viewModel,
                context.branchId,
                branchDayId,
                card,
                reason,
                units,
                notes,
                editReason,
            )
        },
    )
}

/** The walk-in sale arm (#419), extracted verbatim from [InventoryWriteDialogs]. */
@Composable
internal fun WalkInWriteDialog(
    card: BranchInventoryResponse,
    context: InventorySectionContext,
    branchDayId: String?,
    onDone: () -> Unit,
) {
    WalkInSaleDialog(
        card = card,
        clientSearch = context.clientSearch,
        onDismiss = onDone,
        onSave = { quantity, clientId, editReason ->
            // Save closes immediately (the #392 shape); the policy owns the fail-closed
            // branch/day guard, the walk-in request build, and the sale call.
            onDone()
            submitWalkInSale(
                context.productSaleViewModel,
                context.branchId,
                branchDayId,
                card,
                quantity,
                clientId,
                editReason,
            )
        },
    )
}

/** Inline surface for write failures (the EditSlotDialog inline-error shape, screen-level). */
@Composable
internal fun WriteErrorBanner(
    results: InventoryWriteResults,
    viewModel: InventoryViewModel,
    onDismissSale: () -> Unit,
) {
    val error =
        firstWriteError(results.restockResult, results.movementResult, results.cardResult, results.saleResult)
            ?: return
    // The sale leg lives in its own VM (#419) — its Dismiss clears there, the rest here.
    val fromSale = error === (results.saleResult as? UiState.Error)
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
            TextButton(onClick = { if (fromSale) onDismissSale() else viewModel.clearWriteResults() }) {
                Text("Dismiss")
            }
        }
    }
}
