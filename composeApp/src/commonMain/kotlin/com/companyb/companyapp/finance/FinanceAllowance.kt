package com.companyb.companyapp.finance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branchday.BranchDayUserResponse
import com.companyb.companyapp.contracts.finance.AllowanceResponse
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn

@Composable
internal fun AllowanceSection(
    allowances: UiState<List<AllowanceResponse>>,
    users: UiState<List<BranchDayUserResponse>>,
    ui: FinanceAssignUi,
    onCreate: (String, String, String?) -> Unit,
    onReload: () -> Unit,
) {
    var showAssign by remember { mutableStateOf(false) }
    var assignWasInFlight by remember { mutableStateOf(false) }
    LaunchedEffect(ui.inFlight) {
        val busy = "allow:create" in ui.inFlight
        if (assignWasInFlight && !busy && ui.errors["allow:create"] == null) showAssign = false
        assignWasInFlight = busy
    }
    SectionHeader(
        title = "Allowances — not in P&L",
        actionLabel = "Assign allowance",
        onAction = { showAssign = true },
        showAction = ui.canAssign && !ui.readOnly && users is UiState.Success && users.data.isNotEmpty(),
    )
    val userNames = (users as? UiState.Success)?.data.orEmpty().associate { it.userId to it.displayName }
    when (allowances) {
        is UiState.Idle -> {}

        is UiState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(Spacing.sm), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.width(Spacing.lg).height(Spacing.lg), strokeWidth = 2.dp)
            }
        }

        is UiState.Error -> {
            logWarn("FinanceReportsScreen", "allowances=Error: ${allowances.message}")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = allowances.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onReload) { Text("Retry") }
            }
        }

        is UiState.Success -> {
            AllowanceSuccessList(
                allowances = allowances.data,
                userNames = userNames,
            )
        }
    }
    if (showAssign) {
        AllowanceDialog(
            users = users,
            busy = "allow:create" in ui.inFlight,
            error = ui.errors["allow:create"],
            onConfirm = { userId, amount, reason -> onCreate(userId, amount, reason) },
            onDismiss = { showAssign = false },
        )
    }
}

@Composable
private fun AllowanceSuccessList(
    allowances: List<AllowanceResponse>,
    userNames: Map<String, String>,
) {
    if (allowances.isEmpty()) {
        Text(
            text = "No allowances for this day.",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            modifier = Modifier.padding(Spacing.sm),
        )
    }
    allowances.forEach { allowance ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = userNames[allowance.userId] ?: allowance.userId,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                // #678 — missing amounts read unavailable, never zero (or a bare ₱).
                text = financeAmount(allowance.amount),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllowanceDialog(
    users: UiState<List<BranchDayUserResponse>>,
    busy: Boolean,
    error: String?,
    onConfirm: (String, String, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var userId by remember { mutableStateOf((users as? UiState.Success)?.data?.firstOrNull()?.userId ?: "") }
    var amount by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    val amountError = compensationAmountError(amount)
    val usersList = (users as? UiState.Success)?.data.orEmpty()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Assign allowance") },
        text = {
            Column {
                UserDropdown(
                    users = usersList,
                    selectedUserId = userId,
                    onSelected = { userId = it },
                )
                Spacer(Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₱, non-negative)") },
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = { if (amountError != null) Text(amountError) },
                )
                Spacer(Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (required on remitted days)") },
                )
                if (error != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (amountError == null && userId.isNotBlank()) {
                        onConfirm(userId, amount, reason.trim().ifBlank { null })
                    }
                },
                enabled = !busy && amountError == null && userId.isNotBlank(),
            ) {
                Text(if (busy) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}
