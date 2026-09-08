package com.companyb.companyapp.finance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn

/** #479 LPL burn — the toolbar's read-side (state + gates) as one object. */
internal data class FinanceToolbarUi(
    val branches: UiState<List<BranchResponse>>,
    val selectedBranchId: String?,
    val mode: ReportMode,
    val editMode: Boolean,
    val canEdit: Boolean,
    val appliedRange: Pair<String, String>?,
)

/** #479 LPL burn — the toolbar's write-side (callbacks + export-note state) as one object. */
internal data class FinanceToolbarActions(
    val onBranchSelected: (String) -> Unit,
    val onRetryBranches: () -> Unit,
    val onModeSelected: (ReportMode) -> Unit,
    val onEditToggle: () -> Unit,
    val onExportMode: (String) -> Unit,
    val downloads: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>>,
    val exportErrors: Map<String, String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FinanceToolbar(
    ui: FinanceToolbarUi,
    actions: FinanceToolbarActions,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FinanceBranchSelector(
                branches = ui.branches,
                selectedBranchId = ui.selectedBranchId,
                onBranchSelected = actions.onBranchSelected,
                onRetryBranches = actions.onRetryBranches,
            )
            Spacer(Modifier.weight(1f))
            // #105 D4 — toolbar export = the mode's export (Daily has none — per-day only, in
            // detail). D6: every export point is CSV + PDF.
            val showModeExport =
                !ui.editMode && ui.mode != ReportMode.DAILY && ui.selectedBranchId != null &&
                    (ui.mode != ReportMode.DATE_RANGE || ui.appliedRange != null)
            if (showModeExport) {
                // #105 D4 — DATE_RANGE has no export until a window is applied (rendered only
                // when enabled: a disabled button would still eat 360dp toolbar width). The
                // key carries the branch (a superseded branch's late landing stays inert).
                ExportButtons(
                    baseKey = "mode:${ui.selectedBranchId}:${ui.mode.name}",
                    onExport = actions.onExportMode,
                    errors = actions.exportErrors,
                    downloads = actions.downloads,
                )
            }
            if (ui.canEdit) {
                TextButton(onClick = actions.onEditToggle) {
                    Text(if (ui.editMode) "Done editing" else "Edit this day")
                }
            }
        }
        FinanceModeTabs(mode = ui.mode, onModeSelected = actions.onModeSelected)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.FinanceBranchSelector(
    branches: UiState<List<BranchResponse>>,
    selectedBranchId: String?,
    onBranchSelected: (String) -> Unit,
    onRetryBranches: () -> Unit,
) {
    var branchMenuOpen by remember { mutableStateOf(false) }
    when (branches) {
        is UiState.Loading -> {
            CircularProgressIndicator(
                modifier = Modifier.width(Spacing.md).height(Spacing.md),
                strokeWidth = 2.dp,
            )
        }

        is UiState.Error -> {
            logWarn("FinanceReportsScreen", "branches=Error: ${branches.message}")
            Text(
                text = branches.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onRetryBranches) { Text("Retry") }
        }

        else -> {}
    }
    if (branches is UiState.Success) {
        ExposedDropdownMenuBox(
            expanded = branchMenuOpen,
            onExpandedChange = { branchMenuOpen = it },
        ) {
            OutlinedTextField(
                value =
                    branches.data
                        .firstOrNull { it.id == selectedBranchId }
                        ?.name
                        ?: "Select branch",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text("Branch") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = branchMenuOpen) },
                // Pass-4 HARD — the fixed 280dp field crushed the trailing exports/edit
                // toggle at 360dp; the field shrinks first (weight), caps at 280dp.
                modifier =
                    Modifier
                        .menuAnchor(
                            ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        ).weight(1f, fill = false)
                        .width(280.dp),
            )
            ExposedDropdownMenu(
                expanded = branchMenuOpen,
                onDismissRequest = { branchMenuOpen = false },
            ) {
                FinanceBranchMenuItems(
                    branches = branches.data,
                    onBranchSelected = onBranchSelected,
                    onClose = { branchMenuOpen = false },
                )
            }
        }
    }
}

@Composable
private fun FinanceBranchMenuItems(
    branches: List<BranchResponse>,
    onBranchSelected: (String) -> Unit,
    onClose: () -> Unit,
) {
    branches.forEach { branch ->
        DropdownMenuItem(
            text = { Text(branch.name) },
            onClick = {
                onClose()
                onBranchSelected(branch.id)
            },
        )
    }
}

@Composable
private fun FinanceModeTabs(
    mode: ReportMode,
    onModeSelected: (ReportMode) -> Unit,
) {
    // #105 D4 — mode tabs (#596: PrimaryTabRow replaces deprecated TabRow, same selected-index contract)
    PrimaryTabRow(selectedTabIndex = mode.ordinal) {
        ReportMode.entries.forEachIndexed { index, reportMode ->
            Tab(
                selected = index == mode.ordinal,
                onClick = { onModeSelected(reportMode) },
                text = { Text(reportMode.tabLabel) },
            )
        }
    }
}

private val ReportMode.tabLabel: String
    get() =
        when (this) {
            ReportMode.DAILY -> "Daily"
            ReportMode.MONTHLY -> "Monthly"
            ReportMode.ALL_TIME -> "All-time"
            ReportMode.DATE_RANGE -> "Date range"
        }

/** #479 LPL burn — the DATE_RANGE param row's editable window state as one object. */
internal data class DateRangeParamUi(
    val fromInput: String,
    val toInput: String,
    val onFromChange: (String) -> Unit,
    val onToChange: (String) -> Unit,
    val applied: Boolean,
)

@Composable
internal fun DateRangeParamRow(
    ui: DateRangeParamUi,
    onApply: () -> Unit,
    onClear: () -> Unit,
    paramError: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = ui.fromInput,
            onValueChange = ui.onFromChange,
            label = { Text("From (yyyy-MM-dd)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.xs))
        OutlinedTextField(
            value = ui.toInput,
            onValueChange = ui.onToChange,
            label = { Text("To (yyyy-MM-dd)") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        TextButton(onClick = onApply) { Text(if (ui.applied) "Apply" else "Go") }
        if (ui.applied) {
            TextButton(onClick = onClear) { Text("Clear") }
        }
        if (paramError != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = paramError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun MonthParamRow(
    monthInput: String,
    onMonthInputChange: (String) -> Unit,
    onApply: () -> Unit,
    paramError: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = monthInput,
            onValueChange = onMonthInputChange,
            label = { Text("Month (yyyy-MM)") },
            singleLine = true,
            modifier = Modifier.width(200.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        TextButton(onClick = onApply) { Text("Go") }
        if (paramError != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = paramError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun JumpParamRow(
    monthInput: String,
    onJumpInputChange: (String) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    paramError: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = monthInput,
            onValueChange = onJumpInputChange,
            label = { Text("Jump to month (yyyy-MM)") },
            singleLine = true,
            modifier = Modifier.width(220.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        TextButton(onClick = onApply) { Text("Jump") }
        TextButton(onClick = onClear) { Text("All-time") }
        if (paramError != null) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = paramError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

internal fun List<BranchResponse>.branchName(id: String?): String = firstOrNull { it.id == id }?.name ?: ""
