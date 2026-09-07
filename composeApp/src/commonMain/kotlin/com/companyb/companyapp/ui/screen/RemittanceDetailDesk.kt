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

/**
 * #447 — shared desk bundle built by the success host: the queue mirrors + shared list
 * state, day labels, rail callbacks, and the desk selection context (branch + selected id).
 */
internal data class RemittanceDeskState(
    val branchId: String,
    val currentId: String,
    val mirrors: Map<String, List<RemittanceResponse>>,
    val queueState: UiState<List<RemittanceResponse>>,
    val dayEntries: Map<String, RemittanceDayPickerEntryResponse>,
    val onQueueClick: (String) -> Unit,
    val onRetryQueue: () -> Unit,
)

/** #447 — queue navigation actions handed down from the screen body (selection + refresh). */
internal class RemittanceDeskActions(
    val onQueueClick: (String) -> Unit,
    val onRetryQueue: () -> Unit,
)

/**
 * #447 — Variant B control desk (owner verdict on #429): draft queue | editable center
 * column | persistent submission brief. Wide layouts only; the center is the unchanged
 * detail content (every flow and state preserved), the rails are read-only
 * rearrangements of existing sources — no new endpoints, DTOs, or routes.
 */
@Composable
internal fun RemittanceControlDesk(
    detail: RemittanceDetailResponse,
    desk: RemittanceDeskState,
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
            DraftQueueRail(desk = desk)
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
            SubmissionBriefRail(detail = detail, dayEntries = desk.dayEntries)
        }
    }
}

/** #447 — desk left rail: drafts to review plus submitted history, from the drafts source. */
@Composable
private fun DraftQueueRail(desk: RemittanceDeskState) {
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
            rows = desk.mirrors[remittanceListKey(desk.branchId, DESK_QUEUE_DRAFTS_STATUS)],
            emptyText = "No drafts",
            desk = desk,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        QueueSection(
            title = "Submitted history",
            rows = desk.mirrors[remittanceListKey(desk.branchId, DESK_QUEUE_SUBMITTED_STATUS)],
            emptyText = "No submitted remittances",
            desk = desk,
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
    desk: RemittanceDeskState,
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
                        selected = item.id == desk.currentId,
                        onClick = { desk.onQueueClick(item.id) },
                    )
                }
            }
        }

        // The list flow is shared across statuses: a Success here may belong to the OTHER
        // section's landing while this key's load failed — Retry instead of spinning forever.
        desk.queueState is UiState.Error || desk.queueState is UiState.Success -> {
            Text(
                text = (desk.queueState as? UiState.Error)?.message ?: "Couldn't load this section",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = desk.onRetryQueue) {
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
internal val CONTROL_DESK_MIN_WIDTH = 860.dp

private val CONTROL_DESK_RAIL_WIDTH = 210.dp

internal val DESK_QUEUE_DRAFTS_STATUS = RemittanceStatus.DRAFT.name

internal val DESK_QUEUE_SUBMITTED_STATUS = RemittanceStatus.SUBMITTED.name

private const val QUEUE_SELECTED_ALPHA = 0.14f

private const val QUEUE_SELECTED_BORDER_ALPHA = 0.6f
