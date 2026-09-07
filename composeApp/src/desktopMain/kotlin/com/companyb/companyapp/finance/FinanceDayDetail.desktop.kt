package com.companyb.companyapp.finance

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.ui.theme.CornerRadius
import com.companyb.companyapp.ui.theme.Spacing
import kotlinx.datetime.LocalDate

// #105 D5 — desktop: the day detail expands inline under the row (the #91 single-route lock —
// no pushed route; the dashboard's inline-pane precedent).
@Composable
internal actual fun FinanceDayDetail(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    expanded: Boolean,
    onClose: () -> Unit,
    export: DayExport,
) {
    if (expanded) {
        Surface(
            shape = RoundedCornerShape(CornerRadius.md),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        ) {
            Column(modifier = Modifier.padding(Spacing.sm)) {
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
}
