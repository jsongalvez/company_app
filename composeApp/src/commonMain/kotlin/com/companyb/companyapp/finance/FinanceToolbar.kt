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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
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
    val appliedRange: Pair<String, String>?,
    val isRefreshing: Boolean,
    val feedLoading: Boolean,
    val canRefresh: Boolean,
)

/** #479 LPL burn — the toolbar's write-side (callbacks + export-note state) as one object. */
internal data class FinanceToolbarActions(
    val onBranchSelected: (String) -> Unit,
    val onRetryBranches: () -> Unit,
    val onModeSelected: (ReportMode) -> Unit,
    val onRefresh: () -> Unit,
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
            // #678 — the stable header owns Refresh: always mounted (never hidden with
            // the feed), disabled while any feed fetch is in flight or no refreshable
            // scope is selected.
            TextButton(
                onClick = actions.onRefresh,
                enabled = ui.canRefresh && !ui.isRefreshing && !ui.feedLoading,
            ) {
                if (ui.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(Spacing.sm).height(Spacing.sm),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Refresh")
                }
            }
            // #105 D4 — toolbar export = the mode's export (Daily has none — per-day only, in
            // detail). D6: every export point is CSV + PDF via the shared menu.
            val showModeExport =
                !ui.editMode && ui.mode != ReportMode.DAILY && ui.selectedBranchId != null &&
                    (ui.mode != ReportMode.DATE_RANGE || ui.appliedRange != null)
            if (showModeExport) {
                // #105 D4 — DATE_RANGE has no export until a window is applied (rendered only
                // when enabled: a disabled button would still eat 360dp toolbar width). The
                // key carries the branch (a superseded branch's export neither blocks nor
                // mislabels the current branch's — the landing still saves under its own key).
                // #678 — the day editor lives behind the selected-day header's Edit action;
                // the toolbar never edits, so the branch/date being edited is always named
                // beside its Edit button in the selected-day header.
                ExportMenu(
                    baseKey = "mode:${ui.selectedBranchId}:${ui.mode.name}",
                    onExport = actions.onExportMode,
                    errors = actions.exportErrors,
                    downloads = actions.downloads,
                )
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
    // #678 — Start/End labeled pickers with typed entry and inline validation: the draft
    // never fires a request (Apply validates first), so the last valid report stays
    // mounted until Apply. The error stacks below the fields — never inside the row —
    // so the row holds at 390dp with no horizontal scroll.
    // #728 — picker affordance rides inside the field (trailing Pick, audit precedent):
    // picking writes the same draft the text edits, never applies, dismissal no-ops.
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FinanceDateField(
                label = "Start (yyyy-MM-dd)",
                value = ui.fromInput,
                onValueChange = ui.onFromChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.xs))
            FinanceDateField(
                label = "End (yyyy-MM-dd)",
                value = ui.toInput,
                onValueChange = ui.onToChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            TextButton(onClick = onApply) { Text("Apply") }
            if (ui.applied) {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        if (paramError != null) {
            Text(
                text = paramError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xxs),
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
    // #678 — the error stacks below the row so the month picker holds at 390dp.
    // #728 — month picker rides inside the field (typed entry stays for speed):
    // picking writes the same draft, never applies, dismissal no-ops.
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FinanceMonthField(
                label = "Month (yyyy-MM)",
                value = monthInput,
                onValueChange = onMonthInputChange,
                modifier = Modifier.width(200.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            TextButton(onClick = onApply) { Text("Apply") }
        }
        if (paramError != null) {
            Text(
                text = paramError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xxs),
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
    // #678 — the error stacks below the row so the jump picker holds at 390dp.
    // #728 — same month-picker treatment as MonthParamRow (draft-only, no auto-jump).
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FinanceMonthField(
                label = "Jump to month (yyyy-MM)",
                value = monthInput,
                onValueChange = onJumpInputChange,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Spacing.sm))
            TextButton(onClick = onApply) { Text("Jump") }
            TextButton(onClick = onClear) { Text("All-time") }
        }
        if (paramError != null) {
            Text(
                text = paramError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceDateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            TextButton(onClick = { pickerOpen = true }) { Text("Pick") }
        },
        modifier = modifier,
    )
    if (pickerOpen) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = financeDateToPickerMillis(value))
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        financePickerMillisToDate(pickerState.selectedDateMillis)?.let(onValueChange)
                        pickerOpen = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceMonthField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        trailingIcon = {
            TextButton(onClick = { pickerOpen = true }) { Text("Pick") }
        },
        modifier = modifier,
    )
    if (pickerOpen) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = financeMonthToPickerMillis(value))
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        financePickerMillisToMonth(pickerState.selectedDateMillis)?.let(onValueChange)
                        pickerOpen = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text("Cancel") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

internal fun List<BranchResponse>.branchName(id: String?): String = firstOrNull { it.id == id }?.name ?: ""
