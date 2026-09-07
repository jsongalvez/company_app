package com.companyb.companyapp.finance

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.companyb.companyapp.contracts.reporting.DailySalesSummaryResponse
import kotlinx.datetime.LocalDate

@Composable
internal actual fun FinanceDayDetail(
    day: DailySalesSummaryResponse,
    today: LocalDate,
    expanded: Boolean,
    onClose: () -> Unit,
    export: DayExport,
) = MobileFinanceDayDetail(day, today, expanded, onClose, export)
