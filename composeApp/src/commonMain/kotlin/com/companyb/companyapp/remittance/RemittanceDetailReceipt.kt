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
    val isDraft = detail.status == com.companyb.companyapp.contracts.remittance.RemittanceStatus.DRAFT
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
    val isDraft = detail.status == com.companyb.companyapp.contracts.remittance.RemittanceStatus.DRAFT
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
        detail.status == com.companyb.companyapp.contracts.remittance.RemittanceStatus.SUBMITTED &&
            detail.type == com.companyb.companyapp.contracts.remittance.RemittanceType.SESSION
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
@Composable
private fun LockIcon(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val keyholeColor = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = modifier.size(LOCK_SIZE),
    ) {
        val w = size.width
        val h = size.height
        val stroke = LOCK_STROKE.toPx()
        val bodyTop = h * LOCK_BODY_RATIO
        val shackleTop = h * LOCK_SHACKLE_RATIO
        drawArc(
            color = tint,
            startAngle = LOCK_ARC_START,
            sweepAngle = LOCK_ARC_SWEEP,
            useCenter = false,
            topLeft = Offset(w * LOCK_SHACKLE_X, shackleTop),
            size = Size(w * LOCK_SHACKLE_WIDTH, (bodyTop - shackleTop) * LOCK_SHACKLE_HEIGHT_FACTOR),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * LOCK_BODY_X, bodyTop),
            size = Size(w * LOCK_BODY_WIDTH, h * LOCK_BODY_HEIGHT_RATIO),
            cornerRadius = GeometryCornerRadius(LOCK_CORNER.toPx()),
        )
        drawCircle(
            color = keyholeColor,
            radius = LOCK_KEYHOLE_RADIUS.toPx(),
            center = Offset(w * LOCK_KEYHOLE_X, bodyTop + h * LOCK_KEYHOLE_Y_RATIO),
        )
    }
}

// #600 lock-glyph geometry (MagicNumber burn-down: named proportions, not bare literals).
private val LOCK_SIZE = 14.dp
private val LOCK_STROKE = 1.5.dp
private val LOCK_KEYHOLE_RADIUS = 1.5.dp
private val LOCK_CORNER = 2.dp
private const val LOCK_BODY_RATIO = 0.40f
private const val LOCK_SHACKLE_RATIO = 0.06f
private const val LOCK_ARC_START = 180f
private const val LOCK_ARC_SWEEP = 180f
private const val LOCK_SHACKLE_X = 0.22f
private const val LOCK_SHACKLE_WIDTH = 0.56f
private const val LOCK_SHACKLE_HEIGHT_FACTOR = 2f
private const val LOCK_BODY_X = 0.10f
private const val LOCK_BODY_WIDTH = 0.80f
private const val LOCK_BODY_HEIGHT_RATIO = 0.46f
private const val LOCK_KEYHOLE_X = 0.50f
private const val LOCK_KEYHOLE_Y_RATIO = 0.25f
