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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import androidx.compose.ui.geometry.CornerRadius as GeometryCornerRadius

internal fun lineLabel(
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

/** D4 — one day row: tick glyph + date + status; already-added/remitted rows grey out. */
@Composable
internal fun DayPickerDayRow(
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

/** D3 — one tick-to-include row's display + selection state, built by the picker body. */
internal data class PickerEntryRowModel(
    val label: String,
    val secondary: String,
    val amountText: String,
    val selected: Boolean,
    val enabled: Boolean,
    val amount: String?,
)

/** D3 — one picker row: tick glyph + label + secondary line + amount (editable when selected). */
@Composable
internal fun PickerEntryRow(
    model: PickerEntryRowModel,
    onAmountChange: (String) -> Unit,
    onToggle: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = model.enabled, onClick = onToggle)
                .rowHover(enabled = model.enabled)
                .padding(vertical = Spacing.xs),
    ) {
        Text(
            text = if (model.selected) "✓" else "○",
            color = if (model.selected) primary else onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.xs))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (model.enabled) onSurface else onSurfaceVariant,
            )
            Text(
                text = if (model.enabled) model.secondary else "Added",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (model.selected) {
            OutlinedTextField(
                value = model.amount.orEmpty(),
                onValueChange = onAmountChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(120.dp),
            )
        } else {
            Text(
                text = model.amountText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (model.enabled) onSurface else onSurfaceVariant,
            )
        }
    }
}

/** Shared label/value snapshot row (desk brief, submit review, frozen receipt). */
@Composable
internal fun SnapshotRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
