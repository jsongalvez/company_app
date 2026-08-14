package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.companyb.companyapp.dto.AllowanceResponse
import com.companyb.companyapp.dto.BranchDayUserResponse
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.CompensationResponse
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.dto.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.saveDownload
import com.companyb.companyapp.viewmodel.EditSection
import com.companyb.companyapp.viewmodel.FinanceReportsViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The merged Finance & Reports screen (#101 D1-D8 + #105 D1-D7, built #154).
 *
 * Read-only feed surface by default (branch picker → mode tabs → windowed day feed → day
 * detail with figures + per-day exports); an Edit toggle (visible only for edit-capability
 * holders, #105 D1) swaps the main area to the day editor: summary cards + P&L breakdown +
 * expense log (dimmed deleted rows + reason + restore) + compensation list/assign +
 * allowances ("not in P&L"). Public Provincial Tour / Medical Mission exports render as a
 * secondary section (any logged-in user, #128).
 */
@Composable
fun FinanceReportsScreen(
    viewModel: FinanceReportsViewModel,
    modifier: Modifier = Modifier,
) {
    val branches by viewModel.branches.collectAsState()
    val selectedBranchId by viewModel.selectedBranchId.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val monthInput by viewModel.monthInput.collectAsState()
    val rangeFromInput by viewModel.rangeFromInput.collectAsState()
    val rangeToInput by viewModel.rangeToInput.collectAsState()
    val paramError by viewModel.paramError.collectAsState()
    val appliedRange by viewModel.appliedRange.collectAsState()
    val monthlyRollup by viewModel.monthlyRollup.collectAsState()
    val feed by viewModel.feedEntries.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val editMode by viewModel.editMode.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val exportErrors by viewModel.exportErrors.collectAsState()

    val today =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.of("Asia/Manila"))
            .date

    LaunchedEffect(Unit) {
        viewModel.loadBranches()
    }

    // D6 — the platform save boundary: a successful export payload is handed to saveDownload
    // (desktop save dialog / Android Downloads), then consumed so the button clears.
    LaunchedEffect(downloads) {
        downloads
            .filterValues { it is UiState.Success }
            .forEach { (key, state) ->
                val payload = (state as UiState.Success<FinanceReportsViewModel.DownloadPayload>).data
                saveDownload(payload.fileName, payload.bytes)
                viewModel.consumeDownload(key)
            }
    }

    Column(modifier = modifier.fillMaxSize().padding(Spacing.md)) {
        FinanceToolbar(
            branches = branches,
            selectedBranchId = selectedBranchId,
            onBranchSelected = viewModel::selectBranch,
            mode = mode,
            onModeSelected = viewModel::setMode,
            canEdit = viewModel.hasEditCapabilities() && selectedDay != null,
            editMode = editMode,
            onEditToggle = { viewModel.setEditMode(!editMode) },
        )
        when (mode) {
            ReportMode.MONTHLY -> {
                MonthParamRow(
                    monthInput = monthInput,
                    onMonthInputChange = viewModel::setMonthInput,
                    onApply = viewModel::applyMonth,
                    paramError = paramError,
                )
            }

            ReportMode.ALL_TIME -> {
                JumpParamRow(
                    monthInput = monthInput,
                    onJumpInputChange = viewModel::setJumpInput,
                    onApply = viewModel::applyJump,
                    onClear = viewModel::clearJump,
                    hasJump = true,
                    paramError = paramError,
                )
            }

            ReportMode.DATE_RANGE -> {
                DateRangeParamRow(
                    fromInput = rangeFromInput,
                    toInput = rangeToInput,
                    onFromChange = { viewModel.setRangeInputs(it, rangeToInput) },
                    onToChange = { viewModel.setRangeInputs(rangeFromInput, it) },
                    onApply = viewModel::applyRange,
                    applied = appliedRange != null,
                    paramError = paramError,
                )
            }

            ReportMode.DAILY -> {}
        }

        val selectedBranch = selectedBranchId
        val day = selectedDay

        when {
            editMode && day != null && selectedBranch != null -> {
                DayEditor(
                    viewModel = viewModel,
                    day = day,
                    branchName = (branches as? UiState.Success)?.data.orEmpty().branchName(selectedBranch),
                    today = today,
                    onBackToFeed = { viewModel.setEditMode(false) },
                    modifier = Modifier.weight(1f),
                )
            }

            else -> {
                FeedSection(
                    viewModel = viewModel,
                    feed = feed,
                    mode = mode,
                    monthlyRollup = monthlyRollup,
                    selectedBranchId = selectedBranch,
                    selectedDay = selectedDay,
                    onDaySelected = viewModel::selectDay,
                    today = today,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        PublicReportsSection(
            exportErrors = exportErrors,
            onExport = viewModel::exportPublic,
        )
    }
}

private fun List<BranchResponse>.branchName(id: String?): String = firstOrNull { it.id == id }?.name ?: ""

// ─────────────────────────── toolbar + params ───────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceToolbar(
    branches: UiState<List<BranchResponse>>,
    selectedBranchId: String?,
    onBranchSelected: (String) -> Unit,
    mode: ReportMode,
    onModeSelected: (ReportMode) -> Unit,
    canEdit: Boolean,
    editMode: Boolean,
    onEditToggle: () -> Unit,
) {
    var branchMenuOpen by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (branches) {
                is UiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.width(Spacing.md).height(Spacing.md),
                        strokeWidth = 2.dp,
                    )
                }

                is UiState.Error -> {
                    Text(
                        text = branches.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { onBranchSelected(selectedBranchId ?: "") }) {}
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
                        modifier = Modifier.menuAnchor().width(280.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = branchMenuOpen,
                        onDismissRequest = { branchMenuOpen = false },
                    ) {
                        branches.data.forEach { branch ->
                            DropdownMenuItem(
                                text = { Text(branch.name) },
                                onClick = {
                                    branchMenuOpen = false
                                    onBranchSelected(branch.id)
                                },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (canEdit) {
                TextButton(onClick = onEditToggle) {
                    Text(if (editMode) "Done editing" else "Edit this day")
                }
            }
        }
        // #105 D4 — mode tabs
        TabRow(selectedTabIndex = mode.ordinal) {
            ReportMode.entries.forEachIndexed { index, reportMode ->
                Tab(
                    selected = index == mode.ordinal,
                    onClick = { onModeSelected(reportMode) },
                    text = { Text(reportMode.tabLabel) },
                )
            }
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

@Composable
private fun MonthParamRow(
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
private fun JumpParamRow(
    monthInput: String,
    onJumpInputChange: (String) -> Unit,
    onApply: () -> Unit,
    onClear: () -> Unit,
    hasJump: Boolean,
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
        if (hasJump) {
            TextButton(onClick = onClear) { Text("All-time") }
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
private fun DateRangeParamRow(
    fromInput: String,
    toInput: String,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onApply: () -> Unit,
    applied: Boolean,
    paramError: String?,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = fromInput,
            onValueChange = onFromChange,
            label = { Text("From (yyyy-MM-dd)") },
            singleLine = true,
            modifier = Modifier.width(190.dp),
        )
        Spacer(Modifier.width(Spacing.xs))
        OutlinedTextField(
            value = toInput,
            onValueChange = onToChange,
            label = { Text("To (yyyy-MM-dd)") },
            singleLine = true,
            modifier = Modifier.width(190.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        TextButton(onClick = onApply) { Text(if (applied) "Apply" else "Go") }
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

// ─────────────────────────── feed ───────────────────────────

@Composable
private fun FeedSection(
    viewModel: FinanceReportsViewModel,
    feed: UiState<List<DailySalesSummaryResponse>>,
    mode: ReportMode,
    monthlyRollup: UiState<MonthlyRemittanceSummaryResponse?>,
    selectedBranchId: String?,
    selectedDay: DailySalesSummaryResponse?,
    onDaySelected: (DailySalesSummaryResponse) -> Unit,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        if (mode == ReportMode.MONTHLY && monthlyRollup is UiState.Success && monthlyRollup.data != null) {
            val rollup = monthlyRollup.data
            MonthlyRollupCard(rollup = rollup)
        }
        when (feed) {
            is UiState.Idle -> {}

            is UiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                ErrorCard(message = feed.message, onRetry = viewModel::retryFeed)
            }

            is UiState.Success -> {
                if (feed.data.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text =
                                "No data for this branch" +
                                    if (mode == ReportMode.DATE_RANGE) " and date range" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSubtle,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(feed.data, key = { it.branchDayId }) { day ->
                            DayRow(
                                day = day,
                                today = today,
                                selected = day.branchDayId == selectedDay?.branchDayId,
                                onSelect = { onDaySelected(day) },
                            )
                        }
                        item(key = "load-more") {
                            val isLoadingMore by viewModel.isLoadingMore.collectAsState()
                            val loadMoreError by viewModel.loadMoreError.collectAsState()
                            val loadMoreErrorValue = loadMoreError
                            if (loadMoreErrorValue != null) {
                                Text(
                                    text = loadMoreErrorValue,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(Spacing.sm),
                                )
                            }
                            val nextCursor by viewModel.nextCursor.collectAsState()
                            if (nextCursor != null || isLoadingMore) {
                                TextButton(
                                    onClick = viewModel::loadMore,
                                    enabled = !isLoadingMore,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(),
                                ) {
                                    if (isLoadingMore) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.width(Spacing.md).height(Spacing.md),
                                            strokeWidth = 2.dp,
                                        )
                                    } else {
                                        Text("Load more")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyRollupCard(rollup: MonthlyRemittanceSummaryResponse) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Text(
                text = "Month rollup",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Gross ${peso(rollup.grossIncome)}", style = MaterialTheme.typography.bodySmall)
            Text("Comp ${peso(rollup.totalCompensation)}", style = MaterialTheme.typography.bodySmall)
            Text("Exp ${peso(rollup.totalExpenses)}", style = MaterialTheme.typography.bodySmall)
            Text("Net ${peso(rollup.netIncome)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DayRow(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val state = derivedDayState(LocalDate.parse(day.date), today)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .background(
                    if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                ).padding(horizontal = Spacing.xs, vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.date,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (state == DerivedDayState.PAST) InkSubtle else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Net ${peso(day.netIncome)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = "Gross ${peso(day.grossIncome)}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
        FinanceDayDetail(day = day, today = today, expanded = selected, onClose = onSelect)
    }
}

/** #105 D5 — day detail presentation: desktop expands inline under the row (the #91 single-route
 * lock), mobile shows a modal ([onClose] dismisses the mobile dialog). Shared content in
 * [FinanceDayDetailContent].
 */
@Composable
internal expect fun FinanceDayDetail(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    expanded: Boolean,
    onClose: () -> Unit,
)

@Composable
internal fun FinanceDayDetailContent(
    day: DailySalesSummaryResponse,
    today: LocalDate,
) {
    val state = derivedDayState(LocalDate.parse(day.date), today)
    Column(modifier = Modifier.padding(horizontal = Spacing.xs)) {
        Text(
            text = dayStateBannerText(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        BreakdownRow("Gross income (sessions)", day.grossIncome)
        BreakdownRow("Product sales", day.totalProductSales)
        BreakdownRow("Commission", day.totalCommission)
        BreakdownRow("Compensation", day.totalCompensation)
        BreakdownRow("Expenses", day.totalExpenses)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        BreakdownRow("Net", day.netIncome, strong = true)
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    strong: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (strong) MaterialTheme.colorScheme.onSurface else InkSubtle,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = peso(value),
            style = if (strong) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────── day editor (edit mode) ───────────────────────────

@Composable
private fun DayEditor(
    viewModel: FinanceReportsViewModel,
    day: DailySalesSummaryResponse,
    branchName: String,
    today: LocalDate,
    onBackToFeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = derivedDayState(LocalDate.parse(day.date), today)
    val expenses by viewModel.editExpenses.collectAsState()
    val compensations by viewModel.editCompensations.collectAsState()
    val allowances by viewModel.editAllowances.collectAsState()
    val users by viewModel.editUsers.collectAsState()
    val editErrors by viewModel.editErrors.collectAsState()
    val inFlight by viewModel.inFlightActions.collectAsState()

    LaunchedEffect(day.branchDayId) {
        viewModel.setEditMode(true)
    }

    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBackToFeed) { Text("← Feed") }
            Text(
                text = "${day.date} · $branchName",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        Text(
            text = dayStateBannerText(state),
            style = MaterialTheme.typography.bodySmall,
            color =
                if (state == DerivedDayState.PAST) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        // D2 — summary cards + breakdown
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SummaryCard("Gross income", day.grossIncome, Modifier.weight(1f))
            SummaryCard("Net income", day.netIncome, Modifier.weight(1f))
        }
        Surface(
            shape = RoundedCornerShape(CornerRadius.md),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(Spacing.sm)) {
                FinanceDayDetailContent(day = day, today = today)
            }
        }
        Spacer(Modifier.height(Spacing.sm))

        ExpenseSection(
            expenses = expenses,
            errors = editErrors,
            inFlight = inFlight,
            onCreate = { amount, category, notes, reason ->
                viewModel.createExpense(amount, category, notes, reason)
            },
            onUpdate = { expense, amount, category, notes, reason ->
                viewModel.updateExpense(expense, amount, category, notes, reason)
            },
            onDelete = { expense, reason -> viewModel.deleteExpense(expense, reason) },
            onRestore = { expense, reason -> viewModel.restoreExpense(expense, reason) },
            onReload = { viewModel.reloadSection(EditSection.EXPENSES) },
        )
        Spacer(Modifier.height(Spacing.sm))

        CompensationSection(
            compensations = compensations,
            users = users,
            errors = editErrors,
            inFlight = inFlight,
            onCreate = { userId, amount, note, reason ->
                viewModel.createCompensation(userId, amount, note, reason)
            },
            onUpdate = { compensation, amount, note, reason ->
                viewModel.updateCompensation(compensation, amount, note, reason)
            },
            onReload = { viewModel.reloadSection(EditSection.COMPENSATIONS) },
        )
        Spacer(Modifier.height(Spacing.sm))

        AllowanceSection(
            allowances = allowances,
            users = users,
            errors = editErrors,
            inFlight = inFlight,
            onCreate = { userId, amount, reason -> viewModel.createAllowance(userId, amount, reason) },
        )
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
            Text(
                text = peso(value),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

// ─────────────────────────── expense section ───────────────────────────

@Composable
private fun ExpenseSection(
    expenses: UiState<List<ExpenseResponse>>,
    errors: Map<String, String>,
    inFlight: Set<String>,
    onCreate: (String, String, String?, String?) -> Unit,
    onUpdate: (ExpenseResponse, String, String, String?, String?) -> Unit,
    onDelete: (ExpenseResponse, String) -> Unit,
    onRestore: (ExpenseResponse, String?) -> Unit,
    onReload: () -> Unit,
) {
    var showExpenseDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ExpenseResponse?>(null) }
    var deleting by remember { mutableStateOf<ExpenseResponse?>(null) }
    var restoring by remember { mutableStateOf<ExpenseResponse?>(null) }
    SectionHeader(
        title = "Expenses",
        actionLabel = "Add expense",
        onAction = { showExpenseDialog = true },
        showAction = true,
        onReload = onReload,
    )
    when (expenses) {
        is UiState.Idle -> {}

        is UiState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(Spacing.sm), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.width(Spacing.lg).height(Spacing.lg), strokeWidth = 2.dp)
            }
        }

        is UiState.Error -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = expenses.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onReload) { Text("Retry") }
            }
        }

        is UiState.Success -> {
            if (expenses.data.isEmpty()) {
                Text(
                    text = "No expenses logged for this day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                    modifier = Modifier.padding(Spacing.sm),
                )
            }
            expenses.data.forEach { expense ->
                ExpenseRow(
                    expense = expense,
                    error =
                        errors["expense:update:${expense.id}"]
                            ?: errors["expense:delete:${expense.id}"]
                            ?: errors["expense:restore:${expense.id}"],
                    busy =
                        "expense:update:${expense.id}" in inFlight ||
                            "expense:delete:${expense.id}" in inFlight ||
                            "expense:restore:${expense.id}" in inFlight,
                    onEdit = { editing = expense },
                    onDelete = { deleting = expense },
                    onRestore = { restoring = expense },
                )
            }
            if (errors["expense:create"] != null) {
                Text(
                    text = errors.getValue("expense:create"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(Spacing.xs),
                )
            }
        }
    }
    if (showExpenseDialog) {
        ExpenseDialog(
            title = "Log expense",
            initial = null,
            busy = "expense:create" in inFlight,
            error = errors["expense:create"],
            onConfirm = { amount, category, notes, reason -> onCreate(amount, category, notes, reason) },
            onDismiss = { showExpenseDialog = false },
        )
    }
    if (editing != null) {
        ExpenseDialog(
            title = "Edit expense",
            initial = editing,
            busy = "expense:update:${editing!!.id}" in inFlight,
            error = errors["expense:update:${editing!!.id}"],
            onConfirm = { amount, category, notes, reason -> onUpdate(editing!!, amount, category, notes, reason) },
            onDismiss = { editing = null },
        )
    }
    if (deleting != null) {
        ReasonDialog(
            title = "Delete expense?",
            message = "Deletion requires a reason.",
            requireReason = true,
            busy = "expense:delete:${deleting!!.id}" in inFlight,
            error = errors["expense:delete:${deleting!!.id}"],
            // requireReason=true guarantees the confirm never fires with null.
            onConfirm = { reason -> onDelete(deleting!!, reason ?: "") },
            onDismiss = { deleting = null },
        )
    }
    if (restoring != null) {
        ReasonDialog(
            title = "Restore expense?",
            message = "The row returns to full edit/delete affordances. A reason is required only on remitted days.",
            requireReason = false,
            busy = "expense:restore:${restoring!!.id}" in inFlight,
            error = errors["expense:restore:${restoring!!.id}"],
            onConfirm = { reason -> onRestore(restoring!!, reason) },
            onDismiss = { restoring = null },
        )
    }
}

@Composable
private fun ExpenseRow(
    expense: ExpenseResponse,
    error: String?,
    busy: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
) {
    val deleted = expense.deletedAt != null
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = expense.category,
            style = MaterialTheme.typography.bodySmall,
            color = if (deleted) InkSubtle else MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .width(140.dp)
                    .then(if (deleted) Modifier.background(MaterialTheme.colorScheme.surfaceVariant) else Modifier),
        )
        Text(
            text = peso(expense.amount),
            style = MaterialTheme.typography.bodySmall,
            color = if (deleted) InkSubtle else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = expense.notes ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (deleted) {
            Text(
                text = "removed",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(Spacing.xs))
            Text(
                text = expense.deletedReason ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
            Spacer(Modifier.width(Spacing.sm))
            TextButton(onClick = onRestore, enabled = !busy) { Text("Restore") }
        } else {
            TextButton(onClick = onEdit, enabled = !busy) { Text("Edit") }
            TextButton(onClick = onDelete, enabled = !busy) { Text("Delete") }
        }
    }
    if (error != null) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
        )
    }
}

@Composable
private fun ExpenseDialog(
    title: String,
    initial: ExpenseResponse?,
    busy: Boolean,
    error: String?,
    onConfirm: (String, String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(initial?.amount ?: "") }
    var categoryIndex by remember { mutableStateOf(expenseCategoryCodes.indexOf(initial?.category).coerceAtLeast(0)) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var reason by remember { mutableStateOf("") }
    val amountError = expenseAmountError(amount)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₱, positive)") },
                    singleLine = true,
                    isError = amountError != null,
                    supportingText = { if (amountError != null) Text(amountError) },
                )
                Spacer(Modifier.height(Spacing.xs))
                CategoryDropdown(
                    selected = expenseCategoryLabels[categoryIndex],
                    onSelected = { label -> categoryIndex = expenseCategoryLabels.indexOf(label).coerceAtLeast(0) },
                )
                Spacer(Modifier.height(Spacing.xs))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
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
                    if (amountError == null) {
                        onConfirm(
                            amount,
                            expenseCategoryCodes[categoryIndex],
                            notes.trim().ifBlank { null },
                            reason.trim().ifBlank { null },
                        )
                    }
                },
                enabled = !busy && amountError == null,
            ) {
                Text(if (busy) "Saving…" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    selected: String,
    onSelected: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            expenseCategoryLabels.forEach { label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(label)
                        open = false
                    },
                )
            }
        }
    }
}

// ─────────────────────────── compensation section ───────────────────────────

@Composable
private fun CompensationSection(
    compensations: UiState<List<CompensationResponse>>,
    users: UiState<List<BranchDayUserResponse>>,
    errors: Map<String, String>,
    inFlight: Set<String>,
    onCreate: (String, String, String?, String?) -> Unit,
    onUpdate: (CompensationResponse, String, String?, String?) -> Unit,
    onReload: () -> Unit,
) {
    var showAssign by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CompensationResponse?>(null) }
    SectionHeader(
        title = "Compensation",
        actionLabel = "Assign compensation",
        onAction = { showAssign = true },
        showAction = users is UiState.Success && users.data.isNotEmpty(),
        onReload = onReload,
    )
    when (compensations) {
        is UiState.Idle -> {}

        is UiState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(Spacing.sm), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.width(Spacing.lg).height(Spacing.lg), strokeWidth = 2.dp)
            }
        }

        is UiState.Error -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = compensations.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onReload) { Text("Retry") }
            }
        }

        is UiState.Success -> {
            if (compensations.data.isEmpty()) {
                Text(
                    text = "No compensation assigned for this day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                    modifier = Modifier.padding(Spacing.sm),
                )
            }
            compensations.data.forEach { compensation ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = compensation.userName ?: compensation.userId,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = peso(compensation.amount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = compensation.note ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = InkSubtle,
                        maxLines = 1,
                    )
                    TextButton(
                        onClick = { editing = compensation },
                        enabled = "comp:update:${compensation.id}" !in inFlight,
                    ) {
                        Text("Edit")
                    }
                }
                val error = errors["comp:update:${compensation.id}"]
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
                    )
                }
            }
        }
    }
    if (showAssign) {
        CompensationDialog(
            title = "Assign compensation",
            users = users,
            busy = "comp:create" in inFlight,
            error = errors["comp:create"],
            onConfirm = { userId, amount, note, reason -> onCreate(userId, amount, note, reason) },
            onDismiss = { showAssign = false },
        )
    }
    if (editing != null) {
        CompensationDialog(
            title = "Edit compensation",
            users = users,
            initial = editing,
            busy = "comp:update:${editing!!.id}" in inFlight,
            error = errors["comp:update:${editing!!.id}"],
            onConfirm = { userId, amount, note, reason -> onUpdate(editing!!, amount, note, reason) },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompensationDialog(
    title: String,
    users: UiState<List<BranchDayUserResponse>>,
    initial: CompensationResponse? = null,
    busy: Boolean,
    error: String?,
    onConfirm: (String, String, String?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var userId by remember {
        mutableStateOf(initial?.userId ?: (users as? UiState.Success)?.data?.firstOrNull()?.userId ?: "")
    }
    var amount by remember { mutableStateOf(initial?.amount ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var reason by remember { mutableStateOf("") }
    val amountError = compensationAmountError(amount)
    val usersList = (users as? UiState.Success)?.data.orEmpty()

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
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
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
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
                        onConfirm(userId, amount, note.trim().ifBlank { null }, reason.trim().ifBlank { null })
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

// ─────────────────────────── allowance section ───────────────────────────

@Composable
private fun AllowanceSection(
    allowances: UiState<List<AllowanceResponse>>,
    users: UiState<List<BranchDayUserResponse>>,
    errors: Map<String, String>,
    inFlight: Set<String>,
    onCreate: (String, String, String?) -> Unit,
) {
    var showAssign by remember { mutableStateOf(false) }
    SectionHeader(
        title = "Allowances — not in P&L",
        actionLabel = "Assign allowance",
        onAction = { showAssign = true },
        showAction = users is UiState.Success && users.data.isNotEmpty(),
        onReload = { },
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
            Text(
                text = allowances.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        is UiState.Success -> {
            if (allowances.data.isEmpty()) {
                Text(
                    text = "No allowances for this day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                    modifier = Modifier.padding(Spacing.sm),
                )
            }
            allowances.data.forEach { allowance ->
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
                        text = peso(allowance.amount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    if (showAssign) {
        AllowanceDialog(
            users = users,
            busy = "allow:create" in inFlight,
            error = errors["allow:create"],
            onConfirm = { userId, amount, reason -> onCreate(userId, amount, reason) },
            onDismiss = { showAssign = false },
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserDropdown(
    users: List<BranchDayUserResponse>,
    selectedUserId: String,
    onSelected: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val selectedName = users.firstOrNull { it.userId == selectedUserId }?.displayName ?: "Select user"
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("User (worked this day)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            users.forEach { user ->
                DropdownMenuItem(
                    text = { Text(user.displayName) },
                    onClick = {
                        onSelected(user.userId)
                        open = false
                    },
                )
            }
        }
    }
}

// ─────────────────────────── shared bits ───────────────────────────

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
    showAction: Boolean,
    onReload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.weight(1f))
        if (showAction) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun ReasonDialog(
    title: String,
    message: String,
    requireReason: Boolean,
    busy: Boolean,
    error: String?,
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    val reasonBlank = requireReason && reason.isBlank()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text(if (requireReason) "Reason" else "Reason (optional)") },
                    singleLine = true,
                    isError = reasonBlank,
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
                onClick = { onConfirm(reason.trim().ifBlank { null }) },
                enabled = !busy && !reasonBlank,
            ) {
                Text(if (busy) "Saving…" else "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") }
        },
    )
}

// ─────────────────────────── public reports (D2) ───────────────────────────

@Composable
private fun PublicReportsSection(
    exportErrors: Map<String, String>,
    onExport: (String, String) -> Unit,
) {
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Text(
            text = "Public reports",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Provincial Tour / Medical Mission — downloadable by any logged-in user (#128).",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
        )
        listOf("provincial" to "Provincial Tour", "medical-mission" to "Medical Mission").forEach { (kind, label) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                ExportButtons(
                    baseKey = "public:$kind",
                    onExport = { format -> onExport(kind, format) },
                    errors = exportErrors,
                )
            }
        }
    }
}

@Composable
internal fun ExportButtons(
    baseKey: String,
    onExport: (String) -> Unit,
    errors: Map<String, String>,
    downloads: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>> = emptyMap(),
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf("csv" to "CSV", "pdf" to "PDF").forEach { (format, label) ->
            val key = "$baseKey:$format"
            val state = downloads[key]
            TextButton(
                onClick = { onExport(format) },
                enabled = state !is UiState.Loading,
            ) {
                if (state is UiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(Spacing.sm).height(Spacing.sm),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(label)
                }
            }
            val error = errors[key]
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }
        }
    }
}
