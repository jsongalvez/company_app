package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.CommissionInclusionResponse
import com.companyb.companyapp.dto.CommissionSplitResponse
import com.companyb.companyapp.dto.CreateCommissionInclusionRequest
import com.companyb.companyapp.network.ApiClient
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
        viewModelScope.launch {
            _inclusionResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/commission-inclusions") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _inclusionResult.value = UiState.Success(response.body())
                } else {
                    _inclusionResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _inclusionResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadSplits(branchDayId: String) {
        viewModelScope.launch {
            _splits.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/commission-splits/$branchDayId")
                if (response.status.isSuccess()) {
                    _splits.value = UiState.Success(response.body())
                } else {
                    _splits.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _splits.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
