package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.domain.ReliefAccessStatus
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.dto.ReliefBranchOptionResponse
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.viewmodel.ReliefAccessViewModel
import com.companyb.companyapp.viewmodel.UiState

/**
 * The outsider's pre-clock-in relief-request surface (#357): pick any branch, optionally
 * name a future date (blank = today), one button submits; live asks list below with
 * Withdraw. Collapsed by default so scheduling stays the screen's primary action.
 */
@Composable
fun ReliefRequestPanel(
    viewModel: ReliefAccessViewModel,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedBranchId by remember { mutableStateOf<String?>(null) }
    var selectedBranchName by remember { mutableStateOf<String?>(null) }
    var dateInput by remember { mutableStateOf("") }

    val options by viewModel.branchOptions.collectAsState()
    val mine by viewModel.freshestMine.collectAsState()
    val requestState by viewModel.requestResult.collectAsState()
    val cancelState by viewModel.cancelResult.collectAsState()

    if (!expanded) {
        TextButton(onClick = {
            expanded = true
            viewModel.loadBranchOptions()
            viewModel.loadMine()
        }) {
            Text("Request relief duty…")
        }
        return
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        TextButton(onClick = { expanded = false }) { Text("Hide relief requests") }

        BranchOptionsRow(
            options = options,
            selectedBranchId = selectedBranchId,
            onPick = { option ->
                selectedBranchId = option.branchId
                selectedBranchName = option.branchName
            },
        )

        OutlinedTextField(
            value = dateInput,
            onValueChange = { dateInput = it },
            label = { Text("Date (yyyy-MM-dd, blank = today)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val canSubmit = selectedBranchId != null && requestState !is UiState.Loading
        val submitLabel =
            buildString {
                append("Request relief duty")
                selectedBranchName?.let { append(" at $it") }
                if (dateInput.isNotBlank()) append(" on $dateInput")
            }
        Button(
            enabled = canSubmit,
            onClick = { viewModel.requestAccess(selectedBranchId!!, dateInput.ifBlank { null }) },
        ) {
            Text(submitLabel)
        }

        MyReliefRequests(mine.orEmpty(), cancelState, onCancel = { viewModel.cancel(it.id, null) })

        val error =
            listOfNotNull(requestState as? UiState.Error, cancelState as? UiState.Error).firstOrNull()?.message
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun BranchOptionsRow(
    options: UiState<List<ReliefBranchOptionResponse>>,
    selectedBranchId: String?,
    onPick: (ReliefBranchOptionResponse) -> Unit,
) {
    when (options) {
        is UiState.Loading -> {
            Text("Loading branches…", style = MaterialTheme.typography.bodySmall, color = InkSubtle)
        }

        is UiState.Error -> {
            Text(options.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        else -> {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                (options as? UiState.Success)?.data?.forEach { option ->
                    TextButton(onClick = { onPick(option) }) {
                        val suffix =
                            if (option.branchId == selectedBranchId) {
                                " ✓"
                            } else {
                                ""
                            }
                        Text(option.branchName + suffix)
                    }
                }
            }
        }
    }
}

@Composable
private fun MyReliefRequests(
    mine: List<ReliefAccessResponse>,
    cancelState: UiState<Unit>,
    onCancel: (ReliefAccessResponse) -> Unit,
) {
    // #399 — past-operational-date PENDING rows render Expired with no Withdraw, mirroring
    // the NotificationsScreen invite pattern; day-state is authoritative, not a new status.
    val today = currentOperationalDate()
    val live = mine.filter { it.requestStatus == ReliefAccessStatus.PENDING }
    if (live.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Your pending requests", style = MaterialTheme.typography.labelSmall, color = InkSubtle)
        live.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${row.branchName ?: "Branch"} — ${row.date ?: "today"}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (isRequestExpired(row, today)) {
                    Text(
                        text = "Expired",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    TextButton(enabled = cancelState !is UiState.Loading, onClick = { onCancel(row) }) {
                        Text("Withdraw")
                    }
                }
            }
        }
    }
}
