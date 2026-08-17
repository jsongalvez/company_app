package com.companyb.companyapp.viewmodel
import com.companyb.companyapp.api.ApiRoutes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.AssignDelegateRequest
import com.companyb.companyapp.dto.DelegateResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DelegateViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "DelegateVM")

    private val _assignResult = MutableStateFlow<UiState<DelegateResponse>>(UiState.Idle)
    val assignResult: StateFlow<UiState<DelegateResponse>> = _assignResult.asStateFlow()

    private val _revokeResult = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val revokeResult: StateFlow<UiState<Unit>> = _revokeResult.asStateFlow()

    fun assignDelegate(request: AssignDelegateRequest) {
        handler.launch(
            state = _assignResult,
            operation = "assignDelegate",
            endpoint = "POST /api/delegates",
            block = {
                apiClient.httpClient.post(ApiRoutes.DELEGATES) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun revokeDelegate(delegateId: String) {
        handler.launchUnit(
            state = _revokeResult,
            operation = "revokeDelegate",
            endpoint = "DELETE /api/delegates/$delegateId",
            block = { apiClient.httpClient.delete("/api/delegates/$delegateId") },
        )
    }
}
