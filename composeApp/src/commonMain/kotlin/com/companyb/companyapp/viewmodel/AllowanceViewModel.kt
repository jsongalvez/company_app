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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AllowanceViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "AllowanceVM")

    private val _allowances = MutableStateFlow<UiState<List<AllowanceResponse>>>(UiState.Idle)
    val allowances: StateFlow<UiState<List<AllowanceResponse>>> = _allowances.asStateFlow()

    private val _createResult = MutableStateFlow<UiState<AllowanceResponse>>(UiState.Idle)
    val createResult: StateFlow<UiState<AllowanceResponse>> = _createResult.asStateFlow()

    fun loadAllowances(branchDayId: String) {
        handler.launch(
            state = _allowances,
            operation = "loadAllowances",
            endpoint = "GET /api/allowances?branchDayId=$branchDayId",
            block = { apiClient.httpClient.get("/api/allowances?branchDayId=$branchDayId") },
            transform = { it.body() },
        )
    }

    fun createAllowance(request: CreateAllowanceRequest) {
        handler.launch(
            state = _createResult,
            operation = "createAllowance",
            endpoint = "POST /api/allowances",
            block = {
                apiClient.httpClient.post("/api/allowances") {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }
}
