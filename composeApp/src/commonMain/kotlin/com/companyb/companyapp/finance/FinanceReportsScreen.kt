package com.companyb.companyapp.finance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.companyb.companyapp.app.AppSessionState
import com.companyb.companyapp.app.hasCapabilityAnyContext
import com.companyb.companyapp.app.hasDayGrant
import com.companyb.companyapp.app.navigation.NavigationContextStore
import com.companyb.companyapp.app.navigation.Route
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.branch.BranchResponse
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryResponse
import com.companyb.companyapp.contracts.reporting.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.saveDownload
import com.companyb.companyapp.workforce.relief.currentOperationalDate
import kotlinx.datetime.LocalDate
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
 *
 * #479 — the screen body split across seam files: [FinanceReportsBody] (main/relief dispatch
 * + relief surface), FinanceToolbar (toolbar + params), FinanceFeed, FinanceDayDetail,
 * FinanceDayEditor, FinanceExpense/FinanceExpenseDialog, FinanceCompensation, FinanceAllowance,
 * FinanceShared — all behind the [FinanceReportsCollected] carrier.
 */
@Composable
fun FinanceReportsScreen(
    viewModel: FinanceReportsViewModel,
    modifier: Modifier = Modifier,
) {
    val collected = rememberFinanceReportsCollected(viewModel)
    var downloadNote by remember { mutableStateOf<String?>(null) }
    var showReliefSection by remember { mutableStateOf(collected.reliefOnly) }
    val relief = FinanceReliefVisibility(show = showReliefSection, onChange = { showReliefSection = it })
    FinanceReportsScreenEffects(
        viewModel = viewModel,
        reliefOnly = collected.reliefOnly,
        downloads = collected.downloads,
        onDownloadNote = { downloadNote = it },
    )
    FinanceAppliedContextEffects(collected = collected)
    FinanceReportsDownloadNote(downloadNote)

    Column(modifier = modifier.fillMaxSize().padding(Spacing.md)) {
        FinanceReportsBody(
            viewModel = viewModel,
            collected = collected,
            relief = relief,
        )

        Spacer(Modifier.height(Spacing.md))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        PublicReportsSection(
            export = collected.export,
            onExport = viewModel::exportPublic,
        )
    }
}

@Composable
private fun rememberFinanceReportsCollected(viewModel: FinanceReportsViewModel): FinanceReportsCollected {
    val branches by viewModel.branches.collectAsState()
    val selectedBranchId by viewModel.selectedBranchId.collectAsState()
    val mode by viewModel.mode.collectAsState()
    val monthInput by viewModel.monthInput.collectAsState()
    val rangeFromInput by viewModel.rangeFromInput.collectAsState()
    val rangeToInput by viewModel.rangeToInput.collectAsState()
    val appliedRange by viewModel.appliedRange.collectAsState()
    val appliedMonth by viewModel.appliedMonth.collectAsState()
    val jumpMonth by viewModel.jumpMonth.collectAsState()
    val paramError by viewModel.paramError.collectAsState()
    val monthlyRollup by viewModel.monthlyRollup.collectAsState()
    val feed by viewModel.feedEntries.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val editMode by viewModel.editMode.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val exportErrors by viewModel.exportErrors.collectAsState()
    val snapshot by AppSessionState.snapshot.collectAsState()
    val capabilities = snapshot.capabilities
    // #698 — operational date (04:00 Asia/Manila), not calendar date — mirrors the VM.
    val today = currentOperationalDate(Clock.System.now())
    // #158 — a BRANCH_DAY grant holder's day-scoped surface. Relief-only users (no
    // VIEW_BRANCH_DATA) get it directly; hybrid users (VIEW elsewhere + a day grant at
    // a branch the picker never lists — the #98 window is BRANCH-grant-only) reach it
    // via the Relief-day chip (pass-1 triage: the picker can't reach the relief branch).
    val hasDayGrant = capabilities.hasDayGrant(CapabilityCodes.EDIT_BRANCH_DATA)
    val reliefOnly = hasDayGrant && !capabilities.hasCapabilityAnyContext(CapabilityCodes.VIEW_BRANCH_DATA)
    return FinanceReportsCollected(
        branches = branches,
        selectedBranchId = selectedBranchId,
        mode = mode,
        monthInput = monthInput,
        rangeFromInput = rangeFromInput,
        rangeToInput = rangeToInput,
        appliedRange = appliedRange,
        appliedMonth = appliedMonth.toString(),
        jumpMonth = jumpMonth?.toString(),
        paramError = paramError,
        monthlyRollup = monthlyRollup,
        feed = feed,
        selectedDay = selectedDay,
        editMode = editMode,
        isRefreshing = isRefreshing,
        downloads = downloads,
        exportErrors = exportErrors,
        export = FinanceExportUi(downloads = downloads, exportErrors = exportErrors),
        capabilities = capabilities,
        today = today,
        hasDayGrant = hasDayGrant,
        reliefOnly = reliefOnly,
    )
}

/** #479 LPL burn — the Finance & Reports screen's collected state, passed through the
 * seam files as one object (data-class constructors are LPL-free). */
internal data class FinanceReportsCollected(
    val branches: UiState<List<BranchResponse>>,
    val selectedBranchId: String?,
    val mode: ReportMode,
    val monthInput: String,
    val rangeFromInput: String,
    val rangeToInput: String,
    val appliedRange: Pair<String, String>?,
    val appliedMonth: String,
    val jumpMonth: String?,
    val paramError: String?,
    val monthlyRollup: UiState<MonthlyRemittanceSummaryResponse?>,
    val feed: UiState<List<DailySalesSummaryResponse>>,
    val selectedDay: DailySalesSummaryResponse?,
    val editMode: Boolean,
    val isRefreshing: Boolean,
    val downloads: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>>,
    val exportErrors: Map<String, String>,
    val export: FinanceExportUi,
    val capabilities: List<UserCapabilityResponse>,
    val today: LocalDate,
    val hasDayGrant: Boolean,
    val reliefOnly: Boolean,
)

@Composable
private fun FinanceReportsScreenEffects(
    viewModel: FinanceReportsViewModel,
    reliefOnly: Boolean,
    downloads: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>>,
    onDownloadNote: (String?) -> Unit,
) {
    LaunchedEffect(Unit) {
        // #158 pass-1 SOFT — the relief-only surface never renders the picker, and the
        // accessible-branches list is BRANCH-grant-only (empty for a relief delegate):
        // the fetch would be dead work.
        if (!reliefOnly) {
            // #728 — stage the retained applied scope before the first load so the
            // restore lands in one load (default-then-restored would flash + double-fetch).
            // Drafts mirror applied on restore; edit mode/dialogs/errors never stage.
            val snapshot = AppSessionState.snapshot.value
            val retained =
                NavigationContextStore.retained(snapshot.user?.id, snapshot.clock?.branchId, Route.Finance)
            if (retained != null && hasStoredFinanceScope(retained)) {
                viewModel.stageFinanceRestore(
                    FinanceRestoreRequest(
                        branchId = retained.financeBranchId,
                        modeName = retained.financeMode,
                        month = retained.financeMonth,
                        rangeFrom = retained.financeRangeFrom,
                        rangeTo = retained.financeRangeTo,
                        jumpMonth = retained.financeJumpMonth,
                    ),
                )
            }
            viewModel.loadBranches()
        }
    }
    // D6 — the platform save boundary: a successful export payload is handed to saveDownload
    // (desktop save dialog / Android Downloads), then consumed so the button clears. A false
    // return (user cancelled the dialog / the write failed) surfaces as an in-place note.
    LaunchedEffect(downloads) {
        // A fresh export clears a stale note (a cancelled export's note must not outlive the
        // user's next attempt — pass-3 SOFT).
        if (downloads.values.any { it is UiState.Loading }) onDownloadNote(null)
        downloads
            .filterValues { it is UiState.Success }
            .forEach { (key, state) ->
                // SAFETY: filtered by `is UiState.Success` above; delegated map values don't smart-cast #467
                val payload = (state as UiState.Success<FinanceReportsViewModel.DownloadPayload>).data
                if (saveDownload(payload.fileName, payload.bytes)) {
                    onDownloadNote(null)
                } else {
                    onDownloadNote("Download cancelled or failed for ${payload.fileName}")
                }
                viewModel.consumeDownload(key)
            }
    }
}

/**
 * #728 — stored applied scope probe (the ComplexCondition burn): a retained slot counts
 * as restorable when any applied leg is present. List-form so the call site stays a
 * single null-check plus one predicate.
 */
private fun hasStoredFinanceScope(retained: NavigationContextStore.SectionContext?): Boolean {
    if (retained == null) return false
    return listOf(
        retained.financeBranchId,
        retained.financeMode,
        retained.financeMonth,
        retained.financeRangeFrom,
        retained.financeRangeTo,
        retained.financeJumpMonth,
    ).any { it != null }
}

@Composable
private fun FinanceReportsDownloadNote(note: String?) {
    if (note != null) {
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * #728 - retains the applied Finance browsing scope on every applied change (branch,
 * mode, month, range, jump). Guarded by branches Success (branch access must be known
 * before anything is trusted) and reliefOnly (the relief surface owns no report scope).
 * Draft invalid text is entry-local and never retained; only applied scope lands here.
 * Null user/clock legs fail closed via the store. The selected day, list anchor, and
 * Rows/Cards choice ride FeedSection (they need the feed/list chrome there).
 */
@Composable
private fun FinanceAppliedContextEffects(collected: FinanceReportsCollected) {
    val snapshot by AppSessionState.snapshot.collectAsState()
    val userId = snapshot.user?.id
    val clockBranchId = snapshot.clock?.branchId
    val branchesReady = collected.branches is UiState.Success
    LaunchedEffect(
        collected.selectedBranchId,
        collected.mode,
        collected.appliedMonth,
        collected.appliedRange,
        collected.jumpMonth,
        branchesReady,
        collected.reliefOnly,
        userId,
        clockBranchId,
    ) {
        if (collected.reliefOnly || !branchesReady) return@LaunchedEffect
        if (userId == null || clockBranchId == null) return@LaunchedEffect
        val branchId = collected.selectedBranchId ?: return@LaunchedEffect
        NavigationContextStore.retain(
            userId,
            clockBranchId,
            Route.Finance,
            selectedId = null,
            financeBranchId = branchId,
            financeMode = collected.mode.name,
            financeMonth = collected.appliedMonth,
            financeRangeFrom = collected.appliedRange?.first ?: "",
            financeRangeTo = collected.appliedRange?.second ?: "",
            financeJumpMonth = collected.jumpMonth ?: "",
        )
    }
}
