package com.companyb.companyapp.commerce.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.commerce.BranchInventoryResponse
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

/** The restock arm (#392, retained #676): submit keeps the dialog mounted, success closes. */
@Composable
internal fun RestockWriteDialog(
    card: BranchInventoryResponse,
    context: InventorySectionContext,
    branchDayId: String?,
    onDone: () -> Unit,
) {
    val restockResult by context.viewModel.restockResult.collectAsState()
    LaunchedEffect(restockResult) {
        if (restockResult is UiState.Success) onDone()
        val error = restockResult as? UiState.Error
        if (error != null && needsVersionReconcile(error.message)) {
            context.branchId?.let { context.viewModel.refresh(it) }
        }
    }
    RestockDialog(
        card = card,
        result = restockResult,
        onDismiss = {
            if (restockResult !is UiState.Loading) onDone()
        },
        onSubmit = { units, editReason, operationId ->
            submitRestock(context.viewModel, context.branchId, branchDayId, card, units, editReason, operationId)
        },
    )
}

/** The movement arm (#392, retained #676): submit keeps the dialog mounted, success closes. */
@Composable
internal fun MovementWriteDialog(
    card: BranchInventoryResponse,
    context: InventorySectionContext,
    branchDayId: String?,
    onDone: () -> Unit,
) {
    val snapshot by AppSessionState.snapshot.collectAsState()
    val capabilities = snapshot.capabilities
    val movementResult by context.viewModel.movementResult.collectAsState()
    LaunchedEffect(movementResult) {
        if (movementResult is UiState.Success) onDone()
        val error = movementResult as? UiState.Error
        if (error != null && needsVersionReconcile(error.message)) {
            context.branchId?.let { context.viewModel.refresh(it) }
        }
    }
    MovementDialog(
        card = card,
        allowedReasons = allowedMovementReasons(capabilities, context.branchId),
        result = movementResult,
        onDismiss = {
            if (movementResult !is UiState.Loading) onDone()
        },
        onSubmit = { reason, units, notes, editReason, operationId ->
            submitMovement(
                context.viewModel,
                context.branchId,
                branchDayId,
                card,
                reason,
                units,
                notes,
                editReason,
                operationId,
            )
        },
    )
}

/** The walk-in sale arm (#419, retained #676): submit keeps the dialog mounted, success closes. */
@Composable
internal fun WalkInWriteDialog(
    card: BranchInventoryResponse,
    context: InventorySectionContext,
    branchDayId: String?,
    onDone: () -> Unit,
) {
    val saleResult by context.productSaleViewModel.saleResult.collectAsState()
    // #676 — the walk-in path drains its own terminal: close + clear so the next Sell
    // opens fresh (the session pane drains via SaleEffects; this screen owns its leg).
    // Version/stock conflicts refresh the list under the retained draft so Retry converges.
    LaunchedEffect(saleResult) {
        if (saleResult is UiState.Success) {
            onDone()
            context.productSaleViewModel.clearSaleResult()
        }
        val error = saleResult as? UiState.Error
        if (error != null && needsVersionReconcile(error.message)) {
            context.branchId?.let { context.viewModel.refresh(it) }
        }
    }
    WalkInSaleDialog(
        card = card,
        clientSearch = context.clientSearch,
        saleResult = saleResult,
        onDismiss = {
            if (saleResult !is UiState.Loading) onDone()
        },
        onSubmit = { quantity, clientId, editReason, operationId ->
            submitWalkInSale(
                context.productSaleViewModel,
                context.branchId,
                branchDayId,
                card,
                quantity,
                clientId,
                editReason,
                operationId,
            )
        },
    )
}
