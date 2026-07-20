package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.dto.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReportViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ReportVM")

    private val _dailySummary = MutableStateFlow<UiState<DailySalesSummaryResponse>>(UiState.Idle)
    val dailySummary: StateFlow<UiState<DailySalesSummaryResponse>> = _dailySummary.asStateFlow()

    private val _monthlySummary = MutableStateFlow<UiState<List<MonthlyRemittanceSummaryResponse>>>(UiState.Idle)
    val monthlySummary: StateFlow<UiState<List<MonthlyRemittanceSummaryResponse>>> = _monthlySummary.asStateFlow()

    fun loadDailySummary(
        branchId: String,
        date: String,
    ) {
        handler.launch(
            state = _dailySummary,
            operation = "loadDailySummary",
            endpoint = "GET /api/branches/$branchId/daily-summary?date=$date",
            block = {
                apiClient.httpClient.get(
                    "/api/branches/$branchId/daily-summary?date=$date",
                )
            },
            transform = { it.body() },
        )
    }

    fun loadMonthlySummary(
        branchId: String,
        year: Int,
        month: Int,
    ) {
        handler.launch(
            state = _monthlySummary,
            operation = "loadMonthlySummary",
            endpoint = "GET /api/branches/$branchId/monthly-summary?year=$year&month=$month",
            block = {
                apiClient.httpClient.get(
                    "/api/branches/$branchId/monthly-summary?year=$year&month=$month",
                )
            },
            transform = { it.body() },
        )
    }
}
