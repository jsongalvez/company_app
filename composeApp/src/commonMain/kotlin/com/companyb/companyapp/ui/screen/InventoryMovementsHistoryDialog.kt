package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.viewmodel.InventoryViewModel
import com.companyb.companyapp.viewmodel.UiState

/** Shared dialog-body leg: the centered spinner for Idle/Loading states. */
@Composable
internal fun DialogLoadingSpinner() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

/** Shared dialog-body leg: the error message + inline Retry pair (the EnsureCard shape). */
@Composable
internal fun DialogErrorRetry(
    message: String,
    onRetry: () -> Unit,
) {
    Column {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.size(Spacing.sm))
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

/**
 * #397 — browse the branch's past stock movements, newest-first (the backend's order).
 * Loads through its own VM leg on open (the EnsureCardDialog load-on-open shape); a failure
 * degrades to inline retry without touching the list/low-stock legs. Rows resolve product
 * names from the loaded cards (raw-id fallback) via the pure [toMovementRows] mapper, and
 * timestamps reuse the shared relative formatter (Asia/Manila absolute past 24h).
 */
@Composable
internal fun MovementsHistoryDialog(
    viewModel: InventoryViewModel,
    branchId: String,
    cards: List<BranchInventoryResponse>,
    onDismiss: () -> Unit,
) {
    val movements by viewModel.movements.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadMovements(branchId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stock movements") },
        text = {
            when (val state = movements) {
                is UiState.Idle,
                is UiState.Loading,
                -> {
                    DialogLoadingSpinner()
                }

                is UiState.Error -> {
                    DialogErrorRetry(state.message, onRetry = { viewModel.loadMovements(branchId) })
                }

                is UiState.Success -> {
                    MovementsHistoryList(rows = state.data.toMovementRows(cards))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

/** The movements-history rows: product, reason · signed quantity, timestamp, notes (#397). */
@Composable
private fun MovementsHistoryList(rows: List<MovementRowModel>) {
    if (rows.isEmpty()) {
        Text(
            text = "No stock movements recorded yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            items(rows, key = { it.id }) { row ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        text = row.productName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Text(
                            text = row.reasonLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = formatRelativeTimestamp(row.movedAt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    row.notes?.let { notes ->
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
