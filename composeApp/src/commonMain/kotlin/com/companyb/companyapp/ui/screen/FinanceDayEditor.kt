package com.companyb.companyapp.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.domain.CapabilityContextType
import com.companyb.companyapp.dto.AllowanceResponse
import com.companyb.companyapp.dto.BranchDayUserResponse
import com.companyb.companyapp.dto.CompensationResponse
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.dto.ExpenseResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.state.hasBranchOrDayCapability
import com.companyb.companyapp.state.hasCapability
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.viewmodel.EditSection
import com.companyb.companyapp.viewmodel.FinanceReportsViewModel
import com.companyb.companyapp.viewmodel.createAllowance
import com.companyb.companyapp.viewmodel.createCompensation
import com.companyb.companyapp.viewmodel.createExpense
import com.companyb.companyapp.viewmodel.deleteExpense
import com.companyb.companyapp.viewmodel.reloadSection
import com.companyb.companyapp.viewmodel.restoreExpense
import com.companyb.companyapp.viewmodel.updateCompensation
import com.companyb.companyapp.viewmodel.updateExpense
import kotlinx.datetime.LocalDate

/** #479 LPL burn — the day editor's frame (branch identity, date, nav + export wiring). */
internal data class DayEditorUi(
    val branchId: String,
    val branchName: String,
    val today: LocalDate,
    val capabilities: List<UserCapabilityResponse>,
    val onBackToFeed: () -> Unit,
    /** #158 — null hides the per-day export row (relief mode: the backend export gate is
     * VIEW_BRANCH_DATA, which a BRANCH_DAY-only holder lacks — no affordance for a 403). */
    val onExportDayEditor: ((String) -> Unit)?,
    val downloadStates: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>>,
    val exportErrors: Map<String, String>,
)

/** #479 LPL burn — the assign blocks' section reads + gate as one object. */
internal data class DayEditorAssignUi(
    val compensations: UiState<List<CompensationResponse>>,
    val allowances: UiState<List<AllowanceResponse>>,
    val users: UiState<List<BranchDayUserResponse>>,
    val canAssign: Boolean,
)

@Composable
internal fun DayEditor(
    viewModel: FinanceReportsViewModel,
    day: DailySalesSummaryResponse,
    edit: DayEditorUi,
    modifier: Modifier = Modifier,
) {
    val gates = dayEditorGates(edit.capabilities, edit.branchId, day.branchDayId, day.date, edit.today)
    val expenses by viewModel.editExpenses.collectAsState()
    val compensations by viewModel.editCompensations.collectAsState()
    val allowances by viewModel.editAllowances.collectAsState()
    val users by viewModel.editUsers.collectAsState()
    val editErrors by viewModel.editErrors.collectAsState()
    val inFlight by viewModel.inFlightActions.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val sectionUi =
        FinanceSectionUi(
            errors = editErrors,
            inFlight = inFlight,
            readOnly = gates.pastDayReadOnly,
            conflicts = conflicts,
        )

    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        DayEditorSummary(day = day, edit = edit)

        if (gates.canEditExpenses) {
            DayEditorExpenseBlock(
                viewModel = viewModel,
                expenses = expenses,
                ui = sectionUi,
            )
            Spacer(Modifier.height(Spacing.sm))
        }

        if (gates.canAssign) {
            // #101 D1 matrix — compensation + allowances = ASSIGN_COMPENSATION (per-element
            // guard; a non-holder never sees the sections NOR their 403ing loads).
            DayEditorAssignBlock(
                viewModel = viewModel,
                assign =
                    DayEditorAssignUi(
                        compensations = compensations,
                        allowances = allowances,
                        users = users,
                        canAssign = gates.canAssign,
                    ),
                ui = sectionUi,
            )
        }
    }
}

private data class DayEditorGates(
    val canAssign: Boolean,
    val canEditExpenses: Boolean,
    val pastDayReadOnly: Boolean,
)

private fun dayEditorGates(
    capabilities: List<UserCapabilityResponse>,
    branchId: String,
    branchDayId: String,
    dateString: String,
    today: LocalDate,
): DayEditorGates {
    val state = derivedDayState(LocalDate.parse(dateString), today)
    // #156 — per-element gates are branch-scoped triples (matching the backend
    // `requireBranchCapability` gates; #101 D1 matrix). #158 — the expense leg ORs the
    // BRANCH_DAY relief grant for this day.
    val canAssign =
        capabilities.hasCapability(
            CapabilityCodes.ASSIGN_COMPENSATION,
            CapabilityContextType.BRANCH,
            branchId,
        )
    // #101 D1 matrix — expenses = EDIT_BRANCH_DATA (per-element guard; the #158 day leg).
    val canEditExpenses =
        capabilities.hasBranchOrDayCapability(
            CapabilityCodes.EDIT_BRANCH_DATA,
            branchId,
            branchDayId,
        )
    // #101 D1/D3 — past days are read-only unless the user holds EDIT_PAST_DAY at the branch
    // (the backend 403 stays authoritative).
    val pastDayReadOnly =
        state == DerivedDayState.PAST &&
            !capabilities.hasCapability(CapabilityCodes.EDIT_PAST_DAY, CapabilityContextType.BRANCH, branchId)
    return DayEditorGates(canAssign, canEditExpenses, pastDayReadOnly)
}

@Composable
private fun DayEditorSummary(
    day: DailySalesSummaryResponse,
    edit: DayEditorUi,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = edit.onBackToFeed) { Text("← Feed") }
        Text(
            text = "${day.date} · ${edit.branchName}",
            style = MaterialTheme.typography.titleLarge,
        )
    }

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
            FinanceDayDetailContent(
                day = day,
                today = edit.today,
                onExportDay = edit.onExportDayEditor,
                downloadStates = edit.downloadStates,
                exportErrors = edit.exportErrors,
            )
        }
    }
    Spacer(Modifier.height(Spacing.sm))
}

@Composable
private fun DayEditorExpenseBlock(
    viewModel: FinanceReportsViewModel,
    expenses: UiState<List<ExpenseResponse>>,
    ui: FinanceSectionUi,
) {
    ExpenseSection(
        viewModel = viewModel,
        expenses = expenses,
        ui = ui,
        actions =
            ExpenseActions(
                onCreate = { amount, category, notes, reason ->
                    viewModel.createExpense(amount, category, notes, reason)
                },
                onUpdate = { expense, amount, category, notes, reason ->
                    viewModel.updateExpense(expense, amount, category, notes, reason)
                },
                onDelete = { expense, reason -> viewModel.deleteExpense(expense, reason) },
                onRestore = { expense, reason -> viewModel.restoreExpense(expense, reason) },
            ),
        onReload = { viewModel.reloadSection(EditSection.EXPENSES) },
    )
}

@Composable
private fun DayEditorAssignBlock(
    viewModel: FinanceReportsViewModel,
    assign: DayEditorAssignUi,
    ui: FinanceSectionUi,
) {
    val assignUi =
        FinanceAssignUi(
            errors = ui.errors,
            inFlight = ui.inFlight,
            readOnly = ui.readOnly,
            conflicts = ui.conflicts,
            canAssign = assign.canAssign,
        )
    CompensationSection(
        viewModel = viewModel,
        compensations = assign.compensations,
        users = assign.users,
        ui = assignUi,
        actions =
            CompensationActions(
                onCreate = { userId, amount, note, reason ->
                    viewModel.createCompensation(userId, amount, note, reason)
                },
                onUpdate = { compensation, amount, note, reason ->
                    viewModel.updateCompensation(compensation, amount, note, reason)
                },
            ),
    )
    Spacer(Modifier.height(Spacing.sm))

    AllowanceSection(
        allowances = assign.allowances,
        users = assign.users,
        ui = assignUi,
        onCreate = { userId, amount, reason -> viewModel.createAllowance(userId, amount, reason) },
        onReload = { viewModel.reloadSection(EditSection.ALLOWANCES) },
    )
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
