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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CommissionViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "CommissionVM")

    private val _inclusionResult = MutableStateFlow<UiState<CommissionInclusionResponse>>(UiState.Idle)
    val inclusionResult: StateFlow<UiState<CommissionInclusionResponse>> = _inclusionResult.asStateFlow()

    private val _splits = MutableStateFlow<UiState<List<CommissionSplitResponse>>>(UiState.Idle)
    val splits: StateFlow<UiState<List<CommissionSplitResponse>>> = _splits.asStateFlow()

    fun createInclusion(request: CreateCommissionInclusionRequest) {
        handler.launch(
            state = _inclusionResult,
            operation = "createInclusion",
            endpoint = "POST /api/commission-inclusions",
            block = {
                apiClient.httpClient.post("/api/commission-inclusions") {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun loadSplits(branchDayId: String) {
        handler.launch(
            state = _splits,
            operation = "loadSplits",
            endpoint = "GET /api/commission-splits/$branchDayId",
            block = { apiClient.httpClient.get("/api/commission-splits/$branchDayId") },
            transform = { it.body() },
        )
    }
}
