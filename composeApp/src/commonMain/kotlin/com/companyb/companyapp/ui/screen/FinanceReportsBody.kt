package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.state.SessionState
import com.companyb.companyapp.state.hasCapability
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.FinanceReportsViewModel
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.applyJump
import com.companyb.companyapp.viewmodel.applyMonth
import com.companyb.companyapp.viewmodel.applyRange
import com.companyb.companyapp.viewmodel.clearJump
import com.companyb.companyapp.viewmodel.clearRange
import com.companyb.companyapp.viewmodel.clearReliefState
import com.companyb.companyapp.viewmodel.exportDay
import com.companyb.companyapp.viewmodel.exportModeCurrent
import com.companyb.companyapp.viewmodel.hasEditCapabilities
import com.companyb.companyapp.viewmodel.loadReliefDay
import com.companyb.companyapp.viewmodel.setEditMode
import com.companyb.companyapp.viewmodel.setJumpInput
import com.companyb.companyapp.viewmodel.setMode
import com.companyb.companyapp.viewmodel.setMonthInput
import com.companyb.companyapp.viewmodel.setRangeInputs
import kotlinx.datetime.LocalDate

/** #479 LPL burn — the relief/main surface switch as one object. */
internal data class FinanceReliefVisibility(
    val show: Boolean,
    val onChange: (Boolean) -> Unit,
)

/** #479 LPL burn — the relief surface's shared context as one object. */
internal data class ReliefDayUi(
    val selectedDay: DailySalesSummaryResponse?,
    val today: LocalDate,
    val capabilities: List<UserCapabilityResponse>,
    val editMode: Boolean,
    val onEditToggle: () -> Unit,
    /** #158 pass-1 — hybrid holders (day grant + VIEW elsewhere) enter via the Relief-day
     * chip; the exit affordance returns them to the reports surface. Null for relief-only
     * users (no surface to return to). */
    val onExit: (() -> Unit)?,
    val downloads: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>>,
    val exportErrors: Map<String, String>,
)

/** #479 LPL burn — the relief date input's state as one object. */
internal data class ReliefDayInputState(
    val dateInput: String,
    val onDateInputChange: (String) -> Unit,
    val dateError: String?,
    val onSubmit: () -> Unit,
)

@Composable
internal fun ColumnScope.FinanceReportsBody(
    viewModel: FinanceReportsViewModel,
    collected: FinanceReportsCollected,
    relief: FinanceReliefVisibility,
) {
    if (relief.show) {
        FinanceReportsReliefContent(viewModel = viewModel, collected = collected, relief = relief)
    } else {
        FinanceReportsMainContent(viewModel = viewModel, collected = collected, relief = relief)
    }
}

@Composable
internal fun ColumnScope.FinanceReportsReliefContent(
    viewModel: FinanceReportsViewModel,
    collected: FinanceReportsCollected,
    relief: FinanceReliefVisibility,
) {
    val reliefDay by viewModel.reliefDay.collectAsState()
    ReliefDaySection(
        viewModel = viewModel,
        reliefDay = reliefDay,
        ui =
            ReliefDayUi(
                selectedDay = collected.selectedDay,
                today = collected.today,
                capabilities = collected.capabilities,
                editMode = collected.editMode,
                onEditToggle = { viewModel.setEditMode(!collected.editMode) },
                onExit =
                    if (collected.reliefOnly) {
                        null
                    } else {
                        {
                            // Pass-2 HARD — leave the relief surface clean: a re-entry via
                            // the chip must not find the previous relief day armed (stale
                            // date input vs old Success). clearReliefState subsumes the
                            // edit-mode reset (pass-3 — no double clear).
                            viewModel.clearReliefState()
                            relief.onChange(false)
                        }
                    },
                downloads = collected.downloads,
                exportErrors = collected.exportErrors,
            ),
        modifier = Modifier.weight(1f),
    )
}

@Composable
internal fun ColumnScope.FinanceReportsMainContent(
    viewModel: FinanceReportsViewModel,
    collected: FinanceReportsCollected,
    relief: FinanceReliefVisibility,
) {
    val day = collected.selectedDay
    // #101 D1/D3 — a past day the user cannot edit (no EDIT_PAST_DAY) offers nothing to
    // toggle into: the Edit toggle stays hidden (the backend 403 stays authoritative).
    val pastDayReadOnlySelection =
        day != null &&
            collected.selectedBranchId != null &&
            derivedDayState(LocalDate.parse(day.date), collected.today) == DerivedDayState.PAST &&
            !collected.capabilities.hasCapability(
                CapabilityCodes.EDIT_PAST_DAY,
                CapabilityContextType.BRANCH,
                collected.selectedBranchId,
            )
    // #158 — hybrid holders (day grant + VIEW at a picker-listed branch): the relief
    // day lives at a branch the #98 window never lists, so the picker can't reach it —
    // the chip switches the surface to the day-scoped entry. Pass-2 HARD — entering
    // mid-edit would leak the reports day's armed sections into the relief surface.
    if (collected.hasDayGrant && !collected.reliefOnly) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
        ) {
            FilterChip(
                selected = false,
                onClick = {
                    viewModel.setEditMode(false)
                    relief.onChange(true)
                },
                label = { Text("Relief day") },
            )
        }
    }
    FinanceToolbar(
        ui =
            FinanceToolbarUi(
                branches = collected.branches,
                selectedBranchId = collected.selectedBranchId,
                mode = collected.mode,
                editMode = collected.editMode,
                canEdit = viewModel.hasEditCapabilities() && collected.selectedDay != null && !pastDayReadOnlySelection,
                appliedRange = collected.appliedRange,
            ),
        actions =
            FinanceToolbarActions(
                onBranchSelected = viewModel::selectBranch,
                onRetryBranches = viewModel::loadBranches,
                onModeSelected = viewModel::setMode,
                onEditToggle = { viewModel.setEditMode(!collected.editMode) },
                onExportMode = { format -> viewModel.exportModeCurrent(format) },
                downloads = collected.downloads,
                exportErrors = collected.exportErrors,
            ),
    )
    if (!collected.editMode) {
        FinanceReportsParamRows(viewModel = viewModel, collected = collected)
    }
    FinanceReportsEditorOrFeed(viewModel = viewModel, collected = collected)
}

@Composable
private fun FinanceReportsParamRows(
    viewModel: FinanceReportsViewModel,
    collected: FinanceReportsCollected,
) {
    when (collected.mode) {
        ReportMode.MONTHLY -> {
            MonthParamRow(
                monthInput = collected.monthInput,
                onMonthInputChange = viewModel::setMonthInput,
                onApply = viewModel::applyMonth,
                paramError = collected.paramError,
            )
        }

        ReportMode.ALL_TIME -> {
            JumpParamRow(
                monthInput = collected.monthInput,
                onJumpInputChange = viewModel::setJumpInput,
                onApply = viewModel::applyJump,
                onClear = viewModel::clearJump,
                paramError = collected.paramError,
            )
        }

        ReportMode.DATE_RANGE -> {
            DateRangeParamRow(
                ui =
                    DateRangeParamUi(
                        fromInput = collected.rangeFromInput,
                        toInput = collected.rangeToInput,
                        onFromChange = { viewModel.setRangeInputs(it, collected.rangeToInput) },
                        onToChange = { viewModel.setRangeInputs(collected.rangeFromInput, it) },
                        applied = collected.appliedRange != null,
                    ),
                onApply = viewModel::applyRange,
                onClear = viewModel::clearRange,
                paramError = collected.paramError,
            )
        }

        ReportMode.DAILY -> {}
    }
}

@Composable
private fun ColumnScope.FinanceReportsEditorOrFeed(
    viewModel: FinanceReportsViewModel,
    collected: FinanceReportsCollected,
) {
    val day = collected.selectedDay
    val selectedBranch = collected.selectedBranchId
    when {
        collected.editMode && day != null && selectedBranch != null -> {
            DayEditor(
                viewModel = viewModel,
                day = day,
                edit =
                    DayEditorUi(
                        branchId = selectedBranch,
                        branchName =
                            (collected.branches as? UiState.Success)?.data.orEmpty().branchName(selectedBranch),
                        today = collected.today,
                        capabilities = collected.capabilities,
                        onBackToFeed = { viewModel.setEditMode(false) },
                        onExportDayEditor = { format -> viewModel.exportDay(day, selectedBranch, format) },
                        downloadStates = collected.downloads,
                        exportErrors = collected.exportErrors,
                    ),
                modifier = Modifier.weight(1f),
            )
        }

        else -> {
            FeedSection(
                viewModel = viewModel,
                collected = collected,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ─────────────────────────── relief day entry (#158) ───────────────────────────

/**
 * #158 — the day-scoped relief surface: a date field (default today) + the single-day
 * summary read (backend day-grant leg), then the shared day detail + editor with the
 * day-gated affordances. No branch picker (the relief branch is the clocked-in branch),
 * no feed browse, no mode tabs — the BRANCH_DAY grant covers one day.
 */
@Composable
private fun ReliefDaySection(
    viewModel: FinanceReportsViewModel,
    reliefDay: UiState<DailySalesSummaryResponse>,
    ui: ReliefDayUi,
    modifier: Modifier = Modifier,
) {
    var dateInput by remember { mutableStateOf(ui.today.toString()) }
    // Pass-1 SOFT — an unparseable date must not round-trip to the backend's 400.
    var dateError by remember { mutableStateOf<String?>(null) }
    // Pass-4 SOFT — the Load button and the ErrorCard retry share one guarded submit
    // (string-coupled parse guard + message must not drift).
    val submitDate: () -> Unit = {
        if (parseDateInput(dateInput.trim()) == null) {
            dateError = "Invalid date — use yyyy-MM-dd"
        } else {
            viewModel.loadReliefDay(dateInput.trim())
        }
    }
    Column(modifier = modifier.fillMaxWidth().padding(top = Spacing.sm)) {
        ReliefDayInputRow(
            viewModel = viewModel,
            reliefDay = reliefDay,
            input =
                ReliefDayInputState(
                    dateInput = dateInput,
                    onDateInputChange = {
                        dateInput = it
                        dateError = null
                    },
                    dateError = dateError,
                    onSubmit = submitDate,
                ),
            ui = ui,
        )
        ReliefDayResultContent(
            viewModel = viewModel,
            reliefDay = reliefDay,
            ui = ui,
            onRetry = submitDate,
        )
    }
}

@Composable
private fun ReliefDayInputRow(
    viewModel: FinanceReportsViewModel,
    reliefDay: UiState<DailySalesSummaryResponse>,
    input: ReliefDayInputState,
    ui: ReliefDayUi,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (ui.onExit != null) {
            TextButton(onClick = ui.onExit) { Text("← Reports") }
        }
        OutlinedTextField(
            value = input.dateInput,
            onValueChange = input.onDateInputChange,
            label = { Text("Date (yyyy-MM-dd)") },
            singleLine = true,
            isError = input.dateError != null,
            supportingText = input.dateError?.let { { Text(it) } },
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = input.onSubmit,
            enabled = reliefDay !is UiState.Loading,
        ) {
            if (reliefDay is UiState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.width(Spacing.sm).height(Spacing.sm),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("Load")
            }
        }
        if (ui.selectedDay != null && reliefDay is UiState.Success) {
            val pastDayReadOnly =
                derivedDayState(LocalDate.parse(ui.selectedDay.date), ui.today) == DerivedDayState.PAST &&
                    !ui.capabilities.hasCapability(
                        CapabilityCodes.EDIT_PAST_DAY,
                        CapabilityContextType.BRANCH,
                        SessionState.selectedBranchId.value,
                    )
            TextButton(
                onClick = ui.onEditToggle,
                enabled = viewModel.hasEditCapabilities() && !pastDayReadOnly,
            ) {
                Text(if (ui.editMode) "Done" else "Edit")
            }
        }
    }
}

@Composable
private fun ReliefDayResultContent(
    viewModel: FinanceReportsViewModel,
    reliefDay: UiState<DailySalesSummaryResponse>,
    ui: ReliefDayUi,
    onRetry: () -> Unit,
) {
    when (reliefDay) {
        is UiState.Idle -> {
            Box(Modifier.fillMaxWidth().padding(vertical = Spacing.md), contentAlignment = Alignment.Center) {
                Text(
                    text = "Enter a date to view your relief day",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSubtle,
                )
            }
        }

        is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            logWarn("FinanceReportsScreen", "reliefDay=Error: ${reliefDay.message}")
            ErrorCard(message = reliefDay.message, onRetry = onRetry)
        }

        is UiState.Success -> {
            val day = reliefDay.data
            if (ui.editMode) {
                DayEditor(
                    viewModel = viewModel,
                    day = day,
                    edit =
                        DayEditorUi(
                            branchId = SessionState.selectedBranchId.value ?: "",
                            branchName = SessionState.selectedBranchName.value ?: "",
                            today = ui.today,
                            capabilities = ui.capabilities,
                            onBackToFeed = ui.onEditToggle,
                            onExportDayEditor = null,
                            downloadStates = ui.downloads,
                            exportErrors = ui.exportErrors,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    FinanceDayDetailContent(
                        day = day,
                        today = ui.today,
                        onExportDay = null,
                    )
                }
            }
        }
    }
}
