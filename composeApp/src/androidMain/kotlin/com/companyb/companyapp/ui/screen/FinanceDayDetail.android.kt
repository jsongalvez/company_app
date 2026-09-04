package com.companyb.companyapp.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import kotlinx.datetime.LocalDate

@Composable
internal actual fun FinanceDayDetail(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    expanded: Boolean,
    onClose: () -> Unit,
    export: DayExport,
) = MobileFinanceDayDetail(day, today, expanded, onClose, export)
