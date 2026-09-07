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
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.domain.CapabilityCodes
import com.companyb.companyapp.dto.BranchResponse
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.dto.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.dto.UserCapabilityResponse
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.util.saveDownload
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
    val paramError by viewModel.paramError.collectAsState()
    val monthlyRollup by viewModel.monthlyRollup.collectAsState()
    val feed by viewModel.feedEntries.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val editMode by viewModel.editMode.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val exportErrors by viewModel.exportErrors.collectAsState()
    val snapshot by AppSessionState.snapshot.collectAsState()
    val capabilities = snapshot.capabilities
    val today =
        Clock.System
            .now()
            .toLocalDateTime(TimeZone.of("Asia/Manila"))
            .date
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
        paramError = paramError,
        monthlyRollup = monthlyRollup,
        feed = feed,
        selectedDay = selectedDay,
        editMode = editMode,
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
    val paramError: String?,
    val monthlyRollup: UiState<MonthlyRemittanceSummaryResponse?>,
    val feed: UiState<List<DailySalesSummaryResponse>>,
    val selectedDay: DailySalesSummaryResponse?,
    val editMode: Boolean,
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
