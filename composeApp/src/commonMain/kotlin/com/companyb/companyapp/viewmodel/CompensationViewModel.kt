package com.companyb.companyapp.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.api.ApiRoutes
import com.companyb.companyapp.dto.CompensationResponse
import com.companyb.companyapp.dto.CreateCompensationRequest
import com.companyb.companyapp.dto.UpdateCompensationRequest
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CompensationViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "CompensationVM")

    private val _createResult = MutableStateFlow<UiState<CompensationResponse>>(UiState.Idle)
    val createResult: StateFlow<UiState<CompensationResponse>> = _createResult.asStateFlow()

    private val _updateResult = MutableStateFlow<UiState<CompensationResponse>>(UiState.Idle)
    val updateResult: StateFlow<UiState<CompensationResponse>> = _updateResult.asStateFlow()

    fun createCompensation(request: CreateCompensationRequest) {
        handler.launch(
            state = _createResult,
            operation = "createCompensation",
            endpoint = "POST /api/compensation",
            block = {
                apiClient.httpClient.post(ApiRoutes.COMPENSATION) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun updateCompensation(
        compensationId: String,
        request: UpdateCompensationRequest,
    ) {
        handler.launch(
            state = _updateResult,
            operation = "updateCompensation",
            endpoint = "PATCH /api/compensation/$compensationId",
            block = {
                apiClient.httpClient.patch(ApiRoutes.compensation(compensationId)) {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }
}
