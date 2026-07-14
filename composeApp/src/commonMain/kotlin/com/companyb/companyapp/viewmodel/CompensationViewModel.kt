package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.CompensationResponse
import com.companyb.companyapp.dto.CreateCompensationRequest
import com.companyb.companyapp.dto.UpdateCompensationRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CompensationViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _createResult = MutableStateFlow<UiState<CompensationResponse>>(UiState.Idle)
    val createResult: StateFlow<UiState<CompensationResponse>> = _createResult.asStateFlow()

    private val _updateResult = MutableStateFlow<UiState<CompensationResponse>>(UiState.Idle)
    val updateResult: StateFlow<UiState<CompensationResponse>> = _updateResult.asStateFlow()

    fun createCompensation(request: CreateCompensationRequest) {
        viewModelScope.launch {
            _createResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/compensation") {
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

    fun updateCompensation(
        compensationId: String,
        request: UpdateCompensationRequest,
    ) {
        viewModelScope.launch {
            _updateResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.patch("/api/compensation/$compensationId") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _updateResult.value = UiState.Success(response.body())
                } else {
                    _updateResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _updateResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
