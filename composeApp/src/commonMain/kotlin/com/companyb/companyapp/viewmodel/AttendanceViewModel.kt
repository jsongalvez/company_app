package com.companyb.companyapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.companyb.companyapp.dto.ClockInRequest
import com.companyb.companyapp.dto.ClockInResponse
import com.companyb.companyapp.dto.ClockOutRequest
import com.companyb.companyapp.dto.ClockOutResponse
import com.companyb.companyapp.network.ApiClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AttendanceViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val handler = ApiCallHandler(viewModelScope, "AttendanceVM")

    private val _clockInState = MutableStateFlow<UiState<ClockInResponse>>(UiState.Idle)
    val clockInState: StateFlow<UiState<ClockInResponse>> = _clockInState.asStateFlow()

    private val _clockOutState = MutableStateFlow<UiState<ClockOutResponse>>(UiState.Idle)
    val clockOutState: StateFlow<UiState<ClockOutResponse>> = _clockOutState.asStateFlow()

    fun clockIn(request: ClockInRequest) {
        handler.launch(
            state = _clockInState,
            operation = "clockIn",
            endpoint = "POST /api/attendance/clock-in",
            block = {
                apiClient.httpClient.post("/api/attendance/clock-in") {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }

    fun clockOut(request: ClockOutRequest) {
        handler.launch(
            state = _clockOutState,
            operation = "clockOut",
            endpoint = "POST /api/attendance/clock-out",
            block = {
                apiClient.httpClient.post("/api/attendance/clock-out") {
                    setBody(request)
                }
            },
            transform = { it.body() },
        )
    }
}
