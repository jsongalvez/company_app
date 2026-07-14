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
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AttendanceViewModel(
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _clockInState = MutableStateFlow<UiState<ClockInResponse>>(UiState.Idle)
    val clockInState: StateFlow<UiState<ClockInResponse>> = _clockInState.asStateFlow()

    private val _clockOutState = MutableStateFlow<UiState<ClockOutResponse>>(UiState.Idle)
    val clockOutState: StateFlow<UiState<ClockOutResponse>> = _clockOutState.asStateFlow()

    fun clockIn(request: ClockInRequest) {
        viewModelScope.launch {
            _clockInState.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/attendance/clock-in") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _clockInState.value = UiState.Success(response.body())
                } else {
                    _clockInState.value = UiState.Error("Clock-in failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _clockInState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun clockOut(request: ClockOutRequest) {
        viewModelScope.launch {
            _clockOutState.value = UiState.Loading
            try {
                val response =
                    apiClient.httpClient.post("/api/attendance/clock-out") {
                        setBody(request)
                    }
                if (response.status.isSuccess()) {
                    _clockOutState.value = UiState.Success(response.body())
                } else {
                    _clockOutState.value = UiState.Error("Clock-out failed: ${response.status.value}")
                }
            } catch (e: Exception) {
                _clockOutState.value = UiState.Error(e.message ?: "Unknown error")
            }
        }
    }
}
