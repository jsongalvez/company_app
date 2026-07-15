package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.CommissionInclusionResponse
import com.companyb.companyapp.dto.CommissionSplitResponse
import com.companyb.companyapp.dto.CreateCommissionInclusionRequest
import com.companyb.companyapp.network.ApiClient
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommissionViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _inclusionResult = MutableStateFlow<UiState<CommissionInclusionResponse>>(UiState.Idle)
    val inclusionResult: StateFlow<UiState<CommissionInclusionResponse>> = _inclusionResult.asStateFlow()

    private val _splits = MutableStateFlow<UiState<List<CommissionSplitResponse>>>(UiState.Idle)
    val splits: StateFlow<UiState<List<CommissionSplitResponse>>> = _splits.asStateFlow()

    fun createInclusion(request: CreateCommissionInclusionRequest) {
        logInfo("CommissionVM", "createInclusion called")
        viewModelScope.launch {
            _inclusionResult.value = UiState.Loading
            try {
                logInfo("CommissionVM", "POST /api/commission-inclusions")
                val response =
                    apiClient.httpClient.post("/api/commission-inclusions") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("CommissionVM", "createInclusion success")
                    _inclusionResult.value = UiState.Success(response.body())
                } else {
                    logInfo("CommissionVM", "createInclusion failed: status=${response.status.value}")
                    _inclusionResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("CommissionVM", "createInclusion exception", e)
                _inclusionResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadSplits(branchDayId: String) {
        logInfo("CommissionVM", "loadSplits called")
        viewModelScope.launch {
            _splits.value = UiState.Loading
            try {
                logInfo("CommissionVM", "GET /api/commission-splits/$branchDayId")
                val response = apiClient.httpClient.get("/api/commission-splits/$branchDayId")
                if (response.status.isSuccess()) {
                    logInfo("CommissionVM", "loadSplits success")
                    _splits.value = UiState.Success(response.body())
                } else {
                    logInfo("CommissionVM", "loadSplits failed: status=${response.status.value}")
                    _splits.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("CommissionVM", "loadSplits exception", e)
                _splits.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
