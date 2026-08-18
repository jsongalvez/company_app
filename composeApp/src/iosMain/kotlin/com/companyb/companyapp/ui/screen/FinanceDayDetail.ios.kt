package com.companyb.companyapp.ui.screen

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.viewmodel.FinanceReportsViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlinx.datetime.LocalDate

// #105 D5 — mobile: the day detail opens as a modal dialog (#97/#99 dialog treatment; no
// bottom-sheet precedent — deferred as a pattern question).
@Composable
internal actual fun FinanceDayDetail(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    expanded: Boolean,
    onClose: () -> Unit,
    onExportDay: (String) -> Unit,
    downloadStates: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>>,
    exportErrors: Map<String, String>,
) {
    if (expanded) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { androidx.compose.material3.Text(day.date) },
            text = {
                FinanceDayDetailContent(
                    day = day,
                    today = today,
                    onExportDay = onExportDay,
                    downloadStates = downloadStates,
                    exportErrors = exportErrors,
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = onClose) {
                    androidx.compose.material3.Text("Close")
                }
            },
            modifier = Modifier,
        )
    }
}
