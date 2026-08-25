package com.companyb.companyapp.ui.screen

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.viewmodel.InventoryViewModel
import com.companyb.companyapp.viewmodel.UiState

/** Entry load plus the #392 write-success legs (+#395 ensure-card): clear + authoritative refresh. */
@Composable
internal fun InventoryLoadEffects(
    viewModel: InventoryViewModel,
    branchId: String?,
    restockResult: UiState<*>,
    movementResult: UiState<*>,
    cardResult: UiState<*>,
    saleResult: UiState<*>,
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
    // #419 — a landed sale refreshes the same legs: the decrement and any new low-stock row
    // repaint without a manual Refresh.
    LaunchedEffect(saleResult) {
        if (saleResult is UiState.Success) {
            logInfo("InventoryScreen", "product sale landed — refreshing inventory")
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

/** Inline surface for write failures (the EditSlotDialog inline-error shape, screen-level). */
@Composable
internal fun WriteErrorBanner(
    restockResult: UiState<*>,
    movementResult: UiState<*>,
    cardResult: UiState<*>,
    saleResult: UiState<*>,
    viewModel: InventoryViewModel,
    onDismissSale: () -> Unit,
) {
    val error =
        (restockResult as? UiState.Error)
            ?: (movementResult as? UiState.Error)
            ?: (cardResult as? UiState.Error)
            ?: (saleResult as? UiState.Error)
            ?: return
    // The sale leg lives in its own VM (#419) — its Dismiss clears there, the rest here.
    val fromSale = error === (saleResult as? UiState.Error)
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
