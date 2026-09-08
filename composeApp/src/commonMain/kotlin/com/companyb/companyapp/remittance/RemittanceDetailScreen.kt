package com.companyb.companyapp.remittance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.remittance.AddDayBreakdownRequest
import com.companyb.companyapp.contracts.remittance.CreateRemittanceLineRequest
import com.companyb.companyapp.contracts.remittance.RemittanceDayBreakdownResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDayPickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDetailResponse
import com.companyb.companyapp.contracts.remittance.RemittanceDriftResponse
import com.companyb.companyapp.contracts.remittance.RemittanceFinancialSnapshotResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineResponse
import com.companyb.companyapp.contracts.remittance.RemittanceProductSalePickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceResponse
import com.companyb.companyapp.contracts.remittance.RemittanceSessionPickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceSubmitResponse
import com.companyb.companyapp.contracts.remittance.SubmitRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UndoRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UpdateRemittanceHeaderRequest
import com.companyb.companyapp.ui.ErrorCard
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius

/**
 * #120 — Remittance detail screen: the receipt, per the locked #103 D3–D10.
 *
 * - Pushed on both platforms (#113 ClientDetail precedent; content-level Back button).
 * - D3: lines — labels come from the #118 pickers' join data (bare-line F7), delete-only;
 *   tick-to-include add dialogs (amount prefilled from finalPrice/totalAmountAtTime, editable at
 *   add time); every add/remove is version-bumped server-side, a concurrent coordinator's edit →
 *   409 → reload + changed-elsewhere banner (ADR-0022).
 * - D4: days-covered picker from the #118 days endpoint — already-remitted (effective status
 *   REMITTED) greyed; removable via the #118 day-breakdown DELETE.
 * - D5: submit confirm (structural info + line total + "days lock, amounts freeze, undo within
 *   48h" copy) → on success the detail reloads and the frozen breakdown appears right away.
 * - D6: frozen-at-submission receipt block (SESSION-only snapshot; hairline card + lock glyph +
 *   muted "Frozen at submission" label — receipt styling is a design requirement) + lazy
 *   "Show current state" drift expander (#118 drift; gross never drifts; PRODUCT hides it).
 * - D9: header editable in place while DRAFT — the D2 popup prefilled, PATCH on save.
 * - D10: undo within 48h (reason required, single-line) — button hides client-side via
 *   submittedAt + clock (server-authoritative).
 */
@Composable
fun RemittanceDetailScreen(
    args: RemittanceDetailArgs,
    onBack: () -> Unit,
    // #447 — desk queue selection (desktop NavHost navigates; mobile keeps the default:
    // the queue rail only renders on wide desktop layouts, so narrow destinations and
    // non-desktop targets are untouched).
    onRemittanceClick: (String) -> Unit = {},
    // #447 — Variant B control desk engages only where the host opts in (desktop) AND
    // the window is wide enough for queue + editor + brief side by side.
    deskEnabled: Boolean = false,
) {
    LaunchedEffect(args.remittanceId) {
        logInfo("RemittanceDetailScreen", "composable entered: remittanceId=${args.remittanceId}")
        args.viewModel.loadRemittance(args.remittanceId)
    }

    // #447 — status/header mutations move rows between queue sections; refresh the desk
    // mirrors so the rails stop showing submitted items as drafts (desk hosts only —
    // classic surfaces never load the mirrors, so this is a no-op for them).
    fun refreshDeskQueue() {
        if (deskEnabled && args.branchId != null) {
            args.viewModel.loadRemittances(args.branchId, DESK_QUEUE_DRAFTS_STATUS)
            args.viewModel.loadRemittances(args.branchId, DESK_QUEUE_SUBMITTED_STATUS)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // #447 — Variant B control desk renders on opted-in hosts (desktop) with a wide
        // window only; everywhere else keeps the classic single column. The queue
        // prefetch is desk-gated so other surfaces issue no extra reads.
        val wideDesk = deskEnabled && maxWidth >= RemittanceLayoutPolicy.queueBreakpoint
        RemittanceDetailDeskPrefetchEffects(
            wideDesk = wideDesk,
            branchId = args.branchId,
            viewModel = args.viewModel,
        )

        RemittanceDetailScreenBody(
            args = args,
            onBack = onBack,
            onRemittanceClick = onRemittanceClick,
            wideDesk = wideDesk,
            onRefreshQueue = ::refreshDeskQueue,
        )
    }
}

@Composable
private fun RemittanceDetailScreenBody(
    args: RemittanceDetailArgs,
    onBack: () -> Unit,
    onRemittanceClick: (String) -> Unit,
    wideDesk: Boolean,
    onRefreshQueue: () -> Unit,
) {
    val detailState by args.viewModel.remittanceDetail.collectAsState()
    val lastDetail by args.viewModel.lastDetail.collectAsState()
    val forbidden by args.viewModel.detailForbidden.collectAsState()
    // #677 — dialog flags live above the detail state branch: a post-mutation detail
    // reload must not unmount the working picker (selection/scroll/queue survive).
    val dialogs = remember { RemittanceDetailDialogState() }

    // #447 — the range the picker caches were loaded for; a header range edit invalidates
    // them (stale Success caches would offer the old range's sessions, sales, and days).
    var pickerRange by remember { mutableStateOf<Pair<String, String>?>(null) }

    RemittanceDetailPickerLoadEffects(
        branchId = args.branchId,
        pickerRange = pickerRange,
        onRangeChange = { pickerRange = it },
        viewModel = args.viewModel,
    )

    RemittanceDetailLineDayEffects(
        viewModel = args.viewModel,
        remittanceId = args.remittanceId,
    )
    RemittanceDetailHeaderSubmitUndoEffects(
        args = args,
        dialogs = dialogs,
        onRefreshQueue = onRefreshQueue,
    )

    // #677 — last-good workspace: the freshest Success payload renders through reloads
    // (lines/days/footer retained, no spinner flash); dialogs mount below on it.
    val liveDetail = (detailState as? UiState.Success)?.data
    val effectiveDetail = liveDetail ?: lastDetail

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.md),
    ) {
        RemittanceDetailBackRow(onBack = onBack)
        RemittanceChangedNotice(viewModel = args.viewModel)

        // #677 — access loss owns the surface: no stale workspace, no spinner, no
        // retry into another 403 — just the way back.
        if (forbidden) {
            RemittanceAccessLost(onBack = onBack)
        } else {
            // #677 — a failed reload over a retained workspace surfaces a compact retry
            // instead of silently presenting stale-as-fresh.
            if (detailState is UiState.Error && effectiveDetail != null) {
                RemittanceStaleRetry(
                    message = (detailState as UiState.Error).message,
                    onRetry = { args.viewModel.loadRemittance(args.remittanceId) },
                )
            }

            when {
                effectiveDetail != null -> {
                    RemittanceDetailWorkspaceHost(
                        args = args,
                        detail = effectiveDetail,
                        wideDesk = wideDesk,
                        onRemittanceClick = onRemittanceClick,
                        onRefreshQueue = onRefreshQueue,
                        dialogs = dialogs,
                    )
                }

                detailState is UiState.Error -> {
                    RemittanceDetailLoadError(
                        message = (detailState as UiState.Error).message,
                        onRetry = { args.viewModel.loadRemittance(args.remittanceId) },
                    )
                }

                else -> {
                    RemittanceDetailLoadingBox()
                }
            }
        }
    }

    // #677 — picker/review/undo dialogs mount independently of detail reloads on the
    // last-good payload: adding entries never dismisses the working picker.
    effectiveDetail?.let { detail ->
        RemittanceDetailMountedDialogs(
            args = args,
            detail = detail,
            dialogs = dialogs,
        )
    }
}

@Composable
private fun RemittanceDetailBackRow(onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) {
            Text("Back")
        }
    }
}

@Composable
private fun RemittanceDetailLoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun RemittanceDetailLoadError(
    message: String,
    onRetry: () -> Unit,
) {
    ErrorCard(message = message, onRetry = onRetry)
}

/** #677 — access-loss terminal: the grant is gone, so the workspace (and its picker
 * caches) is cleared and this card replaces it. No retry — it would 403 again. */
@Composable
private fun RemittanceAccessLost(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "No longer available",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Access to this remittance changed. Your unsent edits were discarded.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onBack) {
            Text("Back")
        }
    }
}

/** #677 — compact retry over a retained workspace: the reload failed, the data is stale. */
@Composable
private fun RemittanceStaleRetry(
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "Couldn't refresh — showing last loaded data. $message",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun RemittanceChangedNotice(viewModel: RemittanceViewModel) {
    val changedNotice by viewModel.detailChangedNotice.collectAsState()
    if (changedNotice) {
        Text(
            text = "Remittance was changed elsewhere — changes reloaded",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
    }
}

@Composable
private fun RemittanceDetailWorkspaceHost(
    args: RemittanceDetailArgs,
    detail: RemittanceDetailResponse,
    wideDesk: Boolean,
    onRemittanceClick: (String) -> Unit,
    onRefreshQueue: () -> Unit,
    dialogs: RemittanceDetailDialogState,
) {
    // #677 — queue + one workspace side by side on wide desks; the full-width
    // workspace alone below the breakpoint (the list route is the queue there —
    // Back restores its tab/scroll structurally via the back stack).
    if (wideDesk && args.branchId != null) {
        val queueState by args.viewModel.remittanceList.collectAsState()
        val queueMirrors by args.viewModel.lastByTab.collectAsState()
        RemittanceQueueWorkspace(
            desk =
                RemittanceDeskState(
                    branchId = args.branchId,
                    currentId = args.remittanceId,
                    mirrors = queueMirrors,
                    queueState = queueState,
                    onQueueClick = onRemittanceClick,
                    onRetryQueue = onRefreshQueue,
                ),
            workspace = { RemittanceDetailWorkspace(args = args, detail = detail, dialogs = dialogs) },
        )
    } else {
        RemittanceDetailWorkspace(args = args, detail = detail, dialogs = dialogs)
    }
}

@Composable
private fun RemittanceDetailLineDayEffects(
    viewModel: RemittanceViewModel,
    remittanceId: String,
) {
    val lineState by viewModel.lineResult.collectAsState()
    val deleteLineState by viewModel.deleteLineResult.collectAsState()
    val dayBreakdownState by viewModel.dayBreakdownResult.collectAsState()
    val deleteDayBreakdownState by viewModel.dayBreakdownDeleteResult.collectAsState()
    // ADR-0022 — every successful mutation reloads the detail (version bumped server-side; the
    // next version-locked call must carry the fresh expectedVersion).
    LaunchedEffect(lineState) {
        if (lineState is UiState.Success) {
            viewModel.reloadDetail(remittanceId)
        }
    }
    LaunchedEffect(deleteLineState) {
        if (deleteLineState is UiState.Success) {
            viewModel.reloadDetail(remittanceId)
        }
    }
    LaunchedEffect(dayBreakdownState) {
        if (dayBreakdownState is UiState.Success) {
            viewModel.reloadDetail(remittanceId)
        }
    }
    LaunchedEffect(deleteDayBreakdownState) {
        if (deleteDayBreakdownState is UiState.Success) {
            viewModel.reloadDetail(remittanceId)
        }
    }
}

@Composable
private fun RemittanceDetailHeaderSubmitUndoEffects(
    args: RemittanceDetailArgs,
    dialogs: RemittanceDetailDialogState,
    onRefreshQueue: () -> Unit,
) {
    val headerUpdateState by args.viewModel.headerUpdateResult.collectAsState()
    val submitState by args.viewModel.submitResult.collectAsState()
    val undoState by args.viewModel.undoResult.collectAsState()
    LaunchedEffect(headerUpdateState) {
        when (val state = headerUpdateState) {
            is UiState.Success -> {
                dialogs.header = false
                args.viewModel.reloadDetail(args.remittanceId)
                onRefreshQueue()
            }

            is UiState.Error -> {
                logWarn("RemittanceDetailScreen", "headerUpdateState=Error: ${state.message}")
            }

            else -> {
                Unit
            }
        }
    }
    LaunchedEffect(submitState) {
        when (val state = submitState) {
            is UiState.Success -> {
                dialogs.submit = false
                // D5 — the frozen breakdown appears right away (the reloaded detail carries the
                // snapshot block).
                args.viewModel.reloadDetail(args.remittanceId)
                onRefreshQueue()
            }

            is UiState.Error -> {
                logWarn("RemittanceDetailScreen", "submitState=Error: ${state.message}")
            }

            else -> {
                Unit
            }
        }
    }
    LaunchedEffect(undoState) {
        when (val state = undoState) {
            is UiState.Success -> {
                dialogs.undo = false
                args.viewModel.reloadDetail(args.remittanceId)
                onRefreshQueue()
            }

            is UiState.Error -> {
                logWarn("RemittanceDetailScreen", "undoState=Error: ${state.message}")
            }

            else -> {
                Unit
            }
        }
    }
}

@Composable
private fun RemittanceDetailPickerLoadEffects(
    branchId: String?,
    pickerRange: Pair<String, String>?,
    onRangeChange: (Pair<String, String>?) -> Unit,
    viewModel: RemittanceViewModel,
) {
    val detailState by viewModel.remittanceDetail.collectAsState()
    val sessionPickerState by viewModel.sessionPicker.collectAsState()
    val productSalePickerState by viewModel.productSalePicker.collectAsState()
    val dayPickerState by viewModel.dayPicker.collectAsState()
    // D6 — line/day labels come from the pickers' join data (F7 bare lines): load the three
    // pickers once the detail's range is known; later detail reloads skip (pickers are cached).
    // A failed picker load (Error) retries on the next detail reload — the dialog's Retry is
    // the immediate path.
    LaunchedEffect(detailState) {
        val detail = (detailState as? UiState.Success)?.data ?: return@LaunchedEffect
        if (branchId == null) return@LaunchedEffect
        val range = detail.dateRangeStart to detail.dateRangeEnd
        if (pickerRange != null && pickerRange != range) {
            viewModel.loadSessionPicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
            viewModel.loadProductSalePicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
            viewModel.loadDayPicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
            onRangeChange(range)
            return@LaunchedEffect
        }
        onRangeChange(range)
        val needsLoad: (UiState<*>) -> Boolean = { it is UiState.Idle || it is UiState.Error }
        if (needsLoad(sessionPickerState)) {
            viewModel.loadSessionPicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
        }
        if (needsLoad(productSalePickerState)) {
            viewModel.loadProductSalePicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
        }
        if (needsLoad(dayPickerState)) {
            viewModel.loadDayPicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
        }
    }
}

@Composable
private fun RemittanceDetailDeskPrefetchEffects(
    wideDesk: Boolean,
    branchId: String?,
    viewModel: RemittanceViewModel,
) {
    val queueState by viewModel.remittanceList.collectAsState()
    LaunchedEffect(wideDesk, branchId) {
        if (wideDesk && branchId != null) {
            logInfo("RemittanceDetailScreen", "desk queue prefetch for branch $branchId")
            viewModel.loadRemittances(branchId, DESK_QUEUE_DRAFTS_STATUS)
            viewModel.loadRemittances(branchId, DESK_QUEUE_SUBMITTED_STATUS)
        }
    }
    LaunchedEffect(queueState) {
        (queueState as? UiState.Error)?.let {
            logWarn("RemittanceDetailScreen", "queueState=Error: ${it.message}")
        }
    }
}
