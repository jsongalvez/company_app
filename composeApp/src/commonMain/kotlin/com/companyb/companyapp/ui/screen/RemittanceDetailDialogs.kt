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
import com.companyb.companyapp.async.UiState
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

/** D9 — header edit popup: the D2 create popup prefilled from the draft; PATCH on save. */
@Composable
internal fun HeaderEditDialog(
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
                HeaderEditDraft(type, method, startDate, endDate, dateError),
                updateState,
                HeaderEditHandlers(
                    { label -> type = RemittanceTypeChoice.entries.first { it.label == label } },
                    { label -> method = RemittanceMethodChoice.entries.first { it.label == label } },
                    { startDate = it },
                    { endDate = it },
                ),
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

/** D9 — snapshot of the header-edit field values + validation error shown under the fields. */
internal class HeaderEditDraft(
    val type: RemittanceTypeChoice,
    val method: RemittanceMethodChoice,
    val startDate: String,
    val endDate: String,
    val dateError: String?,
)

/** D9 — field-edit callbacks from the popup's text/dropdown fields back into the dialog state. */
internal class HeaderEditHandlers(
    val onTypeSelect: (String) -> Unit,
    val onMethodSelect: (String) -> Unit,
    val onStartChange: (String) -> Unit,
    val onEndChange: (String) -> Unit,
)

@Composable
private fun HeaderEditFields(
    draft: HeaderEditDraft,
    updateState: UiState<RemittanceResponse>,
    handlers: HeaderEditHandlers,
) {
    Column {
        LabeledDropdown(
            label = "Type",
            displayValue = draft.type.label,
            options = RemittanceTypeChoice.entries.map { it.label },
            onSelect = handlers.onTypeSelect,
        )
        LabeledDropdown(
            label = "Method",
            displayValue = draft.method.label,
            options = RemittanceMethodChoice.entries.map { it.label },
            onSelect = handlers.onMethodSelect,
        )
        RemittanceDatePickerField(
            label = "Date range start",
            value = draft.startDate,
            onValueChange = handlers.onStartChange,
        )
        RemittanceDatePickerField(
            label = "Date range end",
            value = draft.endDate,
            onValueChange = handlers.onEndChange,
        )
        draft.dateError?.let {
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

/** D5 — structural confirm: type, covered days (count + dates), line count, line total, method. */
@Composable
internal fun SubmitConfirmDialog(
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

/** D10 — undo confirm: reason required, one line (Void discipline precedent; server enforces). */
@Composable
internal fun UndoDialog(
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

/** Shared picker/dialog cancel button: disabled while the owning mutation is in flight. */
@Composable
internal fun IncomePickerDismissButton(
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
