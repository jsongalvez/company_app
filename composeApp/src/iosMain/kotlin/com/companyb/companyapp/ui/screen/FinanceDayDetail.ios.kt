package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.viewmodel.FinanceReportsViewModel
import com.companyb.companyapp.viewmodel.UiState
import kotlinx.datetime.LocalDate

@Composable
internal actual fun FinanceDayDetail(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    expanded: Boolean,
    onClose: () -> Unit,
    onExportDay: (String) -> Unit,
    downloadStates: Map<String, UiState<FinanceReportsViewModel.DownloadPayload>>,
    exportErrors: Map<String, String>,
) = MobileFinanceDayDetail(day, today, expanded, onClose, onExportDay, downloadStates, exportErrors)
