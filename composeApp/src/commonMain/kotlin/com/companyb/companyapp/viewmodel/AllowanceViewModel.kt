package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AllowanceResponse
import com.companyb.companyapp.dto.CreateAllowanceRequest
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

class AllowanceViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _allowances = MutableStateFlow<UiState<List<AllowanceResponse>>>(UiState.Idle)
    val allowances: StateFlow<UiState<List<AllowanceResponse>>> = _allowances.asStateFlow()

    private val _createResult = MutableStateFlow<UiState<AllowanceResponse>>(UiState.Idle)
    val createResult: StateFlow<UiState<AllowanceResponse>> = _createResult.asStateFlow()

    fun loadAllowances(branchDayId: String) {
        viewModelScope.launch {
            _allowances.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/allowances?branchDayId=$branchDayId")
                if (response.status.isSuccess()) {
                    _allowances.value = UiState.Success(response.body())
                } else {
                    _allowances.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _allowances.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun createAllowance(request: CreateAllowanceRequest) {
        viewModelScope.launch {
            _createResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/allowances") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _createResult.value = UiState.Success(response.body())
                } else {
                    _createResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _createResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
