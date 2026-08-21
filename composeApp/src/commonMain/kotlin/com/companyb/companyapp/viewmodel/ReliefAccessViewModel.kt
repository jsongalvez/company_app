package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.ReliefAccessRequest
import com.companyb.companyapp.dto.ReliefAccessResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ReliefAccessViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "ReliefAccessVM")

    private val _requestState = MutableStateFlow<UiState<ReliefAccessResponse>>(UiState.Idle)
    val requestState: StateFlow<UiState<ReliefAccessResponse>> = _requestState.asStateFlow()

    private val _grantState = MutableStateFlow<UiState<ReliefAccessResponse>>(UiState.Idle)
    val grantState: StateFlow<UiState<ReliefAccessResponse>> = _grantState.asStateFlow()

    private val _denyState = MutableStateFlow<UiState<ReliefAccessResponse>>(UiState.Idle)
    val denyState: StateFlow<UiState<ReliefAccessResponse>> = _denyState.asStateFlow()

    fun requestAccess(request: ReliefAccessRequest) {
        handler.launch(
            state = _requestState,
            operation = "requestAccess",
            endpoint = "POST /api/relief-access/request",
            block = {
                apiClient.httpClient.post(ApiRoutes.RELIEF_ACCESS_REQUEST) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun grantAccess(requestId: String) {
        handler.launch(
            state = _grantState,
            operation = "grantAccess",
            endpoint = "PATCH /api/relief-access/$requestId/grant",
            block = { apiClient.httpClient.patch(ApiRoutes.reliefAccessRequest(requestId) + "/grant") },
            transform = { it.body() },
        )
    }

    fun denyAccess(requestId: String) {
        handler.launch(
            state = _denyState,
            operation = "denyAccess",
            endpoint = "PATCH /api/relief-access/$requestId/deny",
            block = { apiClient.httpClient.patch(ApiRoutes.reliefAccessRequest(requestId) + "/deny") },
            transform = { it.body() },
        )
    }
}
