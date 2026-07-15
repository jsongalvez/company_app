package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.DailySalesSummaryResponse
import com.companyb.companyapp.dto.MonthlyRemittanceSummaryResponse
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReportViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _dailySummary = MutableStateFlow<UiState<DailySalesSummaryResponse>>(UiState.Idle)
    val dailySummary: StateFlow<UiState<DailySalesSummaryResponse>> = _dailySummary.asStateFlow()

    private val _monthlySummary = MutableStateFlow<UiState<List<MonthlyRemittanceSummaryResponse>>>(UiState.Idle)
    val monthlySummary: StateFlow<UiState<List<MonthlyRemittanceSummaryResponse>>> = _monthlySummary.asStateFlow()

    fun loadDailySummary(
        branchId: String,
        date: String,
    ) {
        logInfo("ReportVM", "loadDailySummary called")
        viewModelScope.launch {
            _dailySummary.value = UiState.Loading
            logInfo("ReportVM", "GET /api/branches/$branchId/daily-summary?date=$date")
            try {
                val response =
                    apiClient.httpClient.get(
                        "/api/branches/$branchId/daily-summary?date=$date",
                    )
                if (response.status.isSuccess()) {
                    logInfo("ReportVM", "loadDailySummary success")
                    _dailySummary.value = UiState.Success(response.body())
                } else {
                    logInfo("ReportVM", "loadDailySummary failed: status=${response.status.value}")
                    _dailySummary.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ReportVM", "loadDailySummary exception", e)
                _dailySummary.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadMonthlySummary(
        branchId: String,
        year: Int,
        month: Int,
    ) {
        logInfo("ReportVM", "loadMonthlySummary called")
        viewModelScope.launch {
            _monthlySummary.value = UiState.Loading
            logInfo("ReportVM", "GET /api/branches/$branchId/monthly-summary?year=$year&month=$month")
            try {
                val response =
                    apiClient.httpClient.get(
                        "/api/branches/$branchId/monthly-summary?year=$year&month=$month",
                    )
                if (response.status.isSuccess()) {
                    logInfo("ReportVM", "loadMonthlySummary success")
                    _monthlySummary.value = UiState.Success(response.body())
                } else {
                    logInfo("ReportVM", "loadMonthlySummary failed: status=${response.status.value}")
                    _monthlySummary.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("ReportVM", "loadMonthlySummary exception", e)
                _monthlySummary.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
