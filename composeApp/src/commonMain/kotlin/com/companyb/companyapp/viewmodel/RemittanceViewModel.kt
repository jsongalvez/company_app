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
import com.companyb.companyapp.util.logError
import com.companyb.companyapp.util.logInfo
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
        logInfo("RemittanceVM", "createDraft called")
        viewModelScope.launch {
            _createDraftResult.value = UiState.Loading
            logInfo("RemittanceVM", "POST /api/remittances")
            try {
                val response =
                    apiClient.httpClient.post("/api/remittances") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("RemittanceVM", "createDraft success")
                    _createDraftResult.value = UiState.Success(response.body())
                } else {
                    logInfo("RemittanceVM", "createDraft failed: status=${response.status.value}")
                    _createDraftResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("RemittanceVM", "createDraft exception", e)
                _createDraftResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun loadRemittance(remittanceId: String) {
        logInfo("RemittanceVM", "loadRemittance called")
        viewModelScope.launch {
            _remittanceDetail.value = UiState.Loading
            logInfo("RemittanceVM", "GET /api/remittances/$remittanceId")
            try {
                val response = apiClient.httpClient.get("/api/remittances/$remittanceId")
                if (response.status.isSuccess()) {
                    logInfo("RemittanceVM", "loadRemittance success")
                    _remittanceDetail.value = UiState.Success(response.body())
                } else {
                    logInfo("RemittanceVM", "loadRemittance failed: status=${response.status.value}")
                    _remittanceDetail.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("RemittanceVM", "loadRemittance exception", e)
                _remittanceDetail.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addLine(
        remittanceId: String,
        request: CreateRemittanceLineRequest,
    ) {
        logInfo("RemittanceVM", "addLine called")
        viewModelScope.launch {
            _lineResult.value = UiState.Loading
            logInfo("RemittanceVM", "POST /api/remittances/$remittanceId/lines")
            try {
                val response =
                    apiClient.httpClient.post(
                        "/api/remittances/$remittanceId/lines",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("RemittanceVM", "addLine success")
                    _lineResult.value = UiState.Success(response.body())
                } else {
                    logInfo("RemittanceVM", "addLine failed: status=${response.status.value}")
                    _lineResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("RemittanceVM", "addLine exception", e)
                _lineResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun deleteLine(
        remittanceId: String,
        lineId: String,
    ) {
        logInfo("RemittanceVM", "deleteLine called")
        viewModelScope.launch {
            _deleteLineResult.value = UiState.Loading
            logInfo("RemittanceVM", "DELETE /api/remittances/$remittanceId/lines/$lineId")
            try {
                val response =
                    apiClient.httpClient.delete(
                        "/api/remittances/$remittanceId/lines/$lineId",
                    )
                if (response.status.isSuccess()) {
                    logInfo("RemittanceVM", "deleteLine success")
                    _deleteLineResult.value = UiState.Success(Unit)
                } else {
                    logInfo("RemittanceVM", "deleteLine failed: status=${response.status.value}")
                    _deleteLineResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("RemittanceVM", "deleteLine exception", e)
                _deleteLineResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun addDayBreakdown(
        remittanceId: String,
        request: AddDayBreakdownRequest,
    ) {
        logInfo("RemittanceVM", "addDayBreakdown called")
        viewModelScope.launch {
            _dayBreakdownResult.value = UiState.Loading
            logInfo("RemittanceVM", "POST /api/remittances/$remittanceId/day-breakdowns")
            try {
                val response =
                    apiClient.httpClient.post(
                        "/api/remittances/$remittanceId/day-breakdowns",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("RemittanceVM", "addDayBreakdown success")
                    _dayBreakdownResult.value = UiState.Success(response.body())
                } else {
                    logInfo("RemittanceVM", "addDayBreakdown failed: status=${response.status.value}")
                    _dayBreakdownResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("RemittanceVM", "addDayBreakdown exception", e)
                _dayBreakdownResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun submit(
        remittanceId: String,
        request: SubmitRemittanceRequest,
    ) {
        logInfo("RemittanceVM", "submit called")
        viewModelScope.launch {
            _submitResult.value = UiState.Loading
            logInfo("RemittanceVM", "POST /api/remittances/$remittanceId/submit")
            try {
                val response =
                    apiClient.httpClient.post(
                        "/api/remittances/$remittanceId/submit",
                    ) {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    logInfo("RemittanceVM", "submit success")
                    _submitResult.value = UiState.Success(response.body())
                } else {
                    logInfo("RemittanceVM", "submit failed: status=${response.status.value}")
                    _submitResult.value = UiState.Error("Failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                logError("RemittanceVM", "submit exception", e)
                _submitResult.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
