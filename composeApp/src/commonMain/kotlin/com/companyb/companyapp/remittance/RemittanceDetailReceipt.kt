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
import com.companyb.companyapp.ui.screen.peso
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.formatRelativeTimestamp
import com.companyb.companyapp.util.logInfo
import com.companyb.companyapp.util.logWarn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius

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
internal fun RemittanceDetailRow(
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
internal fun RemittanceDetailLinesSection(
    detail: RemittanceDetailResponse,
    collected: RemittanceDetailCollected,
    args: RemittanceDetailArgs,
    dialogs: RemittanceDetailDialogState,
) {
    val sessionLabels =
        (collected.sessionPickerState as? UiState.Success)
            ?.data
            ?.associate { it.id to (it.clientName ?: "Session") }
            .orEmpty()
    val productSaleLabels =
        (collected.productSalePickerState as? UiState.Success)
            ?.data
            ?.associate { it.id to it.productName }
            .orEmpty()
    val isDraft = detail.status == com.companyb.companyapp.domain.RemittanceStatus.DRAFT
    Spacer(Modifier.size(Spacing.sm))
    SectionLabel("Lines")
    detail.lines.forEach { line ->
        LineRow(
            line = line,
            label = lineLabel(line, sessionLabels, productSaleLabels),
            deletable = isDraft,
            deleting = collected.deleteLineState is UiState.Loading,
            onDelete = {
                if (collected.deleteLineState !is UiState.Loading) {
                    args.viewModel.deleteLine(args.remittanceId, line.id)
                }
            },
        )
    }
    RemittanceDetailRow("Total", peso(detail.totalAmount))
    if (isDraft) {
        Row {
            TextButton(onClick = { dialogs.sessionPicker = true }) { Text("Add session income") }
            TextButton(onClick = { dialogs.productSalePicker = true }) { Text("Add product sales income") }
        }
    }
}

@Composable
internal fun RemittanceDetailDaysSection(
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
    val isDraft = detail.status == com.companyb.companyapp.domain.RemittanceStatus.DRAFT
    Spacer(Modifier.size(Spacing.sm))
    SectionLabel("Days covered")
    detail.dayBreakdowns.forEach { breakdown ->
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
                        if (collected.deleteDayBreakdownState !is UiState.Loading) {
                            args.viewModel.deleteDayBreakdown(args.remittanceId, breakdown.id)
                        }
                    },
                    enabled = collected.deleteDayBreakdownState !is UiState.Loading,
                ) {
                    Text("×")
                }
            }
        }
    }
    if (isDraft) {
        TextButton(onClick = { dialogs.dayPicker = true }) { Text("Add days") }
        Spacer(Modifier.size(Spacing.sm))
        OutlinedButton(
            onClick = { dialogs.submit = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Submit")
        }
    }
}

@Composable
internal fun RemittanceDetailReceiptSection(
    detail: RemittanceDetailResponse,
    collected: RemittanceDetailCollected,
    args: RemittanceDetailArgs,
    dialogs: RemittanceDetailDialogState,
) {
    val isSubmittedSession =
        detail.status == com.companyb.companyapp.domain.RemittanceStatus.SUBMITTED &&
            detail.type == com.companyb.companyapp.domain.RemittanceType.SESSION
    val snapshot = detail.snapshot
    if (isSubmittedSession && snapshot != null) {
        Spacer(Modifier.size(Spacing.md))
        FrozenReceiptBlock(
            snapshot = snapshot,
            driftState = collected.driftState,
            onShowDrift = { args.viewModel.loadDrift(args.remittanceId) },
        )
    }

    if (remittanceCanUndo(detail)) {
        Spacer(Modifier.size(Spacing.sm))
        OutlinedButton(
            onClick = { dialogs.undo = true },
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
