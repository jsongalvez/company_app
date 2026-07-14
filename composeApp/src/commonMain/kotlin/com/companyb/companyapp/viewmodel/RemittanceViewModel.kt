package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AddDayBreakdownRequest
import com.companyb.companyapp.dto.CreateRemittanceDraftRequest
import com.companyb.companyapp.dto.CreateRemittanceLineRequest
import com.companyb.companyapp.dto.RemittanceDayBreakdownResponse
import com.companyb.companyapp.dto.RemittanceDetailResponse
import com.companyb.companyapp.dto.RemittanceLineResponse
import com.companyb.companyapp.dto.RemittanceResponse
import com.companyb.companyapp.dto.RemittanceSubmitResponse
import com.companyb.companyapp.dto.SubmitRemittanceRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RemittanceViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _createDraftResult = MutableStateFlow<UiState<RemittanceResponse>>(UiState.Idle)
    val createDraftResult: StateFlow<UiState<RemittanceResponse>> = _createDraftResult.asStateFlow()

    private val _remittanceDetail = MutableStateFlow<UiState<RemittanceDetailResponse>>(UiState.Idle)
    val remittanceDetail: StateFlow<UiState<RemittanceDetailResponse>> = _remittanceDetail.asStateFlow()

    private val _lineResult = MutableStateFlow<UiState<RemittanceLineResponse>>(UiState.Idle)
    val lineResult: StateFlow<UiState<RemittanceLineResponse>> = _lineResult.asStateFlow()

    private val _deleteLineResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deleteLineResult: StateFlow<UiState<Unit>> = _deleteLineResult.asStateFlow()

    private val _dayBreakdownResult = MutableStateFlow<UiState<RemittanceDayBreakdownResponse>>(UiState.Idle)
    val dayBreakdownResult: StateFlow<UiState<RemittanceDayBreakdownResponse>> = _dayBreakdownResult.asStateFlow()

    private val _submitResult = MutableStateFlow<UiState<RemittanceSubmitResponse>>(UiState.Idle)
    val submitResult: StateFlow<UiState<RemittanceSubmitResponse>> = _submitResult.asStateFlow()

    fun createDraft(request: CreateRemittanceDraftRequest) {
        viewModelScope.launch {
            _createDraftResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/remittances") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _createDraftResult.value = UiState.Success(response.body())
                } else {
                    _createDraftResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _createDraftResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadRemittance(remittanceId: String) {
        viewModelScope.launch {
            _remittanceDetail.value = UiState.Loading
            try {
                val response = apiClient.httpClient.get("/api/remittances/$remittanceId")
                if (response.status.isSuccess()) {
                    _remittanceDetail.value = UiState.Success(response.body())
                } else {
                    _remittanceDetail.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _remittanceDetail.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addLine(
        remittanceId: String,
        request: CreateRemittanceLineRequest,
    ) {
        viewModelScope.launch {
            _lineResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post(
                        "/api/remittances/$remittanceId/lines",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _lineResult.value = UiState.Success(response.body())
                } else {
                    _lineResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _lineResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteLine(
        remittanceId: String,
        lineId: String,
    ) {
        viewModelScope.launch {
            _deleteLineResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.delete(
                        "/api/remittances/$remittanceId/lines/$lineId",
                    )
                if (response.status.isSuccess()) {
                    _deleteLineResult.value = UiState.Success(Unit)
                } else {
                    _deleteLineResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _deleteLineResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addDayBreakdown(
        remittanceId: String,
        request: AddDayBreakdownRequest,
    ) {
        viewModelScope.launch {
            _dayBreakdownResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post(
                        "/api/remittances/$remittanceId/day-breakdowns",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _dayBreakdownResult.value = UiState.Success(response.body())
                } else {
                    _dayBreakdownResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _dayBreakdownResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun submit(
        remittanceId: String,
        request: SubmitRemittanceRequest,
    ) {
        viewModelScope.launch {
            _submitResult.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post(
                        "/api/remittances/$remittanceId/submit",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _submitResult.value = UiState.Success(response.body())
                } else {
                    _submitResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _submitResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
