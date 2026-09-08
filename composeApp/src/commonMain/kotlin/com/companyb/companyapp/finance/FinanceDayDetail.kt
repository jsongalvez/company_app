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
import com.companyb.companyapp.app.hasBranchOrDayCapability
import com.companyb.companyapp.app.hasCapability
import com.companyb.companyapp.contracts.authorization.CapabilityCodes
import com.companyb.companyapp.contracts.authorization.CapabilityContextType
import com.companyb.companyapp.contracts.authorization.UserCapabilityResponse
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryResponse
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
                text = "Net ${financeAmount(day.netIncome)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = "Gross ${financeAmount(day.grossIncome)}",
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
 * #678 — per-day Edit gate: the backend 403 stays authoritative; the affordance resolves
 * the same branches as the toolbar gate did (EDIT_BRANCH_DATA at the branch or a
 * BRANCH_DAY grant for this day, plus the ASSIGN/EDIT_PAST_DAY legs), failing closed
 * on past days without EDIT_PAST_DAY and on malformed server dates.
 */
internal fun dayEditAllowed(
    capabilities: List<UserCapabilityResponse>,
    branchId: String?,
    day: DailySalesSummaryResponse,
    today: LocalDate,
): Boolean {
    if (branchId == null) return false
    if ((derivedDayStateFromIso(day.date, today) ?: DerivedDayState.PAST) == DerivedDayState.PAST &&
        !capabilities.hasCapability(
            CapabilityCodes.EDIT_PAST_DAY,
            CapabilityContextType.BRANCH,
            branchId,
        )
    ) {
        return false
    }
    return capabilities.hasBranchOrDayCapability(
        CapabilityCodes.EDIT_BRANCH_DATA,
        branchId,
        day.branchDayId,
    ) ||
        capabilities.hasCapability(
            CapabilityCodes.ASSIGN_COMPENSATION,
            CapabilityContextType.BRANCH,
            branchId,
        ) ||
        capabilities.hasCapability(
            CapabilityCodes.EDIT_PAST_DAY,
            CapabilityContextType.BRANCH,
            branchId,
        )
}

/**
 * #678 — the selected-day region: an explicitly named branch/date/state header whose
 * primary number is the authoritative net amount (gross/expenses secondary), the Edit-day
 * action beside the identity it edits, then the shared breakdown + export menu.
 * The wide feed mounts it beside the report list; compact detail entries reuse the
 * shared content below.
 *
 * #678 LPL burn — the pane's identity + edit/export cluster travels as one object
 * (data-class constructors are LPL-free, the #479 precedent).
 */
internal data class SelectedDayPaneUi(
    val day: DailySalesSummaryResponse,
    val branchName: String,
    val today: LocalDate,
    val edit: DayEditAction?,
    val export: DayExport,
    val onBack: (() -> Unit)? = null,
)

@Composable
internal fun SelectedDayPane(
    ui: SelectedDayPaneUi,
    modifier: Modifier = Modifier,
) {
    val day = ui.day
    Column(modifier = modifier.fillMaxWidth()) {
        if (ui.onBack != null) {
            TextButton(onClick = ui.onBack) { Text("← Back to report") }
        }
        Text(
            text = "${day.date} · ${ui.branchName}",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = dayStateBannerTextFromIso(day.date, ui.today),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.xs),
        )
        Text(
            text = financeAmount(day.netIncome),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text =
                "Net ${financeAmount(day.netIncome)} · " +
                    "Gross ${financeAmount(day.grossIncome)} · " +
                    "Expenses ${financeAmount(day.totalExpenses)}",
            style = MaterialTheme.typography.bodySmall,
            color = InkSubtle,
            modifier = Modifier.padding(top = Spacing.xxs, bottom = Spacing.xs),
        )
        val edit = ui.edit
        if (edit != null && edit.canEdit) {
            TextButton(onClick = edit.onEditDay) { Text("Edit day") }
        }
        FinanceDayDetailContent(
            day = day,
            today = ui.today,
            // The pane header above owns the Edit-day action — passing ui.edit here too
            // would render it twice. Compact entries (card/dialog/inline) have no pane
            // header, so they carry edit inside their content instead.
            chrome = DayDetailChrome(export = ui.export),
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
                text = dayTitle(day.date, export.branchName),
                style = MaterialTheme.typography.titleMedium,
            )
            FinanceDayDetailContent(
                day = day,
                today = today,
                chrome = DayDetailChrome(export = export, edit = export.editAction()),
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
            title = { Text(dayTitle(day.date, export.branchName)) },
            text = {
                FinanceDayDetailContent(
                    day = day,
                    today = today,
                    chrome = DayDetailChrome(export = export, edit = export.editAction()),
                )
            },
            confirmButton = {
                // #678 — compact detail closes back to the period list (scroll preserved).
                TextButton(onClick = onClose) { Text("Back") }
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

/** #678 — the selected-day Edit entry as one object: null hides it (relief/read-only). */
internal data class DayEditAction(
    val canEdit: Boolean,
    val onEditDay: () -> Unit,
)

internal fun DayExport.editAction(): DayEditAction? = onEditDay?.let { DayEditAction(canEditDay, it) }

/** #678 — compact detail titles name the day's branch beside its date (wide pane headers do the same). */
internal fun dayTitle(
    date: String,
    branchName: String,
): String =
    if (branchName.isBlank()) {
        date
    } else {
        "$date · $branchName"
    }

/** #678 — the day-detail chrome (export + edit entries) as one object: null hides both
 * (relief/read-only surfaces). Data-class constructors are LPL-free (#479 precedent). */
internal data class DayDetailChrome(
    val export: DayExport? = null,
    val edit: DayEditAction? = null,
)

@Composable
internal fun FinanceDayDetailContent(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    chrome: DayDetailChrome = DayDetailChrome(),
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
        // #678 — the Edit-day action sits in the day detail beside the identity it edits
        // (compact inline/dialog entries included); the toolbar never edits.
        val edit = chrome.edit
        if (edit != null && edit.canEdit) {
            TextButton(onClick = edit.onEditDay) { Text("Edit day") }
        }
        // #105 D5 — per-day Download CSV/PDF lives in the day detail; the editor embeds the
        // SAME row (the toolbar export is hidden in edit mode — the embedded row is the
        // editor's only export surface).
        val export = chrome.export
        if (export != null) {
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
                ExportMenu(
                    baseKey = "day:${day.branchDayId}",
                    onExport = export.onExportDay,
                    errors = export.exportErrors,
                    downloads = export.downloadStates,
                )
            }
        }
        BreakdownRow("Gross income (sessions)", financeAmount(day.grossIncome))
        BreakdownRow("Product sales", financeAmount(day.totalProductSales))
        BreakdownRow("Commission", financeAmount(day.totalCommission))
        BreakdownRow("Compensation", financeAmount(day.totalCompensation))
        BreakdownRow("Expenses", financeAmount(day.totalExpenses))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        BreakdownRow("Net", financeAmount(day.netIncome), strong = true)
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
            text = value,
            style = if (strong) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
