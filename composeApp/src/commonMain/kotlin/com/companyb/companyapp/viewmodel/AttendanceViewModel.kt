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
import kotlinx.coroutines.Job
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

    fun clockIn(request: ClockInRequest): Job {
        // Synchronous pre-set: the guard must hold from the caller's frame (a double-tap
        // before any dispatch would otherwise launch two clock-ins — the #135 double-tap
        // pattern; the #140 BranchSelect wrapper checks this state before delegating).
        _clockInState.value = UiState.Loading
        // Return type added for #140's chain — the caller joins the job to fire the
        // ADR-0021 capability refresh only after the clock-in succeeded.
        return handler.launch(
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

    fun clockOut(request: ClockOutRequest): Job {
        // #147 — mirrors clockIn (#140 r1 pattern): synchronous pre-set + Job return so the
        // drawer's dialog can disable the confirm and chain navigation on success.
        _clockOutState.value = UiState.Loading
        return handler.launch(
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
