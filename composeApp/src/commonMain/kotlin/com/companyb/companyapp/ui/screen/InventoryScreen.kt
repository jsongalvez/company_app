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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.dto.BranchInventoryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.viewmodel.InventoryViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * #391 — read-only branch Inventory list (the dead "pending build ticket" placeholder's
 * replacement). Branch-scoped to the clocked-in branch (`selectedBranchId`, the Remittance
 * shape); the route gate lives at the NavHost call sites (any-context EDIT_BRANCH_DATA —
 * mirrors the #108 drawer item), with the backend's branch-scoped read gate authoritative.
 *
 * States: load-on-entry + Refresh button (the Remittance D7 axis); Loading spinner;
 * error → shared ErrorCard retry; empty branch inventory hint; success rows sorted by
 * product name (toInventoryRows pins the presentation rules, desktopTest-packeted).
 */
@Composable
fun InventoryScreen(
    viewModel: InventoryViewModel,
    branchId: String?,
) {
    val inventoryState by viewModel.inventory.collectAsState()

    LaunchedEffect(branchId) {
        logInfo("InventoryScreen", "composable entered (branchId=$branchId)")
        if (branchId != null) {
            viewModel.loadInventory(branchId)
        }
    }

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
            Text(
                text = "Inventory",
                style = MaterialTheme.typography.titleLarge,
            )
            TextButton(
                onClick = { if (branchId != null) viewModel.loadInventory(branchId) },
                enabled = branchId != null && inventoryState !is UiState.Loading,
            ) {
                Text("Refresh")
            }
        }
        when (val state = inventoryState) {
            // Defensive: the drawer is only composed post-clock-in, so a null branch here
            // means a direct nav without a clocked-in branch — never issue a request.
            is UiState.Idle -> {
                CenteredInventoryHint("No branch selected")
            }

            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                ErrorCard(
                    message = state.message,
                    onRetry = { if (branchId != null) viewModel.loadInventory(branchId) },
                )
            }

            is UiState.Success -> {
                if (state.data.isEmpty()) {
                    CenteredInventoryHint("No inventory cards yet")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        items(state.data.toInventoryRows(), key = { it.id }) { row ->
                            InventoryRow(row)
                        }
                    }
                }
            }
        }
    }
}

/** Presentation rule surface for the desktopTest packet: sort + display-line mapping. */
internal fun List<BranchInventoryResponse>.toInventoryRows(): List<InventoryRowModel> =
    sortedBy { it.productName.lowercase() }.map { card ->
        InventoryRowModel(
            id = card.id,
            productName = card.productName,
            stockLine = "In stock: ${card.currentStock}",
            priceLine = "₱${card.unitPrice}",
        )
    }

internal data class InventoryRowModel(
    val id: String,
    val productName: String,
    val stockLine: String,
    val priceLine: String,
)

@Composable
private fun InventoryRow(row: InventoryRowModel) {
    // Hairline border instead of shadow per DESIGN.md (the RemittanceDetail row-card idiom).
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
                    text = row.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = row.stockLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = row.priceLine,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
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
