package com.companyb.companyapp.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.companyb.companyapp.async.UiState
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryResponse
import com.companyb.companyapp.ui.screen.peso
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.InkSubtle
import com.companyb.companyapp.ui.theme.Spacing
import com.companyb.companyapp.ui.theme.rowHover
import com.companyb.companyapp.util.logWarn
import kotlinx.datetime.LocalDate

@Composable
internal fun DayRow(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    selected: Boolean,
    onSelect: () -> Unit,
    export: DayExport,
) {
    // #654 — malformed server date fails closed to PAST display (no composition crash).
    val parsed = derivedDayStateFromIso(day.date, today)
    if (parsed == null) {
        logWarn("FinanceReportsScreen", "DayRow unparseable date=${day.date}")
    }
    val state = parsed ?: DerivedDayState.PAST
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .background(
                    if (selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                )
                // after the selection fill — the row's own opaque background must not cover the wash
                .rowHover()
                .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = day.date,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (state == DerivedDayState.PAST) InkSubtle else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Net ${peso(day.netIncome)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = "Gross ${peso(day.grossIncome)}",
                style = MaterialTheme.typography.bodySmall,
                color = InkSubtle,
            )
        }
        FinanceDayDetail(
            day = day,
            today = today,
            expanded = selected,
            onClose = onSelect,
            export = export,
        )
    }
}

/**
 * #105 D5 — the full-day card view: the complete daily figures at a glance (the card IS the
 * detail content; selection is a no-op visually — the card shows everything). Per-day exports
 * ride the same detail-content row.
 */
@Composable
internal fun FinanceDayCard(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    onSelect: () -> Unit,
    export: DayExport,
) {
    Surface(
        shape = RoundedCornerShape(CornerRadius.md),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.xxs)
                .clickable(onClick = onSelect)
                .rowHover(shape = RoundedCornerShape(CornerRadius.md)),
    ) {
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Text(
                text = day.date,
                style = MaterialTheme.typography.titleMedium,
            )
            FinanceDayDetailContent(
                day = day,
                today = today,
                onExportDay = export.onExportDay,
                downloadStates = export.downloadStates,
                exportErrors = export.exportErrors,
            )
        }
    }
}

/** #105 D5 — day detail presentation: desktop expands inline under the row (the #91 single-route
 * lock), mobile shows a modal ([onClose] dismisses the mobile dialog). Shared content in
 * [FinanceDayDetailContent].
 */
@Composable
internal fun MobileFinanceDayDetail(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    expanded: Boolean,
    onClose: () -> Unit,
    export: DayExport,
) {
    if (expanded) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text(day.date) },
            text = {
                FinanceDayDetailContent(
                    day = day,
                    today = today,
                    onExportDay = export.onExportDay,
                    downloadStates = export.downloadStates,
                    exportErrors = export.exportErrors,
                )
            },
            confirmButton = {
                TextButton(onClick = onClose) { Text("Close") }
            },
            modifier = Modifier,
        )
    }
}

@Composable
internal expect fun FinanceDayDetail(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    expanded: Boolean,
    onClose: () -> Unit,
    export: DayExport,
)

@Composable
internal fun FinanceDayDetailContent(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    onExportDay: ((String) -> Unit)? = null,
    downloadStates: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>> = emptyMap(),
    exportErrors: Map<String, String> = emptyMap(),
) {
    // #654 — malformed server date shows the invalid-date banner instead of crashing.
    val state = derivedDayStateFromIso(day.date, today)
    if (state == null) {
        logWarn("FinanceReportsScreen", "FinanceDayDetailContent unparseable date=${day.date}")
    }
    Column(modifier = Modifier.padding(horizontal = Spacing.xs)) {
        Text(
            text = dayStateBannerTextFromIso(day.date, today),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        // #105 D5 — per-day Download CSV/PDF lives in the day detail; the editor embeds the
        // SAME row (the toolbar export is hidden in edit mode — the embedded row is the
        // editor's only export surface).
        if (onExportDay != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Download",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSubtle,
                    modifier = Modifier.weight(1f),
                )
                ExportButtons(
                    baseKey = "day:${day.branchDayId}",
                    onExport = onExportDay,
                    errors = exportErrors,
                    downloads = downloadStates,
                )
            }
        }
        BreakdownRow("Gross income (sessions)", day.grossIncome)
        BreakdownRow("Product sales", day.totalProductSales)
        BreakdownRow("Commission", day.totalCommission)
        BreakdownRow("Compensation", day.totalCompensation)
        BreakdownRow("Expenses", day.totalExpenses)
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        BreakdownRow("Net", day.netIncome, strong = true)
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    value: String,
    strong: Boolean = false,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (strong) MaterialTheme.colorScheme.onSurface else InkSubtle,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = peso(value),
            style = if (strong) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
