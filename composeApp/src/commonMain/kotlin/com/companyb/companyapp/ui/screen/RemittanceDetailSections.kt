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
import com.companyb.companyapp.viewmodel.addDayBreakdown
import com.companyb.companyapp.viewmodel.addLine
import com.companyb.companyapp.viewmodel.deleteDayBreakdown
import com.companyb.companyapp.viewmodel.deleteLine
import com.companyb.companyapp.viewmodel.loadDrift
import com.companyb.companyapp.viewmodel.remittanceListKey
import com.companyb.companyapp.viewmodel.submit
import com.companyb.companyapp.viewmodel.undo
import com.companyb.companyapp.viewmodel.updateHeader
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius

/**
 * Detail surface states collected in one composition read (ADR-0022 reload axis): the three
 * picker caches, line/day mutation results, submit/undo/header results, and drift.
 */
internal data class RemittanceDetailCollected(
    val sessionPickerState: UiState<List<RemittanceSessionPickerEntryResponse>>,
    val productSalePickerState: UiState<List<RemittanceProductSalePickerEntryResponse>>,
    val dayPickerState: UiState<List<RemittanceDayPickerEntryResponse>>,
    val lineState: UiState<RemittanceLineResponse>,
    val deleteLineState: UiState<Unit>,
    val dayBreakdownState: UiState<RemittanceDayBreakdownResponse>,
    val deleteDayBreakdownState: UiState<Unit>,
    val submitState: UiState<RemittanceSubmitResponse>,
    val undoState: UiState<RemittanceResponse>,
    val headerUpdateState: UiState<RemittanceResponse>,
    val driftState: UiState<RemittanceDriftResponse>,
)

@Composable
internal fun rememberRemittanceDetailCollected(viewModel: RemittanceViewModel): RemittanceDetailCollected {
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
    return RemittanceDetailCollected(
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
    )
}

/**
 * #142 — dialog-flag state holder: the six detail-surface dialogs (header edit, the three
 * pickers, submit, undo) as direct-assignment flags. Callers open/close with flag writes;
 * no callback threading through the host chain.
 */
internal class RemittanceDetailDialogState {
    var header by mutableStateOf(false)
    var sessionPicker by mutableStateOf(false)
    var productSalePicker by mutableStateOf(false)
    var dayPicker by mutableStateOf(false)
    var submit by mutableStateOf(false)
    var undo by mutableStateOf(false)
}

@Composable
internal fun RemittanceDetailContent(
    detail: RemittanceDetailResponse,
    args: RemittanceDetailArgs,
    dialogs: RemittanceDetailDialogState,
) {
    val collected = rememberRemittanceDetailCollected(args.viewModel)
    RemittanceDetailContentBody(
        detail = detail,
        collected = collected,
        args = args,
        dialogs = dialogs,
    )
    RemittanceDetailContentDialogs(
        detail = detail,
        collected = collected,
        args = args,
        dialogs = dialogs,
    )
}

@Composable
private fun RemittanceDetailContentBody(
    detail: RemittanceDetailResponse,
    collected: RemittanceDetailCollected,
    args: RemittanceDetailArgs,
    dialogs: RemittanceDetailDialogState,
) {
    val isDraft = detail.status == com.companyb.companyapp.domain.RemittanceStatus.DRAFT
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        RemittanceDetailHeaderSummary(
            detail = detail,
            isDraft = isDraft,
            dialogs = dialogs,
        )
        RemittanceDetailLinesSection(
            detail = detail,
            collected = collected,
            args = args,
            dialogs = dialogs,
        )
        RemittanceDetailDaysSection(
            detail = detail,
            collected = collected,
            args = args,
            dialogs = dialogs,
        )
        RemittanceDetailReceiptSection(
            detail = detail,
            collected = collected,
            args = args,
            dialogs = dialogs,
        )
    }
}

@Composable
private fun RemittanceDetailContentDialogs(
    detail: RemittanceDetailResponse,
    collected: RemittanceDetailCollected,
    args: RemittanceDetailArgs,
    dialogs: RemittanceDetailDialogState,
) {
    RemittanceDetailHeaderEditHost(
        detail = detail,
        collected = collected,
        args = args,
        dialogs = dialogs,
    )
    RemittanceDetailLinePickerHost(
        detail = detail,
        collected = collected,
        args = args,
        dialogs = dialogs,
    )
    RemittanceDetailDayPickerHost(
        detail = detail,
        collected = collected,
        args = args,
        dialogs = dialogs,
    )
    RemittanceDetailSubmitUndoHost(
        detail = detail,
        collected = collected,
        args = args,
        dialogs = dialogs,
    )
}

@Composable
private fun RemittanceDetailHeaderSummary(
    detail: RemittanceDetailResponse,
    isDraft: Boolean,
    dialogs: RemittanceDetailDialogState,
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
            TextButton(onClick = { dialogs.header = true }) {
                Text("Edit")
            }
        }
    }
    RemittanceDetailRow("Type", remittanceTypeLabel(detail.type.name))
    RemittanceDetailRow("Method", remittanceMethodLabel(detail.method.name))
    RemittanceDetailRow("Date range", "${detail.dateRangeStart} – ${detail.dateRangeEnd}")
    RemittanceDetailRow("Created", formatRelativeTimestamp(detail.createdAt))
    if (detail.submittedDate.isNotBlank()) {
        RemittanceDetailRow("Submitted date", detail.submittedDate)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun RemittanceDetailHeaderEditHost(
    detail: RemittanceDetailResponse,
    collected: RemittanceDetailCollected,
    args: RemittanceDetailArgs,
    dialogs: RemittanceDetailDialogState,
) {
    // D9 — header edit popup (the D2 create popup prefilled).
    if (dialogs.header) {
        HeaderEditDialog(
            detail = detail,
            updateState = collected.headerUpdateState,
            onSave = { request ->
                if (collected.headerUpdateState !is UiState.Loading) {
                    args.viewModel.updateHeader(args.remittanceId, request)
                }
            },
            onDismiss = {
                if (collected.headerUpdateState !is UiState.Loading) {
                    dialogs.header = false
                }
            },
        )
    }
}

@Composable
private fun RemittanceDetailSubmitUndoHost(
    detail: RemittanceDetailResponse,
    collected: RemittanceDetailCollected,
    args: RemittanceDetailArgs,
    dialogs: RemittanceDetailDialogState,
) {
    val dayLabels =
        (collected.dayPickerState as? UiState.Success)
            ?.data
            ?.associate { it.id to it }
            .orEmpty()
    // D5 — structural confirm; numbers unknowable pre-submit (F4): type, covered days
    // (count + dates), line count, line total, method + the 48h warning copy.
    if (dialogs.submit) {
        SubmitConfirmDialog(
            detail = detail,
            dayLabels = dayLabels,
            state = collected.submitState,
            onSubmit = {
                if (collected.submitState !is UiState.Loading) {
                    args.viewModel.submit(
                        args.remittanceId,
                        SubmitRemittanceRequest(expectedVersion = detail.version),
                    )
                }
            },
            onDismiss = {
                if (collected.submitState !is UiState.Loading) {
                    dialogs.submit = false
                }
            },
        )
    }

    // D10 — undo confirm: reason required (one line, mirroring the Void discipline).
    if (dialogs.undo) {
        UndoDialog(
            state = collected.undoState,
            onSubmit = { reason ->
                if (collected.undoState !is UiState.Loading) {
                    args.viewModel.undo(
                        args.remittanceId,
                        UndoRemittanceRequest(expectedVersion = detail.version, reason = reason),
                    )
                }
            },
            onDismiss = {
                if (collected.undoState !is UiState.Loading) {
                    dialogs.undo = false
                }
            },
        )
    }
}

@Composable
private fun RemittanceDetailLinePickerHost(
    detail: RemittanceDetailResponse,
    collected: RemittanceDetailCollected,
    args: RemittanceDetailArgs,
    dialogs: RemittanceDetailDialogState,
) {
    val branchId = args.branchId
    // D3 — tick-to-include pickers; the dialog owns its add-queue (one POST per selected row,
    // advanced on each mutation success).
    if (dialogs.sessionPicker && branchId != null) {
        val includedIds =
            detail.lines
                .filter { it.type == com.companyb.companyapp.domain.RemittanceLineType.SESSION }
                .mapNotNull { it.sessionId }
                .toSet()
        SessionPickerDialog(
            data =
                IncomePickerData(
                    state = collected.sessionPickerState,
                    mutationState = collected.lineState,
                    includedIds = includedIds,
                ),
            flow =
                IncomePickerFlow(
                    onLoad = {
                        args.viewModel.loadSessionPicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
                    },
                    onAdd = { requests ->
                        if (requests.isNotEmpty() && collected.lineState !is UiState.Loading) {
                            args.viewModel.addLine(args.remittanceId, requests.first())
                        }
                    },
                    onDismiss = { dialogs.sessionPicker = false },
                ),
        )
    }
    if (dialogs.productSalePicker && branchId != null) {
        val includedIds =
            detail.lines
                .filter { it.type == com.companyb.companyapp.domain.RemittanceLineType.PRODUCT_SALE }
                .mapNotNull { it.productSaleId }
                .toSet()
        ProductSalePickerDialog(
            data =
                IncomePickerData(
                    state = collected.productSalePickerState,
                    mutationState = collected.lineState,
                    includedIds = includedIds,
                ),
            flow =
                IncomePickerFlow(
                    onLoad = {
                        args.viewModel.loadProductSalePicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
                    },
                    onAdd = { requests ->
                        if (requests.isNotEmpty() && collected.lineState !is UiState.Loading) {
                            args.viewModel.addLine(args.remittanceId, requests.first())
                        }
                    },
                    onDismiss = { dialogs.productSalePicker = false },
                ),
        )
    }
}

@Composable
private fun RemittanceDetailDayPickerHost(
    detail: RemittanceDetailResponse,
    collected: RemittanceDetailCollected,
    args: RemittanceDetailArgs,
    dialogs: RemittanceDetailDialogState,
) {
    val branchId = args.branchId
    // D4 — days-covered picker: already-remitted greyed (no double-covering a day, F10).
    if (dialogs.dayPicker && branchId != null) {
        val includedIds = detail.dayBreakdowns.map { it.branchDayId }.toSet()
        DayPickerDialog(
            data =
                DayPickerData(
                    state = collected.dayPickerState,
                    mutationState = collected.dayBreakdownState,
                    includedIds = includedIds,
                ),
            onLoad = {
                args.viewModel.loadDayPicker(branchId, detail.dateRangeStart, detail.dateRangeEnd)
            },
            onAdd = { requests ->
                if (requests.isNotEmpty() && collected.dayBreakdownState !is UiState.Loading) {
                    args.viewModel.addDayBreakdown(args.remittanceId, requests.first())
                }
            },
            onDismiss = { dialogs.dayPicker = false },
        )
    }
}
