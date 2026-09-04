package com.companyb.companyapp.ui.screen

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
import com.companyb.companyapp.domain.RemittanceStatus
import com.companyb.companyapp.dto.AddDayBreakdownRequest
import com.companyb.companyapp.dto.CreateRemittanceLineRequest
import com.companyb.companyapp.dto.RemittanceDayBreakdownResponse
import com.companyb.companyapp.dto.RemittanceDayPickerEntryResponse
import com.companyb.companyapp.dto.RemittanceDetailResponse
import com.companyb.companyapp.dto.RemittanceDriftResponse
import com.companyb.companyapp.dto.RemittanceFinancialSnapshotResponse
import com.companyb.companyapp.dto.RemittanceLineResponse
import com.companyb.companyapp.dto.RemittanceProductSalePickerEntryResponse
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.dto.RemittanceSessionPickerEntryResponse
import com.companyb.companyapp.dto.RemittanceSubmitResponse
import com.companyb.companyapp.dto.SubmitRemittanceRequest
import com.companyb.companyapp.dto.UndoRemittanceRequest
import com.companyb.companyapp.dto.UpdateRemittanceHeaderRequest
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import com.companyb.companyapp.viewmodel.RemittanceViewModel
import com.companyb.companyapp.viewmodel.UiState
import com.companyb.companyapp.viewmodel.remittanceListKey
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
    remittanceId: String,
    branchId: String?,
    viewModel: RemittanceViewModel,
    onBack: () -> Unit,
    // #447 — desk queue selection (desktop NavHost navigates; mobile keeps the default:
    // the queue rail only renders on wide desktop layouts, so narrow destinations and
    // non-desktop targets are untouched).
    onRemittanceClick: (String) -> Unit = {},
    // #447 — Variant B control desk engages only where the host opts in (desktop) AND
    // the window is wide enough for queue + editor + brief side by side.
    deskEnabled: Boolean = false,
) {
    val detailState by viewModel.remittanceDetail.collectAsState()
    val changedNotice by viewModel.detailChangedNotice.collectAsState()

    var showHeaderDialog by remember { mutableStateOf(false) }
    var showSessionPicker by remember { mutableStateOf(false) }
    var showProductSalePicker by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }
    var showSubmitDialog by remember { mutableStateOf(false) }
    var showUndoDialog by remember { mutableStateOf(false) }
    // #447 — the range the picker caches were loaded for; a header range edit invalidates
    // them (stale Success caches would offer the old range's sessions, sales, and days).
    var pickerRange by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        logInfo("RemittanceDetailScreen", "composable entered: remittanceId=$remittanceId")
        viewModel.loadRemittance(remittanceId)
    }

    // #447 — status/header mutations move rows between queue sections; refresh the desk
    // mirrors so the rails stop showing submitted items as drafts (desk hosts only —
    // classic surfaces never load the mirrors, so this is a no-op for them).
    fun refreshDeskQueue() {
        if (deskEnabled && branchId != null) {
            viewModel.loadRemittances(branchId, DESK_QUEUE_DRAFTS_STATUS)
            viewModel.loadRemittances(branchId, DESK_QUEUE_SUBMITTED_STATUS)
        }
    }

    RemittanceDetailPickerLoadEffects(
        branchId = branchId,
        pickerRange = pickerRange,
        onRangeChange = { pickerRange = it },
        viewModel = viewModel,
    )

    RemittanceDetailLineDayEffects(
        viewModel = viewModel,
        remittanceId = remittanceId,
    )
    RemittanceDetailHeaderSubmitUndoEffects(
        viewModel = viewModel,
        remittanceId = remittanceId,
        onCloseHeaderDialog = { showHeaderDialog = false },
        onCloseSubmitDialog = { showSubmitDialog = false },
        onCloseUndoDialog = { showUndoDialog = false },
        onRefreshQueue = ::refreshDeskQueue,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // #447 — Variant B control desk renders on opted-in hosts (desktop) with a wide
        // window only; everywhere else keeps the classic single column. The queue
        // prefetch is desk-gated so other surfaces issue no extra reads.
        val wideDesk = deskEnabled && maxWidth >= CONTROL_DESK_MIN_WIDTH
        RemittanceDetailDeskPrefetchEffects(
            wideDesk = wideDesk,
            branchId = branchId,
            viewModel = viewModel,
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.md),
        ) {
            RemittanceDetailBackRow(onBack = onBack)

            when (val state = detailState) {
                is UiState.Idle, is UiState.Loading -> {
                    RemittanceDetailLoadingBox()
                }

                is UiState.Error -> {
                    RemittanceDetailLoadError(
                        message = state.message,
                        onRetry = { viewModel.loadRemittance(remittanceId) },
                    )
                }

                is UiState.Success -> {
                    @Composable
                    fun Center() {
                        val sessionPickerState by viewModel.sessionPicker.collectAsState()
                        val productSalePickerState by viewModel.productSalePicker.collectAsState()
                        val dayPickerState by viewModel.dayPicker.collectAsState()
                        val lineState by viewModel.lineResult.collectAsState()
                        val deleteLineState by viewModel.deleteLineResult.collectAsState()
                        val dayBreakdownState by viewModel.dayBreakdownResult.collectAsState()
                        val deleteDayBreakdownState by viewModel.dayBreakdownDeleteResult.collectAsState()
                        val submitState by viewModel.submitResult.collectAsState()
                        val undoState by viewModel.undoResult.collectAsState()
                        val headerUpdateState by viewModel.headerUpdateResult.collectAsState()
                        val driftState by viewModel.drift.collectAsState()
                        RemittanceDetailContent(
                            detail = state.data,
                            branchId = branchId,
                            remittanceId = remittanceId,
                            sessionPickerState = sessionPickerState,
                            productSalePickerState = productSalePickerState,
                            dayPickerState = dayPickerState,
                            lineState = lineState,
                            deleteLineState = deleteLineState,
                            dayBreakdownState = dayBreakdownState,
                            deleteDayBreakdownState = deleteDayBreakdownState,
                            submitState = submitState,
                            undoState = undoState,
                            headerUpdateState = headerUpdateState,
                            driftState = driftState,
                            viewModel = viewModel,
                            showHeaderDialog = showHeaderDialog,
                            showSessionPicker = showSessionPicker,
                            showProductSalePicker = showProductSalePicker,
                            showDayPicker = showDayPicker,
                            showSubmitDialog = showSubmitDialog,
                            showUndoDialog = showUndoDialog,
                            onOpenHeaderDialog = { showHeaderDialog = true },
                            onOpenSessionPicker = { showSessionPicker = true },
                            onOpenProductSalePicker = { showProductSalePicker = true },
                            onOpenDayPicker = { showDayPicker = true },
                            onOpenSubmitDialog = { showSubmitDialog = true },
                            onOpenUndoDialog = { showUndoDialog = true },
                            onCloseHeaderDialog = { showHeaderDialog = false },
                            onCloseSessionPicker = { showSessionPicker = false },
                            onCloseProductSalePicker = { showProductSalePicker = false },
                            onCloseDayPicker = { showDayPicker = false },
                            onCloseSubmitDialog = { showSubmitDialog = false },
                            onCloseUndoDialog = { showUndoDialog = false },
                        )
                    }
                    RemittanceDetailSuccessHost(
                        changedNotice = changedNotice,
                        detail = state.data,
                        branchId = branchId,
                        currentId = remittanceId,
                        wideDesk = wideDesk,
                        viewModel = viewModel,
                        onQueueClick = onRemittanceClick,
                        onRetryQueue = { refreshDeskQueue() },
                        center = { Center() },
                    )
                }
            }
        }
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

@Composable
private fun RemittanceDetailSuccessHost(
    changedNotice: Boolean,
    detail: RemittanceDetailResponse,
    branchId: String?,
    currentId: String,
    wideDesk: Boolean,
    viewModel: RemittanceViewModel,
    onQueueClick: (String) -> Unit,
    onRetryQueue: (String) -> Unit,
    center: @Composable () -> Unit,
) {
    val queueState by viewModel.remittanceList.collectAsState()
    val queueMirrors by viewModel.lastByTab.collectAsState()
    val dayPickerState by viewModel.dayPicker.collectAsState()
    if (changedNotice) {
        Text(
            text = "Remittance was changed elsewhere — changes reloaded",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = Spacing.sm),
        )
    }
    if (wideDesk && branchId != null) {
        RemittanceControlDesk(
            detail = detail,
            branchId = branchId,
            currentId = currentId,
            queueMirrors = queueMirrors,
            queueState = queueState,
            dayEntries =
                (dayPickerState as? UiState.Success)
                    ?.data
                    ?.associate { it.id to it }
                    .orEmpty(),
            onQueueClick = onQueueClick,
            onRetryQueue = { onRetryQueue(branchId) },
            center = center,
        )
    } else {
        center()
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
            viewModel.loadRemittance(remittanceId)
        }
    }
    LaunchedEffect(deleteLineState) {
        if (deleteLineState is UiState.Success) {
            viewModel.loadRemittance(remittanceId)
        }
    }
    LaunchedEffect(dayBreakdownState) {
        if (dayBreakdownState is UiState.Success) {
            viewModel.loadRemittance(remittanceId)
        }
    }
    LaunchedEffect(deleteDayBreakdownState) {
        if (deleteDayBreakdownState is UiState.Success) {
            viewModel.loadRemittance(remittanceId)
        }
    }
}

@Composable
private fun RemittanceDetailHeaderSubmitUndoEffects(
    viewModel: RemittanceViewModel,
    remittanceId: String,
    onCloseHeaderDialog: () -> Unit,
    onCloseSubmitDialog: () -> Unit,
    onCloseUndoDialog: () -> Unit,
    onRefreshQueue: () -> Unit,
) {
    val headerUpdateState by viewModel.headerUpdateResult.collectAsState()
    val submitState by viewModel.submitResult.collectAsState()
    val undoState by viewModel.undoResult.collectAsState()
    LaunchedEffect(headerUpdateState) {
        when (val state = headerUpdateState) {
            is UiState.Success -> {
                onCloseHeaderDialog()
                viewModel.loadRemittance(remittanceId)
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
                onCloseSubmitDialog()
                // D5 — the frozen breakdown appears right away (the reloaded detail carries the
                // snapshot block).
                viewModel.loadRemittance(remittanceId)
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
                onCloseUndoDialog()
                viewModel.loadRemittance(remittanceId)
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

@Composable
private fun RemittanceDetailContent(
    detail: RemittanceDetailResponse,
    branchId: String?,
    remittanceId: String,
    sessionPickerState: UiState<List<RemittanceSessionPickerEntryResponse>>,
    productSalePickerState: UiState<List<RemittanceProductSalePickerEntryResponse>>,
    dayPickerState: UiState<List<RemittanceDayPickerEntryResponse>>,
    lineState: UiState<RemittanceLineResponse>,
    deleteLineState: UiState<Unit>,
    dayBreakdownState: UiState<RemittanceDayBreakdownResponse>,
    deleteDayBreakdownState: UiState<Unit>,
    submitState: UiState<RemittanceSubmitResponse>,
    undoState: UiState<RemittanceResponse>,
    headerUpdateState: UiState<RemittanceResponse>,
    driftState: UiState<RemittanceDriftResponse>,
    viewModel: RemittanceViewModel,
    showHeaderDialog: Boolean,
    showSessionPicker: Boolean,
    showProductSalePicker: Boolean,
    showDayPicker: Boolean,
    showSubmitDialog: Boolean,
    showUndoDialog: Boolean,
    onOpenHeaderDialog: () -> Unit,
    onOpenSessionPicker: () -> Unit,
    onOpenProductSalePicker: () -> Unit,
    onOpenDayPicker: () -> Unit,
    onOpenSubmitDialog: () -> Unit,
    onOpenUndoDialog: () -> Unit,
    onCloseHeaderDialog: () -> Unit,
    onCloseSessionPicker: () -> Unit,
    onCloseProductSalePicker: () -> Unit,
    onCloseDayPicker: () -> Unit,
    onCloseSubmitDialog: () -> Unit,
    onCloseUndoDialog: () -> Unit,
) {
    val isDraft = detail.status == com.companyb.companyapp.domain.RemittanceStatus.DRAFT

    val dayLabels =
        (dayPickerState as? UiState.Success)
            ?.data
            ?.associate { it.id to it }
            .orEmpty()

    RemittanceDetailContentBody(
        detail = detail,
        sessionPickerState = sessionPickerState,
        productSalePickerState = productSalePickerState,
        dayLabels = dayLabels,
        isDraft = isDraft,
        deleteLineState = deleteLineState,
        deleteDayBreakdownState = deleteDayBreakdownState,
        driftState = driftState,
        viewModel = viewModel,
        remittanceId = remittanceId,
        onOpenHeaderDialog = onOpenHeaderDialog,
        onOpenSessionPicker = onOpenSessionPicker,
        onOpenProductSalePicker = onOpenProductSalePicker,
        onOpenDayPicker = onOpenDayPicker,
        onOpenSubmitDialog = onOpenSubmitDialog,
        onOpenUndoDialog = onOpenUndoDialog,
    )

    RemittanceDetailContentDialogs(
        detail = detail,
        branchId = branchId,
        sessionPickerState = sessionPickerState,
        productSalePickerState = productSalePickerState,
        dayPickerState = dayPickerState,
        lineState = lineState,
        dayBreakdownState = dayBreakdownState,
        submitState = submitState,
        undoState = undoState,
        headerUpdateState = headerUpdateState,
        dayLabels = dayLabels,
        viewModel = viewModel,
        remittanceId = remittanceId,
        showHeaderDialog = showHeaderDialog,
        showSessionPicker = showSessionPicker,
        showProductSalePicker = showProductSalePicker,
        showDayPicker = showDayPicker,
        showSubmitDialog = showSubmitDialog,
        showUndoDialog = showUndoDialog,
        onCloseHeaderDialog = onCloseHeaderDialog,
        onCloseSessionPicker = onCloseSessionPicker,
        onCloseProductSalePicker = onCloseProductSalePicker,
        onCloseDayPicker = onCloseDayPicker,
        onCloseSubmitDialog = onCloseSubmitDialog,
        onCloseUndoDialog = onCloseUndoDialog,
    )
}

@Composable
private fun RemittanceDetailContentBody(
    detail: RemittanceDetailResponse,
    sessionPickerState: UiState<List<RemittanceSessionPickerEntryResponse>>,
    productSalePickerState: UiState<List<RemittanceProductSalePickerEntryResponse>>,
    dayLabels: Map<String, RemittanceDayPickerEntryResponse>,
    isDraft: Boolean,
    deleteLineState: UiState<Unit>,
    deleteDayBreakdownState: UiState<Unit>,
    driftState: UiState<RemittanceDriftResponse>,
    viewModel: RemittanceViewModel,
    remittanceId: String,
    onOpenHeaderDialog: () -> Unit,
    onOpenSessionPicker: () -> Unit,
    onOpenProductSalePicker: () -> Unit,
    onOpenDayPicker: () -> Unit,
    onOpenSubmitDialog: () -> Unit,
    onOpenUndoDialog: () -> Unit,
) {
    val sessionLabels =
        (sessionPickerState as? UiState.Success)
            ?.data
            ?.associate { it.id to (it.clientName ?: "Session") }
            .orEmpty()
    val productSaleLabels =
        (productSalePickerState as? UiState.Success)
            ?.data
            ?.associate { it.id to it.productName }
            .orEmpty()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        RemittanceDetailHeaderSummary(
            detail = detail,
            isDraft = isDraft,
            onOpenHeaderDialog = onOpenHeaderDialog,
        )

        RemittanceDetailLinesSection(
            detail = detail,
            sessionLabels = sessionLabels,
            productSaleLabels = productSaleLabels,
            isDraft = isDraft,
            deleteLineState = deleteLineState,
            viewModel = viewModel,
            remittanceId = remittanceId,
            onOpenSessionPicker = onOpenSessionPicker,
            onOpenProductSalePicker = onOpenProductSalePicker,
        )

        RemittanceDetailDaysSection(
            dayBreakdowns = detail.dayBreakdowns,
            dayLabels = dayLabels,
            isDraft = isDraft,
            deleteDayBreakdownState = deleteDayBreakdownState,
            viewModel = viewModel,
            remittanceId = remittanceId,
            onOpenDayPicker = onOpenDayPicker,
            onOpenSubmitDialog = onOpenSubmitDialog,
        )

        RemittanceDetailReceiptSection(
            detail = detail,
            driftState = driftState,
            viewModel = viewModel,
            remittanceId = remittanceId,
            onOpenUndoDialog = onOpenUndoDialog,
        )
    }
}

@Composable
private fun RemittanceDetailContentDialogs(
    detail: RemittanceDetailResponse,
    branchId: String?,
    sessionPickerState: UiState<List<RemittanceSessionPickerEntryResponse>>,
    productSalePickerState: UiState<List<RemittanceProductSalePickerEntryResponse>>,
    dayPickerState: UiState<List<RemittanceDayPickerEntryResponse>>,
    lineState: UiState<RemittanceLineResponse>,
    dayBreakdownState: UiState<RemittanceDayBreakdownResponse>,
    submitState: UiState<RemittanceSubmitResponse>,
    undoState: UiState<RemittanceResponse>,
    headerUpdateState: UiState<RemittanceResponse>,
    dayLabels: Map<String, RemittanceDayPickerEntryResponse>,
    viewModel: RemittanceViewModel,
    remittanceId: String,
    showHeaderDialog: Boolean,
    showSessionPicker: Boolean,
    showProductSalePicker: Boolean,
    showDayPicker: Boolean,
    showSubmitDialog: Boolean,
    showUndoDialog: Boolean,
    onCloseHeaderDialog: () -> Unit,
    onCloseSessionPicker: () -> Unit,
    onCloseProductSalePicker: () -> Unit,
    onCloseDayPicker: () -> Unit,
    onCloseSubmitDialog: () -> Unit,
    onCloseUndoDialog: () -> Unit,
) {
    RemittanceDetailHeaderEditHost(
        detail = detail,
        headerUpdateState = headerUpdateState,
        viewModel = viewModel,
        remittanceId = remittanceId,
        showHeaderDialog = showHeaderDialog,
        onCloseHeaderDialog = onCloseHeaderDialog,
    )

    RemittanceDetailLinePickerHost(
        detail = detail,
        branchId = branchId,
        sessionPickerState = sessionPickerState,
        productSalePickerState = productSalePickerState,
        lineState = lineState,
        viewModel = viewModel,
        remittanceId = remittanceId,
        showSessionPicker = showSessionPicker,
        showProductSalePicker = showProductSalePicker,
        onCloseSessionPicker = onCloseSessionPicker,
        onCloseProductSalePicker = onCloseProductSalePicker,
    )

    RemittanceDetailDayPickerHost(
        detail = detail,
        branchId = branchId,
        dayPickerState = dayPickerState,
        dayBreakdownState = dayBreakdownState,
        viewModel = viewModel,
        remittanceId = remittanceId,
        showDayPicker = showDayPicker,
        onCloseDayPicker = onCloseDayPicker,
    )

    RemittanceDetailSubmitUndoHost(
        detail = detail,
        dayLabels = dayLabels,
        submitState = submitState,
        undoState = undoState,
        viewModel = viewModel,
        remittanceId = remittanceId,
        showSubmitDialog = showSubmitDialog,
        showUndoDialog = showUndoDialog,
        onCloseSubmitDialog = onCloseSubmitDialog,
        onCloseUndoDialog = onCloseUndoDialog,
    )
}

@Composable
private fun RemittanceDetailHeaderSummary(
    detail: RemittanceDetailResponse,
    isDraft: Boolean,
    onOpenHeaderDialog: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Remittance",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = detail.status.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (isDraft) {
            TextButton(onClick = onOpenHeaderDialog) {
                Text("Edit")
            }
        }
    }
    DetailRow("Type", remittanceTypeLabel(detail.type.name))
    DetailRow("Method", remittanceMethodLabel(detail.method.name))
    DetailRow("Date range", "${detail.dateRangeStart} – ${detail.dateRangeEnd}")
    DetailRow("Created", formatRelativeTimestamp(detail.createdAt))
    if (detail.submittedDate.isNotBlank()) {
        DetailRow("Submitted date", detail.submittedDate)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun RemittanceDetailHeaderEditHost(
    detail: RemittanceDetailResponse,
    headerUpdateState: UiState<RemittanceResponse>,
    viewModel: RemittanceViewModel,
    remittanceId: String,
    showHeaderDialog: Boolean,
    onCloseHeaderDialog: () -> Unit,
) {
    // D9 — header edit popup (the D2 create popup prefilled).
    if (showHeaderDialog) {
        HeaderEditDialog(
            detail = detail,
            updateState = headerUpdateState,
            onSave = { request ->
                if (headerUpdateState !is UiState.Loading) {
                    viewModel.updateHeader(remittanceId, request)
                }
            },
            onDismiss = {
                if (headerUpdateState !is UiState.Loading) {
                    onCloseHeaderDialog()
                }
            },
        )
    }
}

@Composable
private fun RemittanceDetailLinesSection(
    detail: RemittanceDetailResponse,
    sessionLabels: Map<String, String>,
    productSaleLabels: Map<String, String>,
    isDraft: Boolean,
    deleteLineState: UiState<Unit>,
    viewModel: RemittanceViewModel,
    remittanceId: String,
    onOpenSessionPicker: () -> Unit,
    onOpenProductSalePicker: () -> Unit,
) {
    Spacer(Modifier.size(Spacing.sm))
    SectionLabel("Lines")
    detail.lines.forEach { line ->
        LineRow(
            line = line,
            label = lineLabel(line, sessionLabels, productSaleLabels),
            deletable = isDraft,
            deleting = deleteLineState is UiState.Loading,
            onDelete = {
                if (deleteLineState !is UiState.Loading) {
                    viewModel.deleteLine(remittanceId, line.id)
                }
            },
        )
    }
    DetailRow("Total", peso(detail.totalAmount))
    if (isDraft) {
        Row {
            TextButton(onClick = onOpenSessionPicker) { Text("Add session income") }
            TextButton(onClick = onOpenProductSalePicker) { Text("Add product sales income") }
        }
    }
}

@Composable
private fun RemittanceDetailDaysSection(
    dayBreakdowns: List<RemittanceDayBreakdownResponse>,
    dayLabels: Map<String, RemittanceDayPickerEntryResponse>,
    isDraft: Boolean,
    deleteDayBreakdownState: UiState<Unit>,
    viewModel: RemittanceViewModel,
    remittanceId: String,
    onOpenDayPicker: () -> Unit,
    onOpenSubmitDialog: () -> Unit,
) {
    Spacer(Modifier.size(Spacing.sm))
    SectionLabel("Days covered")
    dayBreakdowns.forEach { breakdown ->
        val day = dayLabels[breakdown.branchDayId]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = day?.date ?: breakdown.branchDayId,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = day?.status?.name.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (isDraft) {
                TextButton(
                    onClick = {
                        if (deleteDayBreakdownState !is UiState.Loading) {
                            viewModel.deleteDayBreakdown(remittanceId, breakdown.id)
                        }
                    },
                    enabled = deleteDayBreakdownState !is UiState.Loading,
                ) {
                    Text("×")
                }
            }
        }
    }
    if (isDraft) {
        TextButton(onClick = onOpenDayPicker) { Text("Add days") }
        Spacer(Modifier.size(Spacing.sm))
        OutlinedButton(
            onClick = onOpenSubmitDialog,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Submit")
        }
    }
}

@Composable
private fun RemittanceDetailReceiptSection(
    detail: RemittanceDetailResponse,
    driftState: UiState<RemittanceDriftResponse>,
    viewModel: RemittanceViewModel,
    remittanceId: String,
    onOpenUndoDialog: () -> Unit,
) {
    val isSubmittedSession =
        detail.status == com.companyb.companyapp.domain.RemittanceStatus.SUBMITTED &&
            detail.type == com.companyb.companyapp.domain.RemittanceType.SESSION
    val snapshot = detail.snapshot
    if (isSubmittedSession && snapshot != null) {
        Spacer(Modifier.size(Spacing.md))
        FrozenReceiptBlock(
            snapshot = snapshot,
            driftState = driftState,
            onShowDrift = { viewModel.loadDrift(remittanceId) },
        )
    }

    if (remittanceCanUndo(detail)) {
        Spacer(Modifier.size(Spacing.sm))
        OutlinedButton(
            onClick = onOpenUndoDialog,
            colors =
                ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            border =
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.error,
                ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Undo submission")
        }
    }
}

@Composable
private fun RemittanceDetailSubmitUndoHost(
    detail: RemittanceDetailResponse,
    dayLabels: Map<String, RemittanceDayPickerEntryResponse>,
    submitState: UiState<RemittanceSubmitResponse>,
    undoState: UiState<RemittanceResponse>,
    viewModel: RemittanceViewModel,
    remittanceId: String,
    showSubmitDialog: Boolean,
    showUndoDialog: Boolean,
    onCloseSubmitDialog: () -> Unit,
    onCloseUndoDialog: () -> Unit,
) {
    // D5 — structural confirm; numbers unknowable pre-submit (F4): type, covered days
    // (count + dates), line count, line total, method + the 48h warning copy.
    if (showSubmitDialog) {
        SubmitConfirmDialog(
            detail = detail,
            dayLabels = dayLabels,
            state = submitState,
            onSubmit = {
                if (submitState !is UiState.Loading) {
                    viewModel.submit(
                        remittanceId,
                        SubmitRemittanceRequest(expectedVersion = detail.version),
                    )
                }
            },
            onDismiss = {
                if (submitState !is UiState.Loading) {
                    onCloseSubmitDialog()
                }
            },
        )
    }

    // D10 — undo confirm: reason required (one line, mirroring the Void discipline).
    if (showUndoDialog) {
        UndoDialog(
            state = undoState,
            onSubmit = { reason ->
                if (undoState !is UiState.Loading) {
                    viewModel.undo(
                        remittanceId,
                        UndoRemittanceRequest(expectedVersion = detail.version, reason = reason),
                    )
                }
            },
            onDismiss = {
                if (undoState !is UiState.Loading) {
                    onCloseUndoDialog()
                }
            },
        )
    }
}

@Composable
private fun RemittanceDetailLinePickerHost(
    detail: RemittanceDetailResponse,
    branchId: String?,
    sessionPickerState: UiState<List<RemittanceSessionPickerEntryResponse>>,
    productSalePickerState: UiState<List<RemittanceProductSalePickerEntryResponse>>,
    lineState: UiState<RemittanceLineResponse>,
    viewModel: RemittanceViewModel,
    remittanceId: String,
    showSessionPicker: Boolean,
    showProductSalePicker: Boolean,
    onCloseSessionPicker: () -> Unit,
    onCloseProductSalePicker: () -> Unit,
) {
    // D3 — tick-to-include pickers; the dialog owns its add-queue (one POST per selected row,
    // advanced on each mutation success).
    if (showSessionPicker && branchId != null) {
        val includedIds =
            detail.lines
                .filter { it.type == com.companyb.companyapp.domain.RemittanceLineType.SESSION }
                .mapNotNull { it.sessionId }
                .toSet()
        SessionPickerDialog(
            state = sessionPickerState,
            mutationState = lineState,
            includedIds = includedIds,
            onLoad = {
                viewModel.loadSessionPicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
            },
            onAdd = { requests ->
                if (requests.isNotEmpty() && lineState !is UiState.Loading) {
                    viewModel.addLine(remittanceId, requests.first())
                }
            },
            onDismiss = onCloseSessionPicker,
        )
    }
    if (showProductSalePicker && branchId != null) {
        val includedIds =
            detail.lines
                .filter { it.type == com.companyb.companyapp.domain.RemittanceLineType.PRODUCT_SALE }
                .mapNotNull { it.productSaleId }
                .toSet()
        ProductSalePickerDialog(
            state = productSalePickerState,
            mutationState = lineState,
            includedIds = includedIds,
            onLoad = {
                viewModel.loadProductSalePicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
            },
            onAdd = { requests ->
                if (requests.isNotEmpty() && lineState !is UiState.Loading) {
                    viewModel.addLine(remittanceId, requests.first())
                }
            },
            onDismiss = onCloseProductSalePicker,
        )
    }
}

@Composable
private fun RemittanceDetailDayPickerHost(
    detail: RemittanceDetailResponse,
    branchId: String?,
    dayPickerState: UiState<List<RemittanceDayPickerEntryResponse>>,
    dayBreakdownState: UiState<RemittanceDayBreakdownResponse>,
    viewModel: RemittanceViewModel,
    remittanceId: String,
    showDayPicker: Boolean,
    onCloseDayPicker: () -> Unit,
) {
    // D4 — days-covered picker: already-remitted greyed (no double-covering a day, F10).
    if (showDayPicker && branchId != null) {
        val includedIds = detail.dayBreakdowns.map { it.branchDayId }.toSet()
        DayPickerDialog(
            state = dayPickerState,
            mutationState = dayBreakdownState,
            includedIds = includedIds,
            onLoad = {
                viewModel.loadDayPicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
            },
            onAdd = { requests ->
                if (requests.isNotEmpty() && dayBreakdownState !is UiState.Loading) {
                    viewModel.addDayBreakdown(remittanceId, requests.first())
                }
            },
            onDismiss = onCloseDayPicker,
        )
    }
}

private fun lineLabel(
    line: RemittanceLineResponse,
    sessionLabels: Map<String, String>,
    productSaleLabels: Map<String, String>,
): String =
    when (line.type) {
        com.companyb.companyapp.domain.RemittanceLineType.SESSION -> {
            sessionLabels[line.sessionId] ?: "Session"
        }

        com.companyb.companyapp.domain.RemittanceLineType.PRODUCT_SALE -> {
            productSaleLabels[line.productSaleId]
                ?: "Product sale"
        }
    }

@Composable
private fun LineRow(
    line: RemittanceLineResponse,
    label: String,
    deletable: Boolean,
    deleting: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = lineTypeLabel(line.type.name),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                // D3 — added-at; added-by (createdBy UUID) has no name join (F7 bare lines).
                text = formatRelativeTimestamp(line.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = peso(line.amount),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (deletable) {
            TextButton(
                onClick = onDelete,
                enabled = !deleting,
            ) {
                Text("×")
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * D6 — the receipt: frozen-at-submission block (SESSION-only snapshot), styled as a record —
 * hairline card, lock glyph, muted "Frozen at submission" label, distinct from live data.
 * Lazy "Show current state" expander fetches the #118 drift on first click only.
 */
@Composable
private fun FrozenReceiptBlock(
    snapshot: RemittanceFinancialSnapshotResponse,
    driftState: UiState<RemittanceDriftResponse>,
    onShowDrift: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // D6 — lazy: the drift fetches on the first expand only and is then cached (the VM keeps the
    // loaded state); later toggles just open/close the body. A failed fetch re-arms the flag so
    // the next expand retries.
    var driftRequested by remember { mutableStateOf(false) }

    LaunchedEffect(driftState) {
        if (driftState is UiState.Error) {
            driftRequested = false
        }
    }

    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LockIcon(tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "Frozen at submission",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = formatRelativeTimestamp(snapshot.snapshottedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(Spacing.sm))
            SnapshotRow("Gross", peso(snapshot.grossIncome))
            SnapshotRow("Compensation", peso(snapshot.totalCompensation))
            SnapshotRow("Expenses", peso(snapshot.totalExpenses))
            SnapshotRow("Net", peso(snapshot.netIncome))
            TextButton(
                onClick = {
                    if (!driftRequested) {
                        driftRequested = true
                        onShowDrift()
                    }
                    expanded = !expanded
                },
            ) {
                Text(if (expanded) "Hide current state" else "Show current state")
            }
            if (expanded) {
                DriftBody(driftState)
            }
        }
    }
}

@Composable
private fun SnapshotRow(
    label: String,
    value: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** D6 — the drift expander body: frozen → current per sum; drifted values highlighted. */
@Composable
private fun DriftBody(state: UiState<RemittanceDriftResponse>) {
    when (state) {
        is UiState.Idle, is UiState.Loading -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        is UiState.Success -> {
            val drift = state.data
            DriftRow("Compensation", drift.frozen.totalCompensation, drift.currentCompensation)
            DriftRow("Expenses", drift.frozen.totalExpenses, drift.currentExpenses)
            DriftRow("Net", drift.frozen.netIncome, drift.currentNet)
            // Gross never drifts (lines immutable after submit) — the frozen gross is current.
        }
    }
}

@Composable
private fun DriftRow(
    label: String,
    frozen: String,
    current: String,
) {
    val drifted = frozen != current
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${peso(frozen)} → ${peso(current)}",
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (drifted) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}

// D6 — lock glyph via Canvas (no material-icons dependency, #107 precedent): stroked shackle arc
// + filled round-rect body reads as a closed padlock at icon size.
@Suppress("MagicNumber") // padlock proportion literals — one-time drawing constants
@Composable
private fun LockIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val keyholeColor = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = modifier.size(14.dp),
    ) {
        val w = size.width
        val h = size.height
        val stroke = 1.5.dp.toPx()
        val bodyTop = h * 0.40f
        val shackleTop = h * 0.06f
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, shackleTop),
            size = Size(w * 0.56f, (bodyTop - shackleTop) * 2f),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.10f, bodyTop),
            size = Size(w * 0.80f, h * 0.46f),
            cornerRadius = GeometryCornerRadius(2.dp.toPx()),
        )
        drawCircle(
            color = keyholeColor,
            radius = 1.5.dp.toPx(),
            center = Offset(w * 0.50f, bodyTop + h * 0.25f),
        )
    }
}

/** D9 — header edit popup: the D2 create popup prefilled from the draft; PATCH on save. */
@Composable
private fun HeaderEditDialog(
    detail: RemittanceDetailResponse,
    updateState: UiState<RemittanceResponse>,
    onSave: (UpdateRemittanceHeaderRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var type by remember {
        mutableStateOf(
            RemittanceTypeChoice.entries.firstOrNull { it.raw == detail.type.name }
                ?: RemittanceTypeChoice.SESSIONS,
        )
    }
    var method by remember {
        mutableStateOf(
            RemittanceMethodChoice.entries.firstOrNull { it.raw == detail.method.name }
                ?: RemittanceMethodChoice.BANK_TRANSFER,
        )
    }
    var startDate by remember { mutableStateOf(detail.dateRangeStart) }
    var endDate by remember { mutableStateOf(detail.dateRangeEnd) }
    var dateError by remember { mutableStateOf<String?>(null) }

    val inFlight = updateState is UiState.Loading

    fun commit() {
        if (inFlight) return
        val rangeProblem = remittanceRangeError(startDate, endDate)
        if (rangeProblem != null) {
            dateError = rangeProblem
            return
        }
        dateError = null
        onSave(
            UpdateRemittanceHeaderRequest(
                type =
                    com.companyb.companyapp.domain.RemittanceType
                        .valueOf(type.raw),
                method =
                    com.companyb.companyapp.domain.RemittanceMethod
                        .valueOf(method.raw),
                dateRangeStart = startDate,
                dateRangeEnd = endDate,
                expectedVersion = detail.version,
            ),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit header") },
        text = {
            HeaderEditFields(
                type = type,
                method = method,
                startDate = startDate,
                endDate = endDate,
                dateError = dateError,
                updateState = updateState,
                onTypeSelect = { label ->
                    type = RemittanceTypeChoice.entries.first { it.label == label }
                },
                onMethodSelect = { label ->
                    method = RemittanceMethodChoice.entries.first { it.label == label }
                },
                onStartChange = { startDate = it },
                onEndChange = { endDate = it },
            )
        },
        confirmButton = {
            TextButton(
                onClick = ::commit,
                enabled = !inFlight,
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !inFlight,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun HeaderEditFields(
    type: RemittanceTypeChoice,
    method: RemittanceMethodChoice,
    startDate: String,
    endDate: String,
    dateError: String?,
    updateState: UiState<RemittanceResponse>,
    onTypeSelect: (String) -> Unit,
    onMethodSelect: (String) -> Unit,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
) {
    Column {
        LabeledDropdown(
            label = "Type",
            displayValue = type.label,
            options = RemittanceTypeChoice.entries.map { it.label },
            onSelect = onTypeSelect,
        )
        LabeledDropdown(
            label = "Method",
            displayValue = method.label,
            options = RemittanceMethodChoice.entries.map { it.label },
            onSelect = onMethodSelect,
        )
        RemittanceDatePickerField(
            label = "Date range start",
            value = startDate,
            onValueChange = onStartChange,
        )
        RemittanceDatePickerField(
            label = "Date range end",
            value = endDate,
            onValueChange = onEndChange,
        )
        dateError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        (updateState as? UiState.Error)?.let {
            Text(
                text = it.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * D3 — shared tick-to-include income picker (sessions + product sales): per-row selection with
 * prefilled editable amounts, one mutation POST per selected row via [PendingQueueEffect].
 */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun <T> IncomePickerDialog(
    title: String,
    emptyMessage: String,
    state: UiState<List<T>>,
    mutationState: UiState<RemittanceLineResponse>,
    includedIds: Set<String>,
    onLoad: () -> Unit,
    onAdd: (List<CreateRemittanceLineRequest>) -> Unit,
    onDismiss: () -> Unit,
    idOf: (T) -> String,
    mainLabel: (T) -> String,
    secondaryLabel: (T) -> String,
    amountOf: (T) -> String,
    toRequest: (id: String, amount: String) -> CreateRemittanceLineRequest,
) {
    val entries = (state as? UiState.Success)?.data
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var amounts by remember { mutableStateOf(emptyMap<String, String>()) }
    var pending by remember { mutableStateOf<List<CreateRemittanceLineRequest>>(emptyList()) }

    PickerPendingHost(
        pending = pending,
        mutationState = mutationState,
        onAdd = onAdd,
        onDismiss = onDismiss,
        onPendingChange = { pending = it },
    )
    AlertDialog(
        onDismissRequest = {
            if (mutationState !is UiState.Loading && pending.isEmpty()) onDismiss()
        },
        title = { Text(title) },
        text = {
            IncomePickerBody(
                state = state,
                emptyMessage = emptyMessage,
                mutationState = mutationState,
                includedIds = includedIds,
                selectedIds = selectedIds,
                amounts = amounts,
                onLoad = onLoad,
                onToggle = { entry, selected, included ->
                    if (included) return@IncomePickerBody
                    val id = idOf(entry)
                    selectedIds = if (selected) selectedIds - id else selectedIds + id
                    amounts = amounts + (id to amountOf(entry))
                },
                onAmountChange = { id, value -> amounts = amounts + (id to value) },
                idOf = idOf,
                mainLabel = mainLabel,
                secondaryLabel = secondaryLabel,
                amountOf = amountOf,
            )
        },
        confirmButton = {
            IncomePickerConfirmButton(
                mutationState = mutationState,
                pending = pending,
                selectedIds = selectedIds,
                entries = entries,
                amounts = amounts,
                idOf = idOf,
                amountOf = amountOf,
                toRequest = toRequest,
                onConfirm = { confirmIncomePickerRequests(it, { pending = it }, { selectedIds = it }, onAdd) },
            )
        },
        dismissButton = { IncomePickerDismissButton(mutationState = mutationState, onDismiss = onDismiss) },
    )
}

/** #462 — confirm triple-write as a named hook so the dialog call site stays single-statement. */
private fun confirmIncomePickerRequests(
    requests: List<CreateRemittanceLineRequest>,
    onPendingChange: (List<CreateRemittanceLineRequest>) -> Unit,
    onSelectedChange: (Set<String>) -> Unit,
    onAdd: (List<CreateRemittanceLineRequest>) -> Unit,
) {
    onPendingChange(requests)
    onSelectedChange(emptySet())
    onAdd(requests)
}

@Composable
private fun <T> PickerPendingHost(
    pending: List<T>,
    mutationState: UiState<*>,
    onAdd: (List<T>) -> Unit,
    onDismiss: () -> Unit,
    onPendingChange: (List<T>) -> Unit,
) {
    // One POST per selected row, advanced on each success; a failure or the VM's 403/409 Idle
    // reset clears the queue (the dialog stays open showing the error / the reloaded state).
    PendingQueueEffect(
        pending = pending,
        mutationState = mutationState,
        onNext = { remaining ->
            onPendingChange(remaining)
            onAdd(remaining)
        },
        onFinished = {
            onPendingChange(emptyList())
            onDismiss()
        },
        onAborted = { onPendingChange(emptyList()) },
    )
}

@Composable
private fun <T> IncomePickerConfirmButton(
    mutationState: UiState<RemittanceLineResponse>,
    pending: List<CreateRemittanceLineRequest>,
    selectedIds: Set<String>,
    entries: List<T>?,
    amounts: Map<String, String>,
    idOf: (T) -> String,
    amountOf: (T) -> String,
    toRequest: (id: String, amount: String) -> CreateRemittanceLineRequest,
    onConfirm: (List<CreateRemittanceLineRequest>) -> Unit,
) {
    TextButton(
        onClick = {
            val addBlocked =
                mutationState is UiState.Loading || pending.isNotEmpty() ||
                    selectedIds.isEmpty() || entries == null
            if (addBlocked) {
                return@TextButton
            }
            onConfirm(
                selectedIds.map { id ->
                    toRequest(id, amounts[id] ?: amountOf(entries.first { idOf(it) == id }))
                },
            )
        },
        enabled = mutationState !is UiState.Loading && pending.isEmpty() && selectedIds.isNotEmpty(),
    ) {
        Text("Add")
    }
}

@Composable
private fun IncomePickerDismissButton(
    mutationState: UiState<*>,
    onDismiss: () -> Unit,
) {
    TextButton(
        onClick = onDismiss,
        enabled = mutationState !is UiState.Loading,
    ) {
        Text("Cancel")
    }
}

@Composable
private fun <T> IncomePickerBody(
    state: UiState<List<T>>,
    emptyMessage: String,
    mutationState: UiState<RemittanceLineResponse>,
    includedIds: Set<String>,
    selectedIds: Set<String>,
    amounts: Map<String, String>,
    onLoad: () -> Unit,
    onToggle: (entry: T, selected: Boolean, included: Boolean) -> Unit,
    onAmountChange: (id: String, value: String) -> Unit,
    idOf: (T) -> String,
    mainLabel: (T) -> String,
    secondaryLabel: (T) -> String,
    amountOf: (T) -> String,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        when (val s = state) {
            is UiState.Idle, is UiState.Loading -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onLoad) { Text("Retry") }
            }

            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    EmptyState(emptyMessage)
                } else {
                    s.data.forEach { entry ->
                        val id = idOf(entry)
                        val included = id in includedIds
                        val selected = id in selectedIds
                        PickerEntryRow(
                            label = mainLabel(entry),
                            secondary = secondaryLabel(entry),
                            amountText = peso(amountOf(entry)),
                            selected = selected,
                            enabled = !included,
                            amount = amounts[id],
                            onAmountChange = { value -> onAmountChange(id, value) },
                            onToggle = { onToggle(entry, selected, included) },
                        )
                    }
                }
            }
        }
        (mutationState as? UiState.Error)?.let {
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = it.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** D3 — sessions picker: client name, time (bookedAt) + status, amount prefilled from finalPrice. */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun SessionPickerDialog(
    state: UiState<List<RemittanceSessionPickerEntryResponse>>,
    mutationState: UiState<RemittanceLineResponse>,
    includedIds: Set<String>,
    onLoad: () -> Unit,
    onAdd: (List<CreateRemittanceLineRequest>) -> Unit,
    onDismiss: () -> Unit,
) {
    IncomePickerDialog(
        title = "Add session income",
        emptyMessage = "No sessions in this date range",
        state = state,
        mutationState = mutationState,
        includedIds = includedIds,
        onLoad = onLoad,
        onAdd = onAdd,
        onDismiss = onDismiss,
        idOf = { it.id },
        mainLabel = { it.clientName ?: "Anonymized" },
        secondaryLabel = { entry ->
            val time = entry.bookedAt?.let { formatRelativeTimestamp(it) }
            listOfNotNull(time, entry.sessionStatus).joinToString(" · ")
        },
        amountOf = { it.finalPrice },
        toRequest = { id, amount ->
            CreateRemittanceLineRequest(
                id = Uuid.random().toString(),
                type = com.companyb.companyapp.domain.RemittanceLineType.SESSION,
                sessionId = id,
                amount = amount,
            )
        },
    )
}

/** D3 — product-sales picker: product name + quantity, amount prefilled from totalAmountAtTime. */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun ProductSalePickerDialog(
    state: UiState<List<RemittanceProductSalePickerEntryResponse>>,
    mutationState: UiState<RemittanceLineResponse>,
    includedIds: Set<String>,
    onLoad: () -> Unit,
    onAdd: (List<CreateRemittanceLineRequest>) -> Unit,
    onDismiss: () -> Unit,
) {
    IncomePickerDialog(
        title = "Add product sales income",
        emptyMessage = "No product sales in this date range",
        state = state,
        mutationState = mutationState,
        includedIds = includedIds,
        onLoad = onLoad,
        onAdd = onAdd,
        onDismiss = onDismiss,
        idOf = { it.id },
        mainLabel = { it.productName },
        secondaryLabel = { "Qty ${it.quantity}" },
        amountOf = { it.totalAmountAtTime },
        toRequest = { id, amount ->
            CreateRemittanceLineRequest(
                id = Uuid.random().toString(),
                type = com.companyb.companyapp.domain.RemittanceLineType.PRODUCT_SALE,
                productSaleId = id,
                amount = amount,
            )
        },
    )
}

/**
 * Shared add-queue driver for the tick-to-include pickers (D3/D4): one mutation per selected
 * row, advanced on each success; an Error or the VM's 403/409 Idle reset aborts the batch
 * (the dialog stays open — the error / reloaded state is visible — and the queue clears, so
 * dismissal is never blocked by a stalled batch).
 */
@Composable
private fun <T> PendingQueueEffect(
    pending: List<T>,
    mutationState: UiState<*>,
    onNext: (List<T>) -> Unit,
    onFinished: () -> Unit,
    onAborted: () -> Unit,
) {
    LaunchedEffect(mutationState) {
        if (pending.isEmpty()) return@LaunchedEffect
        when (mutationState) {
            is UiState.Success -> {
                val remaining = pending.drop(1)
                if (remaining.isEmpty()) {
                    onFinished()
                } else {
                    onNext(remaining)
                }
            }

            is UiState.Error, is UiState.Idle -> {
                onAborted()
            }

            is UiState.Loading -> {
                Unit
            }
        }
    }
}

/** D4 — days-covered picker: effective statuses from #118 G4; already-remitted greyed out. */
@OptIn(ExperimentalUuidApi::class)
@Composable
private fun DayPickerDialog(
    state: UiState<List<RemittanceDayPickerEntryResponse>>,
    mutationState: UiState<RemittanceDayBreakdownResponse>,
    includedIds: Set<String>,
    onLoad: () -> Unit,
    onAdd: (List<AddDayBreakdownRequest>) -> Unit,
    onDismiss: () -> Unit,
) {
    val entries = (state as? UiState.Success)?.data
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var pending by remember { mutableStateOf<List<AddDayBreakdownRequest>>(emptyList()) }

    // One POST per selected day, advanced on each success; a failure or the VM's 403/409 Idle
    // reset clears the queue (the dialog stays open showing the error / the reloaded state).
    PickerPendingHost(
        pending = pending,
        mutationState = mutationState,
        onAdd = onAdd,
        onDismiss = onDismiss,
        onPendingChange = { pending = it },
    )

    AlertDialog(
        onDismissRequest = {
            if (mutationState !is UiState.Loading && pending.isEmpty()) {
                onDismiss()
            }
        },
        title = { Text("Add days") },
        text = {
            DayPickerBody(
                state = state,
                mutationState = mutationState,
                includedIds = includedIds,
                selectedIds = selectedIds,
                onLoad = onLoad,
                onToggle = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
            )
        },
        confirmButton = {
            DayPickerConfirmButton(
                mutationState = mutationState,
                pending = pending,
                selectedIds = selectedIds,
                entries = entries,
                onConfirm = { requests ->
                    pending = requests
                    selectedIds = emptySet()
                    onAdd(requests)
                },
            )
        },
        dismissButton = {
            IncomePickerDismissButton(mutationState = mutationState, onDismiss = onDismiss)
        },
    )
}

/** D4 — day-picker text column: load/error/empty/day rows + mutation error. */
@Composable
private fun DayPickerBody(
    state: UiState<List<RemittanceDayPickerEntryResponse>>,
    mutationState: UiState<RemittanceDayBreakdownResponse>,
    includedIds: Set<String>,
    selectedIds: Set<String>,
    onLoad: () -> Unit,
    onToggle: (id: String) -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        when (val s = state) {
            is UiState.Idle, is UiState.Loading -> {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onLoad) { Text("Retry") }
            }

            is UiState.Success -> {
                if (s.data.isEmpty()) {
                    EmptyState("No days in this date range")
                } else {
                    s.data.forEach { entry ->
                        DayPickerDayRow(
                            entry = entry,
                            includedIds = includedIds,
                            selectedIds = selectedIds,
                            onToggle = onToggle,
                        )
                    }
                }
            }
        }
        (mutationState as? UiState.Error)?.let {
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = it.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** D4 — one day row: tick glyph + date + status; already-added/remitted rows grey out. */
@Composable
private fun DayPickerDayRow(
    entry: RemittanceDayPickerEntryResponse,
    includedIds: Set<String>,
    selectedIds: Set<String>,
    onToggle: (id: String) -> Unit,
) {
    val included = entry.id in includedIds
    val remitted = entry.status == com.companyb.companyapp.domain.DayStatus.REMITTED
    val selectable = !included && !remitted
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = selectable) { onToggle(entry.id) }
                .rowHover(enabled = selectable)
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = if (entry.id in selectedIds) "✓" else "○",
            color =
                if (entry.id in selectedIds) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = entry.date,
            style = MaterialTheme.typography.bodyMedium,
            color =
                if (selectable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
                when {
                    included -> "Added"
                    remitted -> "Already remitted"
                    else -> entry.status.name
                },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun DayPickerConfirmButton(
    mutationState: UiState<RemittanceDayBreakdownResponse>,
    pending: List<AddDayBreakdownRequest>,
    selectedIds: Set<String>,
    entries: List<RemittanceDayPickerEntryResponse>?,
    onConfirm: (List<AddDayBreakdownRequest>) -> Unit,
) {
    TextButton(
        onClick = {
            val addBlocked =
                mutationState is UiState.Loading || pending.isNotEmpty() ||
                    selectedIds.isEmpty() || entries == null
            if (addBlocked) {
                return@TextButton
            }
            onConfirm(
                selectedIds.map { id ->
                    AddDayBreakdownRequest(
                        id = Uuid.random().toString(),
                        branchDayId = id,
                    )
                },
            )
        },
        enabled = mutationState !is UiState.Loading && pending.isEmpty() && selectedIds.isNotEmpty(),
    ) {
        Text("Add")
    }
}

/** D3 — one picker row: tick glyph + label + secondary line + amount (editable when selected). */
@Composable
private fun PickerEntryRow(
    label: String,
    secondary: String,
    amountText: String,
    selected: Boolean,
    enabled: Boolean,
    amount: String?,
    onAmountChange: (String) -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onToggle)
                .rowHover(enabled = enabled)
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = if (selected) "✓" else "○",
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
        Spacer(Modifier.width(Spacing.xs))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
            Text(
                text = if (enabled) secondary else "Added",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            OutlinedTextField(
                value = amount.orEmpty(),
                onValueChange = onAmountChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(120.dp),
            )
        } else {
            Text(
                text = amountText,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

/** D5 — structural confirm: type, covered days (count + dates), line count, line total, method. */
@Composable
private fun SubmitConfirmDialog(
    detail: RemittanceDetailResponse,
    dayLabels: Map<String, RemittanceDayPickerEntryResponse>,
    state: UiState<RemittanceSubmitResponse>,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Submit remittance?") },
        text = {
            SubmitConfirmBody(
                detail = detail,
                dayLabels = dayLabels,
                state = state,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                enabled = state !is UiState.Loading,
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = state !is UiState.Loading,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SubmitConfirmBody(
    detail: RemittanceDetailResponse,
    dayLabels: Map<String, RemittanceDayPickerEntryResponse>,
    state: UiState<RemittanceSubmitResponse>,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
            text = remittanceTypeLabel(detail.type.name),
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.size(Spacing.xs))
        Text(
            text =
                "Method: ${remittanceMethodLabel(detail.method.name)}\n" +
                    "Days covered: ${detail.dayBreakdowns.size}\n" +
                    "Lines: ${detail.lines.size}\n" +
                    "Line total: ${peso(detail.totalAmount)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.size(Spacing.xs))
        detail.dayBreakdowns.forEach { breakdown ->
            Text(
                text = "• ${dayLabels[breakdown.branchDayId]?.date ?: breakdown.branchDayId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(Spacing.sm))
        // #447 — snapshot-review step (money UX requirement): the numbers that
        // freeze are reviewed here, before confirm, in every submit path. The
        // submit itself still writes the existing snapshot path unchanged.
        SubmitSnapshotReview(detail = detail)
        Spacer(Modifier.size(Spacing.sm))
        Text(
            text =
                "After submit, these days lock (REMITTED) and the amounts freeze. " +
                    "You can undo within 48 hours — after that it's permanent.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        (state as? UiState.Error)?.let {
            Spacer(Modifier.size(Spacing.xs))
            Text(
                text = it.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * #447 — snapshot-review step inside the submit confirm (money UX requirement): the
 * amounts that freeze at submit, reviewed before confirm. SESSION freezes gross (the
 * line total), compensation, and expenses into the immutable snapshot; PRODUCT writes
 * no SESSION snapshot and excludes commission. Read-only review — the submit still
 * writes the existing snapshot path unchanged.
 */
@Composable
private fun SubmitSnapshotReview(detail: RemittanceDetailResponse) {
    val isSession = detail.type == com.companyb.companyapp.domain.RemittanceType.SESSION
    Surface(
        shape = RoundedCornerShape(CornerRadius.sm),
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Snapshot review",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "LOCKS ON SUBMIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(Spacing.xs))
            if (isSession) {
                // The server freezes SESSION-type lines only — a mixed draft's gross-to-freeze
                // differs from its line total (both previewed from the loaded lines).
                val sessionGrossCents = sessionLinesGrossCents(detail.lines)
                SnapshotRow("Gross to freeze", peso(centsToMoney(sessionGrossCents)))
                if (moneyToCents(detail.totalAmount) != sessionGrossCents) {
                    SnapshotRow("Line total (incl. product lines)", peso(detail.totalAmount))
                    Text(
                        text = "Only session lines freeze into gross.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "Compensation and expenses freeze from the branch-day records at submit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SnapshotRow("Product total", peso(detail.totalAmount))
                Text(
                    text = "Product flows write no SESSION snapshot; commission is excluded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * #447 — Variant B control desk (owner verdict on #429): draft queue | editable center
 * column | persistent submission brief. Wide layouts only; the center is the unchanged
 * detail content (every flow and state preserved), the rails are read-only
 * rearrangements of existing sources — no new endpoints, DTOs, or routes.
 */
@Composable
private fun RemittanceControlDesk(
    detail: RemittanceDetailResponse,
    branchId: String,
    currentId: String,
    queueMirrors: Map<String, List<RemittanceResponse>>,
    queueState: UiState<List<RemittanceResponse>>,
    dayEntries: Map<String, RemittanceDayPickerEntryResponse>,
    onQueueClick: (String) -> Unit,
    onRetryQueue: () -> Unit,
    center: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Surface(
            shape = RoundedCornerShape(CornerRadius.md),
            border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(CONTROL_DESK_RAIL_WIDTH).fillMaxHeight(),
        ) {
            DraftQueueRail(
                branchId = branchId,
                currentId = currentId,
                mirrors = queueMirrors,
                queueState = queueState,
                onQueueClick = onQueueClick,
                onRetryQueue = onRetryQueue,
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            center()
        }
        Surface(
            shape = RoundedCornerShape(CornerRadius.md),
            border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(CONTROL_DESK_RAIL_WIDTH).fillMaxHeight(),
        ) {
            SubmissionBriefRail(detail = detail, dayEntries = dayEntries)
        }
    }
}

/** #447 — desk left rail: drafts to review plus submitted history, from the drafts source. */
@Composable
private fun DraftQueueRail(
    branchId: String,
    currentId: String,
    mirrors: Map<String, List<RemittanceResponse>>,
    queueState: UiState<List<RemittanceResponse>>,
    onQueueClick: (String) -> Unit,
    onRetryQueue: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "Draft queue",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Choose a flow to review",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QueueSection(
            title = "Drafts",
            rows = mirrors[remittanceListKey(branchId, DESK_QUEUE_DRAFTS_STATUS)],
            emptyText = "No drafts",
            currentId = currentId,
            queueState = queueState,
            onQueueClick = onQueueClick,
            onRetryQueue = onRetryQueue,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        QueueSection(
            title = "Submitted history",
            rows = mirrors[remittanceListKey(branchId, DESK_QUEUE_SUBMITTED_STATUS)],
            emptyText = "No submitted remittances",
            currentId = currentId,
            queueState = queueState,
            onQueueClick = onQueueClick,
            onRetryQueue = onRetryQueue,
        )
    }
}

/**
 * #447 — one queue section: the selected status key's mirror renders (a response for the
 * other status can never paint here); without a mirror yet, spinner / error + Retry.
 */
@Composable
private fun QueueSection(
    title: String,
    rows: List<RemittanceResponse>?,
    emptyText: String,
    currentId: String,
    queueState: UiState<List<RemittanceResponse>>,
    onQueueClick: (String) -> Unit,
    onRetryQueue: () -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when {
        rows != null -> {
            if (rows.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                rows.forEach { item ->
                    QueueRow(
                        item = item,
                        selected = item.id == currentId,
                        onClick = { onQueueClick(item.id) },
                    )
                }
            }
        }

        // The list flow is shared across statuses: a Success here may belong to the OTHER
        // section's landing while this key's load failed — Retry instead of spinning forever.
        queueState is UiState.Error || queueState is UiState.Success -> {
            Text(
                text = (queueState as? UiState.Error)?.message ?: "Couldn't load this section",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onRetryQueue) {
                Text("Retry")
            }
        }

        else -> {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: RemittanceResponse,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !selected,
        shape = RoundedCornerShape(CornerRadius.sm),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primary.copy(alpha = QUEUE_SELECTED_ALPHA)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        border =
            BorderStroke(
                width = 1.dp,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = QUEUE_SELECTED_BORDER_ALPHA)
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = remittanceTypeLabel(item.type.name),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.status.name,
                    style = MaterialTheme.typography.labelSmall,
                    color =
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
            Text(
                text = "${item.dateRangeStart} – ${item.dateRangeEnd}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = remittanceMethodLabel(item.method.name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * #447 — desk right rail: the covered range, line items, day states, and frozen-snapshot
 * review at a glance. Read-only; every number comes from the loaded detail (rearranged,
 * not re-sourced).
 */
@Composable
private fun SubmissionBriefRail(
    detail: RemittanceDetailResponse,
    dayEntries: Map<String, RemittanceDayPickerEntryResponse>,
) {
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = "Submission brief",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = remittanceTypeLabel(detail.type.name),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "${detail.dateRangeStart} – ${detail.dateRangeEnd}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Method: ${remittanceMethodLabel(detail.method.name)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SnapshotRow("Lines", detail.lines.size.toString())
        SnapshotRow("Line total", peso(detail.totalAmount))
        SnapshotRow("Days covered", detail.dayBreakdowns.size.toString())
        detail.dayBreakdowns.forEach { breakdown ->
            val day = dayEntries[breakdown.branchDayId]
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = day?.date ?: breakdown.branchDayId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = day?.status?.name.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        SubmissionBriefSnapshot(detail)
    }
}

@Composable
private fun SubmissionBriefSnapshot(detail: RemittanceDetailResponse) {
    val snapshot = detail.snapshot
    if (snapshot != null) {
        Text(
            text = "Frozen at submission",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SnapshotRow("Gross", peso(snapshot.grossIncome))
        SnapshotRow("Compensation", peso(snapshot.totalCompensation))
        SnapshotRow("Expenses", peso(snapshot.totalExpenses))
        SnapshotRow("Net", peso(snapshot.netIncome))
    } else if (detail.type == com.companyb.companyapp.domain.RemittanceType.SESSION) {
        Text(
            text = "Freezes on submit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val sessionGrossCents = sessionLinesGrossCents(detail.lines)
        SnapshotRow("Gross to freeze", peso(centsToMoney(sessionGrossCents)))
        if (moneyToCents(detail.totalAmount) != sessionGrossCents) {
            SnapshotRow("Line total (incl. product lines)", peso(detail.totalAmount))
        }
    } else {
        SnapshotRow("Product total", peso(detail.totalAmount))
        Text(
            text = "Product flows write no SESSION snapshot; commission is excluded.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// #447 — control-desk geometry + queue statuses (backend enum names, mirroring the list tabs).
// The editor column needs room for the money-review rows: rails stay narrow so the desk
// engages from ~1220px desktop windows (drawer + rails + editor).
private val CONTROL_DESK_MIN_WIDTH = 860.dp
private val CONTROL_DESK_RAIL_WIDTH = 210.dp
private val DESK_QUEUE_DRAFTS_STATUS = RemittanceStatus.DRAFT.name
private val DESK_QUEUE_SUBMITTED_STATUS = RemittanceStatus.SUBMITTED.name
private const val QUEUE_SELECTED_ALPHA = 0.14f
private const val QUEUE_SELECTED_BORDER_ALPHA = 0.6f

/** D10 — undo confirm: reason required, one line (Void discipline precedent; server enforces). */
@Composable
private fun UndoDialog(
    state: UiState<RemittanceResponse>,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by remember { mutableStateOf("") }
    var reasonError by remember { mutableStateOf<String?>(null) }

    val inFlight = state is UiState.Loading

    fun commit() {
        if (inFlight) return
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) {
            reasonError = "A reason is required"
            return
        }
        reasonError = null
        onSubmit(trimmed)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Undo submission?") },
        text = {
            Column {
                Text(
                    text =
                        "The draft is restored, the days unlock, and the frozen numbers are " +
                            "deleted. The undo reason is recorded in the audit log.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.size(Spacing.sm))
                UndoReasonFields(
                    reason = reason,
                    reasonError = reasonError,
                    state = state,
                    onReasonChange = {
                        // one line — strip newlines (the backend rejects multi-line reasons)
                        reason = it.filterNot { c -> c == '\n' || c == '\r' }
                        reasonError = null
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::commit,
                enabled = !inFlight,
            ) {
                Text("Undo")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !inFlight,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun UndoReasonFields(
    reason: String,
    reasonError: String?,
    state: UiState<RemittanceResponse>,
    onReasonChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = reason,
        onValueChange = onReasonChange,
        singleLine = true,
        isError = reasonError != null,
        placeholder = { Text("Reason") },
        modifier = Modifier.fillMaxWidth(),
    )
    reasonError?.let {
        Text(
            text = it,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    (state as? UiState.Error)?.let {
        Text(
            text = it.message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
