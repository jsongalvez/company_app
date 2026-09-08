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
import com.companyb.companyapp.contracts.remittance.RemittanceDriftResponse
import com.companyb.companyapp.contracts.remittance.RemittanceFinancialSnapshotResponse
import com.companyb.companyapp.contracts.remittance.RemittanceLineResponse
import com.companyb.companyapp.contracts.remittance.RemittanceProductSalePickerEntryResponse
import com.companyb.companyapp.contracts.remittance.RemittanceResponse
import com.companyb.companyapp.contracts.remittance.RemittanceStatus
import com.companyb.companyapp.contracts.remittance.RemittanceSubmitResponse
import com.companyb.companyapp.contracts.remittance.SubmitRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UndoRemittanceRequest
import com.companyb.companyapp.contracts.remittance.UpdateRemittanceHeaderRequest
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
 * #677 — shared desk bundle built by the success host: the queue mirrors + shared list
 * state, rail callbacks, and the desk selection context (branch + selected id).
 */
internal data class RemittanceDeskState(
    val branchId: String,
    val currentId: String,
    val mirrors: Map<String, List<RemittanceResponse>>,
    val queueState: UiState<List<RemittanceResponse>>,
    val onQueueClick: (String) -> Unit,
    val onRetryQueue: () -> Unit,
)

/**
 * #677 — queue + one workspace (the #447 Variant B desk minus its third summary rail:
 * the evolving draft and its review live in the one workspace, never duplicated into
 * a side brief). Wide layouts only; the workspace is the detail content with its
 * #677 footer/sections, the queue is a read-only rearrangement of the existing list
 * source — no new endpoints, DTOs, or routes.
 */
@Composable
internal fun RemittanceQueueWorkspace(
    desk: RemittanceDeskState,
    workspace: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Surface(
            shape = RoundedCornerShape(CornerRadius.md),
            border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(RemittanceLayoutPolicy.queueWidth).fillMaxHeight(),
        ) {
            DraftQueueRail(desk = desk)
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            workspace()
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

// #677 — queue statuses (backend enum names, mirroring the list tabs).
internal val DESK_QUEUE_DRAFTS_STATUS = RemittanceStatus.DRAFT.name

internal val DESK_QUEUE_SUBMITTED_STATUS = RemittanceStatus.SUBMITTED.name

private const val QUEUE_SELECTED_ALPHA = 0.14f

private const val QUEUE_SELECTED_BORDER_ALPHA = 0.6f
