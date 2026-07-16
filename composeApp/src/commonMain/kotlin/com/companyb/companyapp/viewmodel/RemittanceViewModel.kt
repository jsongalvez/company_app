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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RemittanceViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "RemittanceVM")

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
        handler.launch(
            state = _createDraftResult,
            operation = "createDraft",
            endpoint = "POST /api/remittances",
            block = {
                apiClient.httpClient.post("/api/remittances") {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun loadRemittance(remittanceId: String) {
        handler.launch(
            state = _remittanceDetail,
            operation = "loadRemittance",
            endpoint = "GET /api/remittances/$remittanceId",
            block = { apiClient.httpClient.get("/api/remittances/$remittanceId") },
            transform = { it.body() },
        )
    }

    fun addLine(
        remittanceId: String,
        request: CreateRemittanceLineRequest,
    ) {
        handler.launch(
            state = _lineResult,
            operation = "addLine",
            endpoint = "POST /api/remittances/$remittanceId/lines",
            block = {
                apiClient.httpClient.post(
                    "/api/remittances/$remittanceId/lines",
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun deleteLine(
        remittanceId: String,
        lineId: String,
    ) {
        handler.launchUnit(
            state = _deleteLineResult,
            operation = "deleteLine",
            endpoint = "DELETE /api/remittances/$remittanceId/lines/$lineId",
            block = {
                apiClient.httpClient.delete(
                    "/api/remittances/$remittanceId/lines/$lineId",
                )
            },
        )
    }

    fun addDayBreakdown(
        remittanceId: String,
        request: AddDayBreakdownRequest,
    ) {
        handler.launch(
            state = _dayBreakdownResult,
            operation = "addDayBreakdown",
            endpoint = "POST /api/remittances/$remittanceId/day-breakdowns",
            block = {
                apiClient.httpClient.post(
                    "/api/remittances/$remittanceId/day-breakdowns",
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun submit(
        remittanceId: String,
        request: SubmitRemittanceRequest,
    ) {
        handler.launch(
            state = _submitResult,
            operation = "submit",
            endpoint = "POST /api/remittances/$remittanceId/submit",
            block = {
                apiClient.httpClient.post(
                    "/api/remittances/$remittanceId/submit",
                ) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }
}
